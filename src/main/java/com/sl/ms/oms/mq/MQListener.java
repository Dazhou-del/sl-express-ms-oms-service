package com.sl.ms.oms.mq;

import cn.hutool.json.JSONUtil;
import com.sl.ms.oms.dto.OrderPickupDTO;
import com.sl.ms.oms.service.CrudOrderService;
import com.sl.transport.common.constant.Constants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * 消息处理
 *
 * @author zzj
 * @version 1.0
 */
@Slf4j
@Component
public class MQListener {

    @Resource
    private CrudOrderService crudOrderService;

    /**
     * 快递员取件后更新订单
     *
     * @param msg 消息
     */
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = Constants.MQ.Queues.OMS_COURIER_PICKUP_UPDATE_ORDER),
            exchange = @Exchange(name = Constants.MQ.Exchanges.COURIER, type = ExchangeTypes.TOPIC),
            key = Constants.MQ.RoutingKeys.COURIER_UPDATE_ORDER
    ))
    public void listenCourierPickupUpdateOrderMsg(String msg) {
        log.info("接收到快递员取件成功的消息 ({})-> {}", Constants.MQ.Queues.OMS_COURIER_PICKUP_UPDATE_ORDER, msg);
        OrderPickupDTO orderPickupDTO = JSONUtil.toBean(msg, OrderPickupDTO.class);
        this.crudOrderService.orderPickup(orderPickupDTO);
    }
}
