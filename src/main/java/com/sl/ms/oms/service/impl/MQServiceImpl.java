package com.sl.ms.oms.service.impl;

import cn.hutool.json.JSONUtil;
import com.sl.ms.oms.service.MQService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/**
 * 消息处理类
 *
 * @author zzj
 * @version 1.0
 */
@Service
@Slf4j
public class MQServiceImpl implements MQService {

    @Resource
    private RabbitTemplate rabbitTemplate;
    @Override
    public Boolean sendMsg(String xchange, String routingKey, Object msg) {
        String jsonMsg = JSONUtil.toJsonStr(msg);
        try {
            //发送消息
            this.rabbitTemplate.convertAndSend(xchange, null, jsonMsg);
            return true;
        } catch (Exception e) {
            //发送消息失败，需要将消息持久化到数据库，通过任务调度的方式处理失败的消息
//            FailMsgEntity failMsgEntity = new FailMsgEntity();
//            failMsgEntity.setExchange(xchange);
//            failMsgEntity.setRoutingKey(routingKey);
//            failMsgEntity.setMsg(jsonMsg);
//            this.failMsgService.save(failMsgEntity);

            log.error("消息发送失败！exchange = {}, routingKey = {}, msg = {}", xchange, routingKey, jsonMsg);
        }
        return false;
    }
}
