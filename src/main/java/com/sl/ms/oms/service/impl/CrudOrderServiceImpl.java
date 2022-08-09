package com.sl.ms.oms.service.impl;

import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sl.ms.oms.dto.OrderCargoDTO;
import com.sl.ms.oms.dto.OrderPickupDTO;
import com.sl.ms.oms.dto.OrderStatusCountDTO;
import com.sl.ms.oms.entity.OrderCargoEntity;
import com.sl.ms.oms.entity.OrderEntity;
import com.sl.ms.oms.entity.OrderLocationEntity;
import com.sl.ms.oms.enums.MailType;
import com.sl.ms.oms.enums.OrderPaymentStatus;
import com.sl.ms.oms.enums.OrderPickupType;
import com.sl.ms.oms.enums.OrderStatus;
import com.sl.ms.oms.mapper.OrderMapper;
import com.sl.ms.oms.service.CrudOrderService;
import com.sl.ms.oms.service.OrderCargoService;
import com.sl.ms.oms.service.OrderLocationService;
import com.sl.ms.user.api.MemberFeign;
import com.sl.ms.user.domain.dto.MemberDTO;
import com.sl.transport.common.vo.TradeStatusMsg;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 订单  服务实现类
 */
@Service
@Slf4j
public class CrudOrderServiceImpl extends ServiceImpl<OrderMapper, OrderEntity> implements CrudOrderService {

    @Resource
    private OrderCargoService orderCargoService;

    @Resource
    private OrderLocationService orderLocationService;

    @Resource
    private MemberFeign memberFeign;

    @Transactional
    @Override
    public void saveOrder(OrderEntity order, OrderCargoEntity orderCargo, OrderLocationEntity orderLocation) throws Exception {
        order.setCreateTime(LocalDateTime.now());
        order.setPaymentStatus(OrderPaymentStatus.UNPAID.getStatus());
        if (OrderPickupType.NO_PICKUP.getCode().equals(order.getPickupType())) {
            order.setStatus(OrderStatus.OUTLETS_SINCE_SENT.getCode());
        } else {
            order.setStatus(OrderStatus.PENDING.getCode());
        }
        // 保存订单
        if (save(order)) {
            // 保存货物
            orderCargo.setOrderId(order.getId());
            orderCargoService.saveSelective(orderCargo);
            // 保存位置
            orderLocation.setOrderId(order.getId());
            orderLocationService.save(orderLocation);
            return;
        }
        throw new Exception("保存订单失败");
    }

    @Override
    public Page<OrderEntity> findByPage(Integer page, Integer pageSize, OrderEntity order) {
        Page<OrderEntity> iPage = new Page<>(page, pageSize);
        LambdaQueryWrapper<OrderEntity> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        if (ObjectUtil.isNotEmpty(order.getId())) {
            lambdaQueryWrapper.like(OrderEntity::getId, order.getId());
        }
        lambdaQueryWrapper.ne(OrderEntity::getStatus, OrderStatus.DEL.getCode());
        if (order.getStatus() != null) {
            lambdaQueryWrapper.eq(OrderEntity::getStatus, order.getStatus());
        }
        if (order.getPaymentStatus() != null) {
            lambdaQueryWrapper.eq(OrderEntity::getPaymentStatus, order.getPaymentStatus());
        }
        //发件人信息
        if (ObjectUtil.isNotEmpty(order.getSenderName())) {
            lambdaQueryWrapper.like(OrderEntity::getSenderName, order.getSenderName());
        }
        if (StrUtil.isNotEmpty(order.getSenderPhone())) {
            lambdaQueryWrapper.like(OrderEntity::getSenderPhone, order.getSenderPhone());
        }
        if (ObjectUtil.isNotEmpty(order.getSenderProvinceId())) {
            lambdaQueryWrapper.eq(OrderEntity::getSenderProvinceId, order.getSenderProvinceId());
        }
        if (ObjectUtil.isNotEmpty(order.getSenderCityId())) {
            lambdaQueryWrapper.eq(OrderEntity::getSenderCityId, order.getSenderCityId());
        }
        if (ObjectUtil.isNotEmpty(order.getSenderCountyId())) {
            lambdaQueryWrapper.eq(OrderEntity::getSenderCountyId, order.getSenderCountyId());
        }
        //收件人信息
        if (ObjectUtil.isNotEmpty(order.getReceiverName())) {
            lambdaQueryWrapper.like(OrderEntity::getReceiverName, order.getReceiverName());
        }
        if (StrUtil.isNotEmpty(order.getReceiverPhone())) {
            lambdaQueryWrapper.or()
                    .eq(OrderEntity::getReceiverPhone, order.getReceiverPhone())
                    .notIn(ObjectUtil.isNotEmpty(order.getMemberId()), OrderEntity::getStatus, Arrays.asList(OrderStatus.CLOSE.getCode(), OrderStatus.CANCELLED.getCode(), OrderStatus.PENDING.getCode()));
        }
        if (ObjectUtil.isNotEmpty(order.getReceiverProvinceId())) {
            lambdaQueryWrapper.eq(OrderEntity::getReceiverProvinceId, order.getReceiverProvinceId());
        }
        if (ObjectUtil.isNotEmpty(order.getReceiverCityId())) {
            lambdaQueryWrapper.eq(OrderEntity::getReceiverCityId, order.getReceiverCityId());
        }
        if (ObjectUtil.isNotEmpty(order.getReceiverCountyId())) {
            lambdaQueryWrapper.eq(OrderEntity::getReceiverCountyId, order.getReceiverCountyId());
        }
        lambdaQueryWrapper.eq(ObjectUtil.isNotEmpty(order.getMemberId()), OrderEntity::getMemberId, order.getMemberId());
        lambdaQueryWrapper.orderBy(true, false, OrderEntity::getCreateTime);
        return page(iPage, lambdaQueryWrapper);
    }

    @Override
    public List<OrderEntity> findAll(List<Long> ids) {
        LambdaQueryWrapper<OrderEntity> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        if (ids != null && ids.size() > 0) {
            lambdaQueryWrapper.in(OrderEntity::getId, ids);
        }
        lambdaQueryWrapper.orderBy(true, false, OrderEntity::getCreateTime);
        return list(lambdaQueryWrapper);
    }

    /**
     * 统计各个状态的数量
     *
     * @return 状态数量数据
     * @param memberId 用户ID
     */
    @Override
    public List<OrderStatusCountDTO> groupByStatus(Long memberId) {
        List<OrderStatusCountDTO> list = new ArrayList<>();
        MemberDTO detail = memberFeign.detail(memberId);
        // 收件数量
        long count = count(Wrappers.<OrderEntity>lambdaQuery()
                .eq(OrderEntity::getReceiverPhone, detail.getPhone())
        );
        OrderStatusCountDTO orderStatusCountDTO = new OrderStatusCountDTO();
        orderStatusCountDTO.setStatus(MailType.RECEIVE);
        orderStatusCountDTO.setStatusCode(MailType.RECEIVE.getCode());
        orderStatusCountDTO.setCount(count);
        list.add(orderStatusCountDTO);

        // 寄件数量
        long sendCount = count(Wrappers.<OrderEntity>lambdaQuery()
                .eq(OrderEntity::getMemberId, memberId)
        );
        OrderStatusCountDTO send = new OrderStatusCountDTO();
        send.setStatus(MailType.SEND);
        send.setStatusCode(MailType.SEND.getCode());
        send.setCount(sendCount);
        list.add(send);
        return list;
    }

    /**
     * 状态更新
     * @param orderId 订单ID
     * @param code 状态码
     */
    @Override
    public void updateStatus(List<Long> orderId, Integer code) {
        update(Wrappers.<OrderEntity>lambdaUpdate()
                .in(OrderEntity::getId, orderId)
        .set(OrderEntity::getStatus, code));
    }

    /**
     * 快递员取件更新订单和货物信息
     * @param orderPickupDTO 订单和货物信息
     */
    @Transactional
    @Override
    public void orderPickup(OrderPickupDTO orderPickupDTO) {
        //5.更新订单
        OrderEntity orderEntity = new OrderEntity();
        orderEntity.setPaymentMethod(orderPickupDTO.getPayMethod());//付款方式,1.预结2到付
        orderEntity.setPaymentStatus(OrderPaymentStatus.UNPAID.getStatus());//付款状态,1.未付2已付
        orderEntity.setAmount(orderPickupDTO.getAmount());//金额
        orderEntity.setStatus(OrderStatus.PICKED_UP.getCode());//订单状态
        orderEntity.setMark(orderPickupDTO.getRemark());//备注
        orderEntity.setId(orderPickupDTO.getId());
        updateById(orderEntity);

        //6.更新订单货品
        BigDecimal volume = NumberUtil.round(orderPickupDTO.getVolume(), 4);
        BigDecimal weight = NumberUtil.round(orderPickupDTO.getWeight(), 2);
        OrderCargoDTO cargoDTO = orderCargoService.findByOrderId(orderPickupDTO.getId());
        OrderCargoEntity orderCargoEntity = new OrderCargoEntity();
        orderCargoEntity.setName(orderPickupDTO.getGoodName());//货物名称
        orderCargoEntity.setVolume(volume);//货品体积，单位m^3
        orderCargoEntity.setWeight(weight);//货品重量，单位kg
        orderCargoEntity.setTotalVolume(volume);//货品总体积，单位m^3
        orderCargoEntity.setTotalWeight(weight);//货品总重量，单位kg
        orderCargoEntity.setId(cargoDTO.getId());
        orderCargoService.saveOrUpdate(orderCargoEntity);
    }

    /**
     * 更新支付状态
     * @param ids 订单ID
     * @param status 状态
     */
    @Override
    public void updatePayStatus(List<Long> ids, Integer status) {
        LambdaUpdateWrapper<OrderEntity> updateWrapper = Wrappers.<OrderEntity>lambdaUpdate()
                .set(OrderEntity::getPaymentStatus, status)
                .in(OrderEntity::getId, ids);
        update(updateWrapper);
    }

    /**
     * 退款成功
     * @param msgList 退款消息
     */
    @Override
    public void updateRefundInfo(List<TradeStatusMsg> msgList) {

//        LambdaUpdateWrapper<OrderEntity> updateWrapper = Wrappers.<OrderEntity>lambdaUpdate()
//                .set(OrderEntity::getRefund, )
//                .in(OrderEntity::getId, msgList.stream().map(TradeStatusMsg::getProductOrderNo).collect(Collectors.toList()));
//        update(updateWrapper);
    }
}
