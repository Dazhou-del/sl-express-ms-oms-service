package com.sl.ms.oms.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.convert.Convert;
import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.itheima.em.sdk.EagleMapTemplate;
import com.itheima.em.sdk.enums.ProviderEnum;
import com.itheima.em.sdk.vo.Coordinate;
import com.itheima.em.sdk.vo.GeoResult;
import com.sl.ms.base.api.common.AreaFeign;
import com.sl.ms.base.domain.base.AreaDto;
import com.sl.mq.service.MQService;
import com.sl.ms.carriage.appi.CarriageFeign;
import com.sl.ms.carriage.domain.dto.CarriageDTO;
import com.sl.ms.carriage.domain.dto.WaybillDTO;
import com.sl.ms.oms.dto.MailingSaveDTO;
import com.sl.ms.oms.dto.OrderCarriageDTO;
import com.sl.ms.oms.dto.OrderDTO;
import com.sl.ms.oms.entity.OrderCargoEntity;
import com.sl.ms.oms.entity.OrderEntity;
import com.sl.ms.oms.entity.OrderLocationEntity;
import com.sl.transport.common.exception.SLException;
import com.sl.transport.common.vo.OrderMsg;
import com.sl.ms.oms.enums.OrderType;
import com.sl.ms.oms.mapper.OrderMapper;
import com.sl.ms.oms.service.CrudOrderService;
import com.sl.ms.oms.service.OrderService;
import com.sl.ms.scope.api.ServiceScopeFeign;
import com.sl.ms.scope.dto.ServiceScopeDTO;
import com.sl.ms.user.api.AddressBookFeign;
import com.sl.ms.user.domain.dto.AddressBookDTO;
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

    @Resource
    private CarriageFeign carriageFeign;

    /**
     * 下单
     * @param mailingSaveDTO 下单信息
     * @return 下单成功信息
     * @throws Exception
     */
    @Override
    public OrderDTO mailingSave(MailingSaveDTO mailingSaveDTO) throws Exception {
        // 获取地址详细信息
        HashMap<Long, AddressBookDTO> orderAddress = getOrderAddress(mailingSaveDTO.getSendAddress(), mailingSaveDTO.getReceiptAddress());
        AddressBookDTO sendAddress = orderAddress.get(mailingSaveDTO.getSendAddress());
        AddressBookDTO receiptAddress = orderAddress.get(mailingSaveDTO.getReceiptAddress());
        // 构建实体
        OrderEntity order = buildOrder(mailingSaveDTO, sendAddress, receiptAddress);
        log.info("订单信息入库:{}", order);

        // 计算运费
        CarriageDTO carriageDTO = computeCarriage(mailingSaveDTO, sendAddress.getCityId(), receiptAddress.getCityId());
        order.setAmount(BigDecimal.valueOf(carriageDTO.getExpense()));

        // 订单位置
        OrderLocationEntity orderLocation = buildOrderLocation(order);
        log.info("订单位置为：{}", orderLocation);

        // 货物
        OrderCargoEntity orderCargo = buildOrderCargo(mailingSaveDTO);

        // 距离 设置当前机构ID
        appendOtherInfo(order, orderLocation, orderCargo);

        // 执行保存
        crudOrderService.saveOrder(order, orderCargo, orderLocation);

        // 生成订单mq 调度服务用来调度 之后快递员服务处理
        noticeOrderStatusChange(order, orderLocation);

        // 返回下单成功页
        OrderDTO orderDTO = BeanUtil.toBean(order, OrderDTO.class);
        // 基础运费
        orderDTO.setFirstWeight(carriageDTO.getFirstWeight());
        return orderDTO;
    }

    /**
     * 获取订单地址信息
     * @param sendAddressId 发送地址ID
     * @param receiptAddressId 接收地址ID
     * @return
     */
    private HashMap<Long, AddressBookDTO> getOrderAddress(Long sendAddressId, Long receiptAddressId) {
        AddressBookDTO sendAddress = addressBookFeign.detail(sendAddressId);
        AddressBookDTO receiptAddress = addressBookFeign.detail(receiptAddressId);
        log.info("sendAddress:{},{} receiptAddress:{},{}", receiptAddressId, sendAddress, receiptAddressId, receiptAddress);
        if (ObjectUtil.isEmpty(sendAddress) || ObjectUtil.isEmpty(receiptAddress)) {
            log.error("获取地址薄详细信息 失败 receiptAddressId  receiptAddressId :{} {} ", sendAddressId, receiptAddressId);
            throw new SLException("获取地址详细信息失败");
        }
        HashMap<Long, AddressBookDTO> map = new HashMap<>();
        map.put(sendAddressId, sendAddress);
        map.put(receiptAddressId, receiptAddress);
        return map;
    }

    /**
     * 预估总价
     * @param mailingSaveDTO 下单信息
     * @return 运费预估信息
     */
    @Override
    public OrderCarriageDTO totalPrice(MailingSaveDTO mailingSaveDTO) {
        // 获取地址详细信息
        // 获取地址详细信息
        HashMap<Long, AddressBookDTO> orderAddress = getOrderAddress(mailingSaveDTO.getSendAddress(), mailingSaveDTO.getReceiptAddress());
        AddressBookDTO sendAddress = orderAddress.get(mailingSaveDTO.getSendAddress());
        AddressBookDTO receiptAddress = orderAddress.get(mailingSaveDTO.getReceiptAddress());
        // 计算运费
        CarriageDTO carriageDTO = computeCarriage(mailingSaveDTO, sendAddress.getCityId(), receiptAddress.getCityId());
        return BeanUtil.toBean(carriageDTO, OrderCarriageDTO.class);
    }

    /**
     * 运费计算
     * @param mailingSaveDTO 下单信息
     * @param senderCityId 发送城市ID
     * @param receiverCityId 接收城市ID
     * @return 计算结果
     */
    private CarriageDTO computeCarriage(MailingSaveDTO mailingSaveDTO, Long senderCityId, Long receiverCityId) {
        // 运费
        WaybillDTO waybillDTO = WaybillDTO.builder()
                .senderCityId(senderCityId)
                .receiverCityId(receiverCityId)
                .measureHigh(mailingSaveDTO.getMeasureHigh())
                .measureLong(mailingSaveDTO.getMeasureLong())
                .measureWidth(mailingSaveDTO.getMeasureWidth())
                .volume(mailingSaveDTO.getTotalVolume().toBigInteger().intValue())
                .weight(mailingSaveDTO.getTotalWeight().toBigInteger().doubleValue())
                .build();

        CarriageDTO compute = carriageFeign.compute(waybillDTO);
        if (ObjectUtil.isEmpty(compute)) {
            throw new SLException(StrUtil.format("计算运费出错 mailingSaveDTO {}", mailingSaveDTO));
        }
        return compute;
    }

    /**
     * 补充数据
     * @param order 订单
     * @param orderLocation 订单位置
     * @param orderCargo
     */
    private void appendOtherInfo(OrderEntity order, OrderLocationEntity orderLocation, OrderCargoEntity orderCargo) {
        // 当前机构
        order.setCurrentAgencyId(orderLocation.getSendAgentId());

        //查询地图服务商
        String[] sendLocation = orderLocation.getSendLocation().split(",");
        double sendLnt = Double.parseDouble(sendLocation[0]);
        double sendLat = Double.parseDouble(sendLocation[1]);

        String[] receiveLocation = orderLocation.getReceiveLocation().split(",");
        double receiveLnt = Double.parseDouble(receiveLocation[0]);
        double receiveLat = Double.parseDouble(receiveLocation[1]);
        Coordinate origin = new Coordinate(sendLnt, sendLat);
        Coordinate destination = new Coordinate(receiveLnt, receiveLat);
        //设置高德地图参数，默认是不返回预计耗时的，需要额外设置参数
        Map<String, Object> param = MapUtil.<String, Object>builder().put("show_fields", "cost").build();
        String driving = this.eagleMapTemplate.opsForDirection().driving(ProviderEnum.AMAP, origin, destination, param);
        if (StrUtil.isEmpty(driving)) {
            return;
        }
        JSONObject jsonObject = JSONUtil.parseObj(driving);
        //距离，单位：米
        BigDecimal distance = Convert.toBigDecimal(jsonObject.getByPath("route.paths[0].distance"));
        order.setDistance(distance);

        //时间，单位：秒
        Long duration = Convert.toLong(jsonObject.getByPath("route.paths[0].cost.duration"), -1L);
        // 预计到达时间 这里根据地图大致估算时间 并非实际时间
        order.setEstimatedArrivalTime(LocalDateTime.now().plus(duration, ChronoUnit.SECONDS));
    }

    /**
     * 构建订单
     *
     * @param mailingSaveDTO 下单信息
     * @param sendAddress 发送地址
     * @param receiptAddress 接收地址
     * @return
     */
    private OrderEntity buildOrder(MailingSaveDTO mailingSaveDTO, AddressBookDTO sendAddress, AddressBookDTO receiptAddress) {
        OrderEntity entity = OrderEntity.builder()
                // 用户ID
                .memberId(mailingSaveDTO.getMemberId())

                .senderName(sendAddress.getName())
                .senderPhone(sendAddress.getPhoneNumber())
                .senderProvinceId(sendAddress.getProvinceId())
                .senderCityId(sendAddress.getCityId())
                .senderCountyId(sendAddress.getCountyId())
                .senderAddress(sendAddress.getAddress())
                .senderAddressId(mailingSaveDTO.getSendAddress())

                .receiverName(receiptAddress.getName())
                .receiverPhone(receiptAddress.getPhoneNumber())
                .receiverProvinceId(receiptAddress.getProvinceId())
                .receiverCityId(receiptAddress.getCityId())
                .receiverCountyId(receiptAddress.getCountyId())
                .receiverAddress(receiptAddress.getAddress())
                .receiverAddressId(mailingSaveDTO.getReceiptAddress())

                .paymentMethod(mailingSaveDTO.getPayMethod())
                .paymentStatus(1) // 默认未付款
                .estimatedStartTime(mailingSaveDTO.getPickUpTime())
                .pickupType(mailingSaveDTO.getPickupType())
                .build();

        entity.setOrderType(entity.getReceiverCityId().equals(entity.getSenderCityId()) ? OrderType.INCITY.getCode() : OrderType.OUTCITY.getCode());
        return entity;
    }

    /**
     * 根据地址计算网点
     *
     * @param address 地址
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
        String location = StrUtil.format("{},{}", lngStr, latStr);

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
     *
     * @param mailingSaveDTO 下单信息
     * @return
     */
    private OrderCargoEntity buildOrderCargo(MailingSaveDTO mailingSaveDTO) {
        OrderCargoEntity cargoEntity = new OrderCargoEntity();
        cargoEntity.setName(mailingSaveDTO.getGoodsName());
        cargoEntity.setGoodsTypeId(mailingSaveDTO.getGoodsType());

        // 数量
        cargoEntity.setQuantity(mailingSaveDTO.getGoodNum());

        // 重量
        cargoEntity.setTotalWeight(mailingSaveDTO.getTotalWeight());
        cargoEntity.setWeight(mailingSaveDTO.getTotalWeight());

        // 体积
        cargoEntity.setTotalVolume(mailingSaveDTO.getTotalVolume());
        cargoEntity.setVolume(mailingSaveDTO.getTotalVolume());
        return cargoEntity;
    }

    /**
     * 根据发收件人地址获取起止机构ID 调用机构范围微服务
     *
     * @param order 订单
     * @return 位置信息
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
     *
     * @param orderEntity 订单
     * @param orderLocation 位置
     */
    private void noticeOrderStatusChange(OrderEntity orderEntity, OrderLocationEntity orderLocation) {
        //{"order":{"orderId":123, "agencyId": 8001, "taskType":1, "mark":"带包装", "longitude":116.111, "latitude":39.00, "created":1654224658728, "estimatedStartTime": 1654224658728}, "created":123456}
        String[] split = orderLocation.getSendLocation().split(",");
        double lnt = Double.parseDouble(split[0]);
        double lat = Double.parseDouble(split[1]);
        OrderMsg build = OrderMsg.builder()
                .created(orderEntity.getCreateTime())
                .estimatedEndTime(orderEntity.getEstimatedStartTime())
                .mark(orderEntity.getMark())
                .taskType(1)
                .latitude(lat)
                .longitude(lnt)
                .agencyId(orderEntity.getCurrentAgencyId())
                .orderId(orderEntity.getId())
                .build();
        //发送消息
        this.mqService.sendMsg(rabbitmqOrderStatusExchange, null, build);
    }
}
