package com.sl.ms.oms.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.sl.ms.oms.entity.OrderCargoEntity;

import java.util.List;

/**
 * 货品总重量  服务类
 */
public interface OrderCargoService extends IService<OrderCargoEntity> {


    /**
     * 保存货物信息
     *
     * @param record 货物信息
     * @return 货物信息
     */
    OrderCargoEntity saveSelective(OrderCargoEntity record);

    /**
     * 获取货物列表
     *
     * @param tranOrderId 运单id
     * @param orderId     订单id
     * @return 货物列表
     */
    List<OrderCargoEntity> findAll(Long tranOrderId, Long orderId);
}
