package com.sl.ms.oms.controller;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.sl.ms.oms.dto.MailingSaveDTO;
import com.sl.ms.oms.dto.OrderDTO;
import com.sl.ms.oms.dto.OrderLocationDTO;
import com.sl.ms.oms.dto.OrderSearchDTO;
import com.sl.ms.oms.entity.OrderEntity;
import com.sl.ms.oms.entity.OrderLocationEntity;
import com.sl.ms.oms.service.CrudOrderService;
import com.sl.ms.oms.service.OrderLocationService;
import com.sl.ms.oms.service.OrderService;
import com.sl.transport.common.util.PageResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
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

    @Resource
    private CrudOrderService crudOrderService;

    /**
     * 下单
     *
     * @param mailingSaveDTO 订单信息
     * @return 订单信息
     */
    @PostMapping
    public OrderDTO mailingSave(@RequestBody MailingSaveDTO mailingSaveDTO) throws Exception {
        log.info("保存订单信息:{}", JSONUtil.toJsonStr(mailingSaveDTO));
        OrderEntity order = orderService.mailingSave(mailingSaveDTO);
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
        IPage<OrderEntity> orderIPage = crudOrderService.findByPage(orderDTO.getPage(), orderDTO.getPageSize(), orderEntity);
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
        IPage<OrderEntity> orderIPage = crudOrderService.pageLikeForCustomer(orderSearchDTO);
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
    public List<OrderDTO> list(@RequestBody OrderSearchDTO orderSearchDTO) {
        LambdaQueryWrapper<OrderEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(orderSearchDTO.getStatus() != null, OrderEntity::getStatus, orderSearchDTO.getStatus());
        wrapper.in(CollUtil.isNotEmpty(orderSearchDTO.getReceiverCountyIds()), OrderEntity::getReceiverCountyId, orderSearchDTO.getReceiverCountyIds());
        wrapper.in(CollUtil.isNotEmpty(orderSearchDTO.getSenderCountyIds()), OrderEntity::getSenderCountyId, orderSearchDTO.getSenderCountyIds());
        wrapper.eq(StrUtil.isNotEmpty(orderSearchDTO.getCurrentAgencyId()), OrderEntity::getCurrentAgencyId, orderSearchDTO.getCurrentAgencyId());
        return orderService.list(wrapper).stream().map(order -> BeanUtil.toBean(order, OrderDTO.class)).collect(Collectors.toList());
    }

    @ResponseBody
    @PostMapping("location/saveOrUpdate")
    public OrderLocationDTO saveOrUpdateLocation(@RequestBody OrderLocationDTO orderLocationDto) {
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

    @GetMapping("location/{orderId}")
    public OrderLocationDTO findOrderLocationByOrderId(@PathVariable(name = "orderId") Long orderId) {
        OrderLocationDTO result = new OrderLocationDTO();
        QueryWrapper<OrderLocationEntity> queryWrapper = new QueryWrapper<OrderLocationEntity>()
                .eq("order_id", orderId).last(" limit 1");
        OrderLocationEntity location = orderLocationService.getOne(queryWrapper);
        if (location != null) {
            BeanUtil.copyProperties(location, result);
        }
        return result;
    }

    /**
     * 根据orderId列表获取订单location信息
     * @param orderIds
     * @return
     */
    @GetMapping("locations")
    public List<OrderLocationDTO> findOrderLocationByOrderIds(@RequestParam("orderIds") List<Long> orderIds) {
        QueryWrapper<OrderLocationEntity> queryWrapper = new QueryWrapper<OrderLocationEntity>()
                .in("order_id", orderIds);
        List<OrderLocationEntity> locationList = orderLocationService.list(queryWrapper);
        if (CollectionUtil.isNotEmpty(locationList)) {
            return locationList.stream().map(location -> {
                OrderLocationDTO locationDTO = new OrderLocationDTO();
                BeanUtil.copyProperties(location, locationDTO);
                return locationDTO;
            }).collect(Collectors.toList());
        }
        return new ArrayList<>();
    }

    @PostMapping("del")
    public int deleteOrderLocation(@RequestBody OrderLocationDTO orderLocationDto) {
        Long orderId = orderLocationDto.getOrderId();
        if (ObjectUtil.isNotEmpty(orderId)) {
            UpdateWrapper<OrderLocationEntity> updateWrapper = new UpdateWrapper<OrderLocationEntity>()
                    .eq("order_id", orderLocationDto);
            return orderLocationService.getBaseMapper().delete(updateWrapper);
        }
        return 0;
    }
}
