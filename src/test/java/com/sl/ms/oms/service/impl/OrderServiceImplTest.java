package com.sl.ms.oms.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.json.JSONUtil;
import com.sl.ms.oms.dto.MailingSaveDTO;
import com.sl.ms.oms.dto.OrderDTO;
import com.sl.ms.oms.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class OrderServiceImplTest {

    @Resource
    private OrderService orderService;

    @Test
    void mailingSave() throws Exception {
        String json = "{\n" +
                "  \"goodsName\": \"食品\",\n" +
                "  \"goodsType\": \"1\",\n" +
                "  \"totalWeight\": \"1\",\n" +
                "   \"totalVolume\": \"1\",\n" +
                "  \"memberId\": 12323432453466712,\n" +
                "  \"payMethod\": 1,\n" +
                "  \"pickUpTime\": \"2022-07-25 13:59:25\",\n" +
                "  \"pickupType\": 2,\n" +
                "  \"receiptAddress\": 1,\n" +
                "  \"sendAddress\": 2\n" +
                "}";
        OrderDTO orderDTO = orderService.mailingSave(JSONUtil.toBean(json, MailingSaveDTO.class));
    }
}