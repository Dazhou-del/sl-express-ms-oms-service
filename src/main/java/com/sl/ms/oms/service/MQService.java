package com.sl.ms.oms.service;

/**
 * 消息处理类
 *
 * @author zzj
 * @version 1.0
 */
public interface MQService {

    /**
     * 发送消息
     *
     * @param xchange    交换机
     * @param routingKey 路由key
     * @param msg        消息对象，会将对象序列化成json字符串发出
     * @return 是否成功
     */
    Boolean sendMsg(String xchange, String routingKey, Object msg);
}
