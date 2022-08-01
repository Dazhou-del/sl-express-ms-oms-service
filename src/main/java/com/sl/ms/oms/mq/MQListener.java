package com.sl.ms.oms.mq;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.lang.TypeReference;
import cn.hutool.json.JSONUtil;
import com.sl.ms.oms.dto.OrderPickupDTO;
import com.sl.ms.oms.enums.OrderPaymentStatus;
import com.sl.ms.oms.service.CrudOrderService;
import com.sl.ms.trade.enums.RefundStatusEnum;
import com.sl.ms.trade.enums.TradingStateEnum;
import com.sl.ms.work.api.TransportOrderFeign;
import com.sl.ms.work.domain.dto.TransportOrderDTO;
import com.sl.transport.common.constant.Constants;
import com.sl.transport.common.vo.CourierMsg;
import com.sl.transport.common.vo.TradeStatusMsg;
import com.sl.transport.common.vo.TransportOrderStatusMsg;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;
import java.util.stream.Collectors;

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

    @Resource
    private TransportOrderFeign transportOrderFeign;
    /**
     * 快递员取件后更新订单
     *Constants.MQ.Exchanges.COURIER, Constants.MQ.RoutingKeys.COURIER_PICKUP
     * @param msg 消息
     */
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = Constants.MQ.Queues.OMS_COURIER_PICKUP_UPDATE_ORDER),
            exchange = @Exchange(name = Constants.MQ.Exchanges.COURIER, type = ExchangeTypes.TOPIC),
            key = Constants.MQ.RoutingKeys.COURIER_PICKUP
    ))
    public void listenCourierPickupUpdateOrderMsg(String msg) {
        log.info("接收到快递员取件成功的消息 ({})-> {}", Constants.MQ.Queues.OMS_COURIER_PICKUP_UPDATE_ORDER, msg);
        CourierMsg courierMsg = JSONUtil.toBean(msg, CourierMsg.class);
        OrderPickupDTO orderPickupDTO = JSONUtil.toBean(courierMsg.getInfo(), OrderPickupDTO.class);
        this.crudOrderService.orderPickup(orderPickupDTO);
    }

    /**
     * 更新运单状态
     *
     * @param msg 消息
     */
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = Constants.MQ.Queues.OMS_TRANSPORT_ORDER_UPDATE_STATUS),
            exchange = @Exchange(name = Constants.MQ.Exchanges.TRANSPORT_ORDER_DELAYED, type = ExchangeTypes.TOPIC, delayed = Constants.MQ.DELAYED),
            key = Constants.MQ.RoutingKeys.TRANSPORT_ORDER_UPDATE_STATUS_PREFIX + "#"
    ))
    public void listenTransportOrderUpdateStatusMsg(String msg) {
        log.info("接收到更新运单状态的消息 ({})-> {}", Constants.MQ.Queues.OMS_TRANSPORT_ORDER_UPDATE_STATUS, msg);
        TransportOrderStatusMsg transportOrderStatusMsg = JSONUtil.toBean(msg, TransportOrderStatusMsg.class);
        // 具体业务逻辑的处理
        List<TransportOrderDTO> list = transportOrderFeign.findByIds(transportOrderStatusMsg.getIdList().toArray(String[]::new));
        this.crudOrderService.updateStatus(list.stream().map(TransportOrderDTO::getOrderId).collect(Collectors.toList()), transportOrderStatusMsg.getStatusCode());
    }

    /**
     * 更新支付结果
     * 支付成功
     * @param msg 消息
     */
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = Constants.MQ.Queues.OMS_TRADE_UPDATE_STATUS),
            exchange = @Exchange(name = Constants.MQ.Exchanges.TRADE, type = ExchangeTypes.TOPIC),
            key = Constants.MQ.RoutingKeys.TRADE_UPDATE_STATUS
    ))
    public void listenTradeUpdatePayStatusMsg(String msg) {
        log.info("接收到支付结果状态的消息 ({})-> {}", Constants.MQ.Queues.OMS_TRADE_UPDATE_STATUS, msg);
        List<TradeStatusMsg> tradeStatusMsgList = JSONUtil.toBean(msg, new TypeReference<List<TradeStatusMsg>>() {}, false);

        // 只处理支付成功的
        List<TradeStatusMsg> msgList = tradeStatusMsgList.stream().filter(v -> v.getStatusCode().equals(TradingStateEnum.YJS.getCode())).collect(Collectors.toList());
        this.crudOrderService.updatePayStatus(msgList.stream().map(TradeStatusMsg::getProductOrderNo).collect(Collectors.toList()), OrderPaymentStatus.PAID.getStatus());
    }

    /**
     * 更新退款结果
     * 退款
     * @param msg 消息
     */
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = Constants.MQ.Queues.OMS_TRADE_REFUND_STATUS),
            exchange = @Exchange(name = Constants.MQ.Exchanges.TRADE, type = ExchangeTypes.TOPIC),
            key = Constants.MQ.RoutingKeys.REFUND_UPDATE_STATUS
    ))
    public void listenTradeUpdateRefundStatusMsg(String msg) {
        log.info("接收到退款订单的消息 ({})-> {}", Constants.MQ.Queues.OMS_TRADE_REFUND_STATUS, msg);
        List<TradeStatusMsg> tradeStatusMsgList = JSONUtil.toBean(msg, new TypeReference<List<TradeStatusMsg>>() {}, false);

        // 只处理需要退款的
        List<TradeStatusMsg> msgList = tradeStatusMsgList.stream().filter(v -> v.getStatusCode().equals(RefundStatusEnum.SUCCESS.getCode())).collect(Collectors.toList());
        this.crudOrderService.updateRefundInfo(msgList);
    }
}
