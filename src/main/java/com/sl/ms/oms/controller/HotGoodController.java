package com.sl.ms.oms.controller;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.sl.ms.oms.dto.OrderCargoDTO;
import com.sl.ms.oms.entity.HotGoodEntity;
import com.sl.ms.oms.entity.OrderCargoEntity;
import com.sl.ms.oms.service.HotGoodService;
import com.sl.ms.oms.service.OrderCargoService;
import com.sl.transport.common.util.ObjectUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 热门货物
 */
@RestController
@RequestMapping("/good")
@Slf4j
public class HotGoodController {

    @Resource
    private HotGoodService hotGoodService;


    /**
     * 批量查询货物信息表
     *
     * @param name 热门货品名称
     * @return
     */
    @GetMapping("/hot")
    List<OrderCargoDTO> list(@RequestParam(name = "name", required = false) String name) {
        return hotGoodService.list(Wrappers.<HotGoodEntity>lambdaQuery()
                .like(ObjectUtil.isNotEmpty(name), HotGoodEntity::getName, name)
                .last("limit 20")
        )
        .stream()
        .map(hotGoodEntity -> BeanUtil.toBean(hotGoodEntity, OrderCargoDTO.class))
        .collect(Collectors.toList());
    }

}
