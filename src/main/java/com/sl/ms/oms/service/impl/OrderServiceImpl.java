package com.sl.ms.oms.service.impl;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.itheima.em.sdk.EagleMapTemplate;
import com.itheima.em.sdk.enums.ProviderEnum;
import com.itheima.em.sdk.vo.Coordinate;
import com.itheima.em.sdk.vo.GeoResult;
import com.sl.ms.base.api.common.AreaFeign;
import com.sl.ms.base.domain.base.AreaDto;
import com.sl.ms.mq.service.MQService;
import com.sl.ms.oms.dto.MailingSaveDTO;
import com.sl.ms.oms.entity.OrderCargoEntity;
import com.sl.ms.oms.entity.OrderEntity;
import com.sl.ms.oms.entity.OrderLocationEntity;
import com.sl.transport.common.vo.OrderMsg;
import com.sl.ms.oms.enums.OrderType;
import com.sl.ms.oms.mapper.OrderMapper;
import com.sl.ms.oms.service.CrudOrderService;
import com.sl.ms.oms.service.OrderService;
import com.sl.ms.scope.api.ServiceScopeFeign;
import com.sl.ms.scope.dto.ServiceScopeDTO;
import com.sl.ms.user.api.AddressBookFeign;
import com.sl.ms.user.domain.dto.AddressBookDto;
import com.sl.transport.common.util.Result;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 订单  服务实现类
 */
@Service
@Slf4j
public class OrderServiceImpl extends ServiceImpl<OrderMapper, OrderEntity> implements OrderService {


    @Value("${rabbitmq.OrderStatus.exchange}")
    private String rabbitmqOrderStatusExchange;

    @Autowired
    private MQService mqService;

    @Autowired
    private AddressBookFeign addressBookFeign;

    @Autowired
    private CrudOrderService crudOrderService;

    @Resource
    AreaFeign areaFeign;

    @Resource
    ServiceScopeFeign agencyScopeFeign;

    @Resource
    private EagleMapTemplate eagleMapTemplate;

    @Override
    public OrderEntity mailingSave(MailingSaveDTO mailingSaveDTO) throws Exception {
        // 获取地址详细信息
        AddressBookDto sendAddress = addressBookFeign.detail(mailingSaveDTO.getSendAddress());
        AddressBookDto receiptAddress = addressBookFeign.detail(mailingSaveDTO.getReceiptAddress());
        log.info("sendAddress:{},{} receiptAddress:{},{}", mailingSaveDTO.getSendAddress(), sendAddress, mailingSaveDTO.getReceiptAddress(), receiptAddress);
        if (ObjectUtil.isEmpty(sendAddress) || ObjectUtil.isEmpty(receiptAddress)) {
            log.error("获取地址薄详细信息 失败 mailingSaveDTO :{}", mailingSaveDTO);
            throw new Exception("获取地址详细信息失败");
        }
        // 构建实体
        OrderEntity order = buildOrder(mailingSaveDTO, sendAddress, receiptAddress);
        log.info("订单信息入库:{}", order);

        // 订单位置
        OrderLocationEntity orderLocation = buildOrderLocation(order);

        // 计算运费 距离 设置当前机构ID
        appendOtherInfo(order, orderLocation.getSendAgentId());

        // 货物
        OrderCargoEntity orderCargo = buildOrderCargo(mailingSaveDTO);

        // 执行保存
        OrderEntity entity = crudOrderService.saveOrder(order, orderCargo, orderLocation);

        // 生成订单mq 调度服务用来调度 之后快递员服务处理
        noticeOrderStatusChange(order, orderLocation);
        return entity;
    }

    /**
     * 补充数据
     * @param order
     * @param sendAgentId
     */
    private void appendOtherInfo(OrderEntity order, Long sendAgentId) {
        order.setCurrentAgencyId(sendAgentId);
        //TODO 预计到达时间
        order.setEstimatedArrivalTime(LocalDateTime.now().plus(2, ChronoUnit.DAYS));
        //TODO 需要计算距离和运费
        order.setAmount(BigDecimal.valueOf(20));
    }

    /**
     * 构建订单
     * @param mailingSaveDTO
     * @param sendAddress
     * @param receiptAddress
     * @return
     */
    private OrderEntity buildOrder(MailingSaveDTO mailingSaveDTO, AddressBookDto sendAddress, AddressBookDto receiptAddress) {
        OrderEntity entity = OrderEntity.builder()
                // 用户ID
                .memberId(mailingSaveDTO.getMemberId())

                .senderName(sendAddress.getName())
                .senderPhone(sendAddress.getPhoneNumber())
                .senderProvinceId(sendAddress.getProvinceId().toString())
                .senderCityId(sendAddress.getCityId().toString())
                .senderCountyId(sendAddress.getCountyId().toString())
                .senderAddress(sendAddress.getAddress())
                .senderAddressId(mailingSaveDTO.getSendAddress())

                .receiverName(receiptAddress.getName())
                .receiverPhone(receiptAddress.getPhoneNumber())
                .receiverProvinceId(receiptAddress.getProvinceId().toString())
                .receiverCityId(receiptAddress.getCityId().toString())
                .receiverCountyId(receiptAddress.getCountyId().toString())
                .receiverAddress(receiptAddress.getAddress())
                .receiverAddressId(mailingSaveDTO.getReceiptAddress())

                .paymentMethod(mailingSaveDTO.getPayMethod())
                .paymentStatus(1) // 默认未付款

                .pickupType(mailingSaveDTO.getPickupType())
                .build();

        entity.setOrderType(entity.getReceiverCityId().equals(entity.getSenderCityId()) ? OrderType.INCITY.getCode() : OrderType.OUTCITY.getCode());
//        Map map = orderFeign.getOrderMsg(entity);
//        .Amount(new BigDecimal(map.getOrDefault("amount", "23").toString()));
        //费用计算在订单服务中
//        .Amount(new BigDecimal("23"));
        return entity;
    }

    /**
     * 根据地址计算网点
     *
     * @param address
     * @return
     */
    private Result getAgencyId(String address) throws Exception {

        if (ObjectUtil.isEmpty(address)) {
            log.error("地址不能为空");
            throw new Exception("下单时发货地址不能为空");
        }
        //根据详细地址查询坐标
        GeoResult geoResult = this.eagleMapTemplate.opsForBase().geoCode(ProviderEnum.AMAP, address, null);
        Coordinate coordinate = geoResult.getLocation();

        log.info("地址和坐标-->" + address + "--" + coordinate);
        if (ObjectUtil.isEmpty(coordinate)) {
            log.error("地址无法定位");
            throw new Exception("地址无法定位");
        }
        double lng = coordinate.getLongitude(); // 经度
        double lat = coordinate.getLatitude(); // 纬度
        DecimalFormat df = new DecimalFormat("#.######");
        String lngStr = df.format(lng);
        String latStr = df.format(lat);
        String location =  StrUtil.format("{},{}",lngStr, latStr);

        List<ServiceScopeDTO> serviceScopeDTOS = agencyScopeFeign.queryListByLocation(1, coordinate.getLongitude(), coordinate.getLatitude());
        if (CollectionUtils.isEmpty(serviceScopeDTOS)) {
            log.error("地址不再服务范围");
            throw new Exception("地址不再服务范围");
        }
        Result result = new Result();
        result.put("agencyId", serviceScopeDTOS.get(0).getBid());
        result.put("location", location);
        return result;
    }

    @SneakyThrows
    private String senderFullAddress(OrderEntity entity) {
        StringBuilder stringBuffer = new StringBuilder();
        Long province = Long.valueOf(entity.getSenderProvinceId());
        Long city = Long.valueOf(entity.getSenderCityId());
        Long county = Long.valueOf(entity.getSenderCountyId());

        Set<Long> areaIdSet = new HashSet<Long>();
        areaIdSet.add(province);
        areaIdSet.add(city);
        areaIdSet.add(county);

        List<AreaDto> result = areaFeign.findAll(null, new ArrayList<>(areaIdSet));
        Map<Long, AreaDto> areaMap = result.stream().collect(Collectors.toMap(AreaDto::getId, vo -> vo));

        stringBuffer.append(areaMap.get(province).getName());
        stringBuffer.append(areaMap.get(city).getName());
        stringBuffer.append(areaMap.get(county).getName());
        stringBuffer.append(entity.getSenderAddress());

        return stringBuffer.toString();
    }

    @SneakyThrows
    private String receiverFullAddress(OrderEntity orderDTO) {
        StringBuilder stringBuffer = new StringBuilder();

        Long province = Long.valueOf(orderDTO.getReceiverProvinceId());
        Long city = Long.valueOf(orderDTO.getReceiverCityId());
        Long county = Long.valueOf(orderDTO.getReceiverCountyId());

        Set<Long> areaIdSet = new HashSet<Long>();
        areaIdSet.add(province);
        areaIdSet.add(city);
        areaIdSet.add(county);

        List<AreaDto> result = areaFeign.findAll(null, new ArrayList<>(areaIdSet));
        Map<Long, AreaDto> areaMap = result.stream().collect(Collectors.toMap(AreaDto::getId, vo -> vo));

        stringBuffer.append(areaMap.get(province).getName());
        stringBuffer.append(areaMap.get(city).getName());
        stringBuffer.append(areaMap.get(county).getName());
        stringBuffer.append(orderDTO.getReceiverAddress());

        return stringBuffer.toString();
    }

    /**
     * 构建货物
     * @param entity
     * @return
     */
    private OrderCargoEntity buildOrderCargo(MailingSaveDTO entity) {
        OrderCargoEntity cargoDto = new OrderCargoEntity();
        cargoDto.setName(entity.getGoodsName());
        cargoDto.setGoodsTypeId(entity.getGoodsType());
        cargoDto.setWeight(new BigDecimal(entity.getGoodsWeight()));
        cargoDto.setQuantity(1);
        cargoDto.setTotalWeight(cargoDto.getWeight().multiply(new BigDecimal(cargoDto.getQuantity())));
        return cargoDto;
    }

    /**
     * 根据发收件人地址获取起止机构ID 调用机构范围微服务
     * @param order
     * @return
     */
    private OrderLocationEntity buildOrderLocation(OrderEntity order) throws Exception {
        String address = senderFullAddress(order);
        Result result = getAgencyId(address);
        String agencyId = result.get("agencyId").toString();
        String sendLocation = result.get("location").toString();

        String receiverAddress = receiverFullAddress(order);
        Result resultReceive = getAgencyId(receiverAddress);
        String receiveAgencyId = resultReceive.get("agencyId").toString();
        String receiveAgentLocation = resultReceive.get("location").toString();

        OrderLocationEntity orderLocationEntity = new OrderLocationEntity();
        orderLocationEntity.setOrderId(order.getId());
        orderLocationEntity.setSendLocation(sendLocation);
        orderLocationEntity.setSendAgentId(Long.parseLong(agencyId));
        orderLocationEntity.setReceiveLocation(receiveAgentLocation);
        orderLocationEntity.setReceiveAgentId(Long.parseLong(receiveAgencyId));
        return orderLocationEntity;
    }

    /**
     * 声明交换机，确保交换机一定存在
     */
    @Bean
    public TopicExchange topicExchange() {
        return new TopicExchange(this.rabbitmqOrderStatusExchange, true, false);
    }

    /**
     * 派件
     * @param orderEntity
     * @param orderLocation
     */
    private void noticeOrderStatusChange(OrderEntity orderEntity, OrderLocationEntity orderLocation) {
        //{"order":{"orderId":123, "agencyId": 8001, "taskType":1, "mark":"带包装", "longitude":116.111, "latitude":39.00, "created":1654224658728, "estimatedStartTime": 1654224658728}, "created":123456}
        String[] split = orderLocation.getSendLocation().split(",");
        double lnt = Double.parseDouble(split[0]);
        double lat = Double.parseDouble(split[1]);
        OrderMsg build = OrderMsg.builder().created(orderEntity.getCreateTime())
                .estimatedStartTime(orderEntity.getEstimatedStartTime())
                .mark(orderEntity.getMark())
                .taskType(1)
                .latitude(lat)
                .longitude(lnt)
                .build();
        //发送消息
        this.mqService.sendMsg(rabbitmqOrderStatusExchange, null, build);
    }
}
