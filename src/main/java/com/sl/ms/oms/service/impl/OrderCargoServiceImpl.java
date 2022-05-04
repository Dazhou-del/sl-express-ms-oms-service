package com.sl.ms.oms.service.impl;

import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sl.ms.oms.entity.OrderCargoEntity;
import com.sl.ms.oms.mapper.OrderCargoMapper;
import com.sl.ms.oms.service.OrderCargoService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 货品总重量  服务实现类
 */
@Service
public class OrderCargoServiceImpl extends ServiceImpl<OrderCargoMapper, OrderCargoEntity>
        implements OrderCargoService {

    @Override
    public OrderCargoEntity saveSelective(OrderCargoEntity record) {
        super.saveOrUpdate(record);
        return record;
    }

    @Override
    public List<OrderCargoEntity> findAll(Long tranOrderId, Long orderId) {
        LambdaQueryWrapper<OrderCargoEntity> queryWrapper = new LambdaQueryWrapper<>();
        if (ObjectUtil.isNotEmpty(tranOrderId)) {
            queryWrapper.eq(OrderCargoEntity::getTranOrderId, tranOrderId);
        }
        if (ObjectUtil.isNotEmpty(orderId)) {
            queryWrapper.eq(OrderCargoEntity::getOrderId, orderId);
        }
        queryWrapper.orderBy(true, false, OrderCargoEntity::getCreated);
        return super.list(queryWrapper);
    }
}
