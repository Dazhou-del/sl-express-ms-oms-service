package com.sl.ms.oms.service.impl;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sl.ms.oms.dto.OrderSearchDTO;
import com.sl.ms.oms.entity.OrderCargoEntity;
import com.sl.ms.oms.entity.OrderEntity;
import com.sl.ms.oms.entity.OrderLocationEntity;
import com.sl.ms.oms.enums.OrderPaymentStatus;
import com.sl.ms.oms.enums.OrderPickupType;
import com.sl.ms.oms.enums.OrderStatus;
import com.sl.ms.oms.mapper.OrderMapper;
import com.sl.ms.oms.service.CrudOrderService;
import com.sl.ms.oms.service.OrderCargoService;
import com.sl.ms.oms.service.OrderLocationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单  服务实现类
 */
@Service
@Slf4j
public class CrudOrderServiceImpl extends ServiceImpl<OrderMapper, OrderEntity> implements CrudOrderService {

    @Autowired
    private OrderCargoService orderCargoService;

    @Autowired
    private OrderLocationService orderLocationService;

    @Transactional
    @Override
    public OrderEntity saveOrder(OrderEntity order, OrderCargoEntity orderCargo, OrderLocationEntity orderLocation) {
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
        return null;
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
//        //发件人信息
//        if (StrUtil.isNotEmpty(order.getSenderName())) {
//            lambdaQueryWrapper.like(OrderEntity::getSenderName, order.getSenderName());
//        }
//        if (StrUtil.isNotEmpty(order.getSenderPhone())) {
//            lambdaQueryWrapper.like(OrderEntity::getSenderPhone, order.getSenderPhone());
//        }
//        if (StrUtil.isNotEmpty(order.getSenderProvinceId())) {
//            lambdaQueryWrapper.eq(OrderEntity::getSenderProvinceId, order.getSenderProvinceId());
//        }
//        if (StrUtil.isNotEmpty(order.getSenderCityId())) {
//            lambdaQueryWrapper.eq(OrderEntity::getSenderCityId, order.getSenderCityId());
//        }
//        if (StrUtil.isNotEmpty(order.getSenderCountyId())) {
//            lambdaQueryWrapper.eq(OrderEntity::getSenderCountyId, order.getSenderCountyId());
//        }
//        //收件人信息
//        if (StrUtil.isNotEmpty(order.getReceiverName())) {
//            lambdaQueryWrapper.like(OrderEntity::getReceiverName, order.getReceiverName());
//        }
//        if (StrUtil.isNotEmpty(order.getReceiverPhone())) {
//            lambdaQueryWrapper.like(OrderEntity::getReceiverPhone, order.getReceiverPhone());
//        }
//        if (StrUtil.isNotEmpty(order.getReceiverProvinceId())) {
//            lambdaQueryWrapper.eq(OrderEntity::getReceiverProvinceId, order.getReceiverProvinceId());
//        }
//        if (StrUtil.isNotEmpty(order.getReceiverCityId())) {
//            lambdaQueryWrapper.eq(OrderEntity::getReceiverCityId, order.getReceiverCityId());
//        }
//        if (StrUtil.isNotEmpty(order.getReceiverCountyId())) {
//            lambdaQueryWrapper.eq(OrderEntity::getReceiverCountyId, order.getReceiverCountyId());
//        }
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

    @Override
    public IPage<OrderEntity> pageLikeForCustomer(OrderSearchDTO orderSearchDTO) {

        Integer page = orderSearchDTO.getPage();
        Integer pageSize = orderSearchDTO.getPageSize();

        IPage<OrderEntity> iPage = new Page<>(page, pageSize);

        LambdaQueryWrapper<OrderEntity> orderQueryWrapper = new LambdaQueryWrapper<>();
        orderQueryWrapper.eq(ObjectUtil.isNotEmpty(orderSearchDTO.getId()), OrderEntity::getId, orderSearchDTO.getId());
        orderQueryWrapper.like(StrUtil.isNotEmpty(orderSearchDTO.getKeyword()), OrderEntity::getId, orderSearchDTO.getKeyword());
        orderQueryWrapper.eq(ObjectUtil.isNotEmpty(orderSearchDTO.getMemberId()), OrderEntity::getMemberId, orderSearchDTO.getMemberId());
//        orderQueryWrapper.eq(StrUtil.isNotEmpty(orderSearchDTO.getReceiverPhone()), OrderEntity::getReceiverPhone, orderSearchDTO.getReceiverPhone());
        orderQueryWrapper.orderByDesc(OrderEntity::getCreateTime);
        return page(iPage, orderQueryWrapper);
    }
}
