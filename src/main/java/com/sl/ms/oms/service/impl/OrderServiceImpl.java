package com.sl.ms.oms.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sl.ms.oms.dto.MailingSaveDTO;
import com.sl.ms.oms.entity.OrderCargoEntity;
import com.sl.ms.oms.entity.OrderEntity;
import com.sl.ms.oms.enums.OrderPaymentStatus;
import com.sl.ms.oms.enums.OrderPickupType;
import com.sl.ms.oms.enums.OrderStatus;
import com.sl.ms.oms.enums.OrderType;
import com.sl.ms.oms.mapper.OrderMapper;
import com.sl.ms.oms.service.CrudOrderService;
import com.sl.ms.oms.service.MQService;
import com.sl.ms.oms.service.OrderCargoService;
import com.sl.ms.oms.service.OrderService;
import com.sl.ms.user.api.AddressBookFeign;
import com.sl.ms.user.domain.dto.AddressBookDto;
import com.sl.transport.common.util.Result;
import com.sl.transport.common.vo.R;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.ObjectUtils;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

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

    @Resource
    private AddressBookFeign addressBookFeign;

    @Autowired
    private CrudOrderService crudOrderService;

    @Resource
    AreaApi areaApi;

    @Override
    public OrderEntity mailingSave(MailingSaveDTO mailingSaveDTO) {
        // 获取地址详细信息
        AddressBookDto sendAddress = addressBookFeign.detail(mailingSaveDTO.getSendAddress());
        AddressBookDto receiptAddress = addressBookFeign.detail(mailingSaveDTO.getReceiptAddress());
        log.info("sendAddress:{},{} receiptAddress:{},{}", mailingSaveDTO.getSendAddress(), sendAddress, mailingSaveDTO.getReceiptAddress(), receiptAddress);
        if (ObjectUtil.isEmpty(sendAddress) || ObjectUtil.isEmpty(receiptAddress)) {
            log.error("获取地址详细信息 失败 mailingSaveDTO :{}", mailingSaveDTO);
            return null;
        }
        // 构建实体
        OrderEntity order = buildOrder(mailingSaveDTO, sendAddress, receiptAddress);
        //TODO 预计到达时间
        order.setEstimatedArrivalTime(LocalDateTime.now().plus(2, ChronoUnit.DAYS));
        //TODO 需要计算距离和运费
        order.setAmount(BigDecimal.valueOf(20));

        // 根据发收件人地址获取起止机构ID TODO 调用机构范围微服务

        OrderCargoEntity orderCargo = buildOrderCargo(mailingSaveDTO);

        OrderEntity entity = crudOrderService.saveOrder(order, orderCargo);
        log.info("订单信息入库:{}", order);
        if (ObjectUtil.isEmpty(entity)) {
            return null;
        }
        // 生成订单mq 调度服务用来调度 之后快递员服务处理
        noticeOrderStatusChange(order);
        return entity;
    }

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
     * 根据计算获取网点
     *
     * @param entity
     * @return
     */
    private Result getAgencyId(OrderEntity entity) {
        String address = senderFullAddress(entity);
        if (ObjectUtil.isEmpty(address)) {
            return Result.error("下单时发货地址不能为空");
        }
        String location = EntCoordSyncJob.getCoordinate(address);
        log.info("订单发货地址和坐标-->" + address + "--" + location);
        if (ObjectUtil.isEmpty(location)) {
            return Result.error("下单时发货地址不能为空");
        }
        //根据坐标获取区域检查区域是否正常
        Map map = EntCoordSyncJob.getLocationByPosition(location);
        if (ObjectUtils.isEmpty(map)) {
            return Result.error("根据地图获取区域信息为空");
        }
        String adcode = map.getOrDefault("adcode", "").toString();
        Result<Area> Result = areaApi.getByCode(adcode + "000000");
        if (!r.getIsSuccess()) {
            R.error(r.getMsg());
        }
        Area area = r.getData();
        if (area == null) {
            return R.error("区域编码:" + adcode + "区域信息未从库中获取到");
        }
        Long areaId = area.getId();
        if (!entity.getSenderCountyId().equals(String.valueOf(areaId))) {
            log.info("参数中区域id和坐标计算出真实区域id不同,数据不合法。{},{}", entity.getSenderCountyId(), areaId);
            return R.error("参数中区域id和坐标计算出真实区域id不同，数据不合法");
        }
        List<AgencyScopeDto> agencyScopes = agencyScopeFeign.findAllAgencyScope(areaId + "", null, null, null);
        if (agencyScopes == null || agencyScopes.size() == 0) {
            return R.error("根据区域无法从机构范围获取网点信息列表");
        }
        R res = calcuate(location, agencyScopes);
        if (!res.get("code").toString().equals("0")) {
            return res;
        }
        Result result = new Result();
        result.put("code", 0);
        result.put("agencyId", res.get("agencyId").toString());
        result.put("location", location);
        return result;
    }

    @SneakyThrows
    private String senderFullAddress(OrderEntity entity) {
        StringBuffer stringBuffer = new StringBuffer();

        Long province = Long.valueOf(entity.getSenderProvinceId());
        Long city = Long.valueOf(entity.getSenderCityId());
        Long county = Long.valueOf(entity.getSenderCountyId());

        Set areaIdSet = new HashSet();
        areaIdSet.add(province);
        areaIdSet.add(city);
        areaIdSet.add(county);

        CompletableFuture<Map<Long, Area>> areaMapFuture = CompletableFuture.areaMapFuture(areaApi, null, areaIdSet);
        Map<Long, Area> areaMap = areaMapFuture.get();

        stringBuffer.append(areaMap.get(province).getName());
        stringBuffer.append(areaMap.get(city).getName());
        stringBuffer.append(areaMap.get(county).getName());
        stringBuffer.append(entity.getSenderAddress());

        return stringBuffer.toString();
    }

    private OrderCargoEntity buildOrderCargo(MailingSaveDTO entity) {
        OrderCargoEntity cargoDto = new OrderCargoEntity();
        cargoDto.setName(entity.getGoodsName());
        cargoDto.setGoodsTypeId(entity.getGoodsType());
        cargoDto.setWeight(new BigDecimal(entity.getGoodsWeight()));
        cargoDto.setQuantity(1);
        cargoDto.setTotalWeight(cargoDto.getWeight().multiply(new BigDecimal(cargoDto.getQuantity())));
        return cargoDto;
    }

    private void noticeOrderStatusChange(OrderEntity orderEntity) {
        //{"order":{}, "created":123456}
        Map<Object, Object> msg = MapUtil.builder()
                .put("order", JSONUtil.toJsonStr(orderEntity))
                .put("created", System.currentTimeMillis()).build();
        //发送消息
        this.mqService.sendMsg(rabbitmqOrderStatusExchange, null, msg);
    }
}
