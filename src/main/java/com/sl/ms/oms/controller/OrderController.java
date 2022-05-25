package com.sl.ms.oms.controller;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.sl.ms.oms.dto.OrderDTO;
import com.sl.ms.oms.dto.OrderLocationDto;
import com.sl.ms.oms.dto.OrderSearchDTO;
import com.sl.ms.oms.entity.OrderCargoEntity;
import com.sl.ms.oms.entity.OrderEntity;
import com.sl.ms.oms.entity.OrderLocationEntity;
import com.sl.ms.oms.service.OrderLocationService;
import com.sl.ms.oms.service.OrderService;
import com.sl.transport.common.util.PageResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 订单  前端控制器
 */
@Slf4j
@RestController
@RequestMapping("order")
public class OrderController {

    @Resource
    private OrderService orderService;
    @Resource
    private OrderLocationService orderLocationService;

    /**
     * 新增订单
     *
     * @param orderDTO 订单信息
     * @return 订单信息
     */
    @PostMapping
    public OrderDTO save(@RequestBody OrderDTO orderDTO) {
        log.info("保存订单信息:{}", JSONUtil.toJsonStr(orderDTO));
        OrderEntity order = new OrderEntity();
        //TODO 预计到达时间
        order.setEstimatedArrivalTime(LocalDateTime.now().plus(2, ChronoUnit.DAYS));
        //TODO 需要计算距离和运费
        order.setAmount(BigDecimal.valueOf(20));
        BeanUtil.copyProperties(orderDTO, order);
        OrderCargoEntity orderCargo = BeanUtil.toBean(orderDTO.getOrderCargoDto(), OrderCargoEntity.class);
        orderService.saveOrder(order, orderCargo);
        log.info("订单信息入库:{}", order);
        return BeanUtil.toBean(order, OrderDTO.class);
    }


    // @PostMapping("orderMsg")
    // public Map save(@RequestBody OrderDTO orderDTO) {
    //     Map map = Maps.newHashMap();
    //     if (orderDTO == null) {
    //         return map;
    //     }
    //     if (orderDTO.getOrderCargoDto() == null) {
    //         return map;
    //     }
    //     BigDecimal bigDecimal = orderDTO.getOrderCargoDto().getTotalWeight() == null ? BigDecimal.ZERO : orderDTO.getOrderCargoDto().getTotalWeight();
    //     if (bigDecimal.compareTo(BigDecimal.ZERO) < 1) {
    //         return map;
    //     }
    //     //根据重量和距离
    //     map = orderService.calculateAmount(orderDTO);
    //     return map;
    // }

    /**
     * 修改订单信息
     *
     * @param id       订单id
     * @param orderDTO 订单信息
     * @return 订单信息
     */
    @PutMapping("/{id}")
    public OrderDTO updateById(@PathVariable(name = "id") Long id, @RequestBody OrderDTO orderDTO) {
        orderDTO.setId(id);
        OrderEntity order = BeanUtil.toBean(orderDTO, OrderEntity.class);
        orderService.updateById(order);
        return orderDTO;
    }

    /**
     * 获取订单分页数据
     *
     * @param orderDTO 查询条件
     * @return 订单分页数据
     */
    @PostMapping("/page")
    public PageResponse<OrderDTO> findByPage(@RequestBody OrderDTO orderDTO) {
        OrderEntity orderEntity = BeanUtil.toBean(orderDTO, OrderEntity.class);
        IPage<OrderEntity> orderIPage = orderService.findByPage(orderDTO.getPage(), orderDTO.getPageSize(), orderEntity);
        List<OrderDTO> dtoList = new ArrayList<>();
        orderIPage.getRecords().forEach(order -> {
            dtoList.add(BeanUtil.toBean(order, OrderDTO.class));
        });

        return PageResponse.<OrderDTO>builder()
                .items(dtoList)
                .pageSize(orderDTO.getPageSize())
                .page(orderDTO.getPage())
                .counts(orderIPage.getTotal())
                .pages(orderIPage.getPages()).build();
    }

    /**
     * 根据id获取订单详情
     *
     * @param id 订单Id
     * @return 订单详情
     */
    @GetMapping("/{id}")
    public OrderDTO findById(@PathVariable(name = "id") Long id) {
        OrderEntity orderEntity = orderService.getById(id);
        if (orderEntity != null) {
            return BeanUtil.toBean(orderEntity, OrderDTO.class);
        }
        return null;
    }

    /**
     * 根据id获取集合
     *
     * @param ids 订单Id
     * @return 订单详情
     */
    @GetMapping("ids")
    public List<OrderDTO> findById(@RequestParam(name = "ids") List<Long> ids) {
        List<OrderEntity> orders = orderService.listByIds(ids);
        return orders.stream().map(item -> BeanUtil.toBean(item, OrderDTO.class))
                .collect(Collectors.toList());
    }

    @ResponseBody
    @RequestMapping(value = "pageLikeForCustomer", method = RequestMethod.POST)
    public PageResponse<OrderDTO> pageLikeForCustomer(@RequestBody OrderSearchDTO orderSearchDTO) {
        //查询结果
        IPage<OrderEntity> orderIPage = orderService.pageLikeForCustomer(orderSearchDTO);
        List<OrderDTO> dtoList = new ArrayList<>();
        orderIPage.getRecords().forEach(order -> {
            dtoList.add(BeanUtil.toBean(order, OrderDTO.class));
        });

        return PageResponse.<OrderDTO>builder()
                .items(dtoList)
                .pageSize(orderSearchDTO.getPageSize())
                .page(orderSearchDTO.getPage()).counts(orderIPage.getTotal())
                .pages(orderIPage.getPages()).build();
    }

    @ResponseBody
    @PostMapping("list")
    public List<OrderEntity> list(@RequestBody OrderSearchDTO orderSearchDTO) {
        LambdaQueryWrapper<OrderEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(orderSearchDTO.getStatus() != null, OrderEntity::getStatus, orderSearchDTO.getStatus());
        wrapper.in(CollUtil.isNotEmpty(orderSearchDTO.getReceiverCountyIds()), OrderEntity::getReceiverCountyId, orderSearchDTO.getReceiverCountyIds());
        wrapper.in(CollUtil.isNotEmpty(orderSearchDTO.getSenderCountyIds()), OrderEntity::getSenderCountyId, orderSearchDTO.getSenderCountyIds());
        wrapper.eq(StrUtil.isNotEmpty(orderSearchDTO.getCurrentAgencyId()), OrderEntity::getCurrentAgencyId, orderSearchDTO.getCurrentAgencyId());
        return orderService.list(wrapper);
    }

    @ResponseBody
    @PostMapping("location/saveOrUpdate")
    public OrderLocationDto saveOrUpdateLocation(@RequestBody OrderLocationDto orderLocationDto) {
        try {
            Long id = orderLocationDto.getId();
            Long orderId = orderLocationDto.getOrderId();
            if (ObjectUtil.isNotEmpty(id)) {
                QueryWrapper<OrderLocationEntity> queryWrapper = new QueryWrapper<OrderLocationEntity>()
                        .eq("order_id", orderId).last(" limit 1");
                OrderLocationEntity location = orderLocationService.getBaseMapper()
                        .selectOne(queryWrapper);
                if (location != null) {
                    OrderLocationEntity orderLocationUpdate = new OrderLocationEntity();
                    BeanUtil.copyProperties(orderLocationDto, orderLocationUpdate);
                    orderLocationUpdate.setId(location.getId());
                    orderLocationService.getBaseMapper().updateById(orderLocationUpdate);
                    BeanUtil.copyProperties(orderLocationUpdate, orderLocationDto);
                }
            } else {
                OrderLocationEntity orderLocation = new OrderLocationEntity();
                BeanUtil.copyProperties(orderLocationDto, orderLocation);
                orderLocationService.save(orderLocation);
                BeanUtil.copyProperties(orderLocation, orderLocationDto);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return orderLocationDto;
    }

    @GetMapping("orderId")
    public OrderLocationDto selectByOrderId(@RequestParam(name = "orderId") Long orderId) {
        OrderLocationDto result = new OrderLocationDto();
        QueryWrapper<OrderLocationEntity> queryWrapper = new QueryWrapper<OrderLocationEntity>()
                .eq("order_id", orderId).last(" limit 1");
        OrderLocationEntity location = orderLocationService.getOne(queryWrapper);
        if (location != null) {
            BeanUtil.copyProperties(location, result);
        }
        return result;
    }

    @PostMapping("del")
    public int deleteOrderLocation(@RequestBody OrderLocationDto orderLocationDto) {
        Long orderId = orderLocationDto.getOrderId();
        if (ObjectUtil.isNotEmpty(orderId)) {
            UpdateWrapper<OrderLocationEntity> updateWrapper = new UpdateWrapper<OrderLocationEntity>()
                    .eq("order_id", orderLocationDto);
            return orderLocationService.getBaseMapper().delete(updateWrapper);
        }
        return 0;
    }
}
