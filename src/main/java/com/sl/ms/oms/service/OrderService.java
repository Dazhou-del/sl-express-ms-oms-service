package com.sl.ms.oms.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.sl.ms.oms.dto.OrderSearchDTO;
import com.sl.ms.oms.entity.OrderEntity;

import java.util.List;

/**
 * 订单状态  服务类
 */
public interface OrderService extends IService<OrderEntity> {

    /**
     * 新增订单
     *
     * @param order 订单信息
     * @return 订单信息
     */
    OrderEntity saveOrder(OrderEntity order);

    /**
     * 获取订单分页数据
     *
     * @param page     页码
     * @param pageSize 页尺寸
     * @param order    查询条件
     * @return 订单分页数据
     */
    IPage<OrderEntity> findByPage(Integer page, Integer pageSize, OrderEntity order);

    /**
     * 获取订单列表
     *
     * @param ids 订单id列表
     * @return 订单列表
     */
    List<OrderEntity> findAll(List<Long> ids);

    /**
     * 获取订单分页数据 客户端使用
     *
     * @return 订单分页数据
     */
    IPage<OrderEntity> pageLikeForCustomer(OrderSearchDTO orderSearchDTO);

}
