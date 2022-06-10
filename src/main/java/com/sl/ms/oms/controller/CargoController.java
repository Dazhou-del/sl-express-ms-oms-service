package com.sl.ms.oms.controller;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sl.ms.oms.dto.OrderCargoDto;
import com.sl.ms.oms.entity.OrderCargoEntity;
import com.sl.ms.oms.service.OrderCargoService;
import com.sl.transport.common.util.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 货品总重量  前端控制器
 */
@RestController
@RequestMapping("cargo")
@Slf4j
public class CargoController {

    @Resource
    private OrderCargoService orderCargoService;

    /**
     * 获取货物列表
     *
     * @param tranOrderId 运单id
     * @param orderId     订单id
     * @return 货物列表
     */
    @GetMapping("")
    public List<OrderCargoDto> findAll(@RequestParam(name = "tranOrderId", required = false) Long tranOrderId,
                                       @RequestParam(name = "orderId", required = false) Long orderId) {
        log.info("oms --- 查询货物列表");
        return orderCargoService.findAll(tranOrderId, orderId).stream().map(orderCargo -> {
            log.info("oms ---  orderCargoService.findAll  result:{}", orderCargo);
            return BeanUtil.toBean(orderCargo, OrderCargoDto.class);
        }).collect(Collectors.toList());
    }

    @GetMapping("/list")
    public List<OrderCargoDto> list(@RequestParam(name = "orderIds", required = false) List<String> orderIds) {
        LambdaQueryWrapper<OrderCargoEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(CollUtil.isNotEmpty(orderIds), OrderCargoEntity::getOrderId, orderIds);

        return orderCargoService.list(wrapper).stream()
                .map(orderCargo -> BeanUtil.toBean(orderCargo, OrderCargoDto.class))
                .collect(Collectors.toList());
    }

    /**
     * 添加货物
     *
     * @param dto 货物信息
     * @return 货物信息
     */
    @PostMapping("")
    public OrderCargoDto save(@RequestBody OrderCargoDto dto) {
        log.info("保存货物信息：{}", dto);
        OrderCargoEntity orderCargo = BeanUtil.toBean(dto, OrderCargoEntity.class);
        orderCargoService.saveOrUpdate(orderCargo);
        log.info("货物信息入库：{}", dto);
        return BeanUtil.toBean(orderCargo, OrderCargoDto.class);
    }

    /**
     * 更新货物信息
     *
     * @param id  货物id
     * @param dto 货物信息
     * @return 货物信息
     */
    @PutMapping("/{id}")
    public OrderCargoDto update(@PathVariable(name = "id") Long id, @RequestBody OrderCargoDto dto) {
        dto.setId(id);
        OrderCargoEntity orderCargo = BeanUtil.toBean(dto, OrderCargoEntity.class);
        orderCargoService.updateById(orderCargo);
        return dto;
    }

    /**
     * 删除货物信息
     *
     * @param id 货物id
     * @return 返回信息
     */
    @DeleteMapping("/{id}")
    public boolean del(@PathVariable(name = "id") Long id) {
        return orderCargoService.removeById(id);
    }


    /**
     * 根据id获取货物详情
     *
     * @param id 货物id
     * @return 货物详情
     */
    @GetMapping("/{id}")
    public OrderCargoDto findById(@PathVariable(name = "id") Long id) {
        OrderCargoEntity orderCargo = orderCargoService.getById(id);
        return BeanUtil.toBean(orderCargo, OrderCargoDto.class);
    }

}
