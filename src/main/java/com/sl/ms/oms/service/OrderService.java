package com.sl.ms.oms.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.sl.ms.oms.dto.MailingSaveDTO;
import com.sl.ms.oms.entity.OrderEntity;

/**
 * 订单状态  服务类
 */
public interface OrderService extends IService<OrderEntity> {

    OrderEntity mailingSave(MailingSaveDTO mailingSaveDTO);
}
