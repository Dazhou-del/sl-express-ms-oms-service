package com.sl.ms.oms.service.impl;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
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
    public OrderEntity saveOrder(OrderEntity order, OrderCargoEntity orderCargo, OrderLocationEntity orderLocation) throws Exception {
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
            return order;
        }
        throw new Exception("保存订单失败");
    }

    @Override
    public Page findByPage(Integer page, Integer pageSize, OrderEntity order) {
        Page iPage = new Page(page, pageSize);
        LambdaQueryWrapper<OrderEntity> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        if (ObjectUtil.isNotEmpty(order.getId())) {
            lambdaQueryWrapper.like(OrderEntity::getId, order.getId());
        }
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
            lambdaQueryWrapper.like(OrderEntity::getReceiverPhone, order.getReceiverPhone());
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

}
