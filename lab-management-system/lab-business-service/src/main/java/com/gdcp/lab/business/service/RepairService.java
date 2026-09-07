package com.gdcp.lab.business.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gdcp.lab.business.config.RabbitConfig;
import com.gdcp.lab.business.entity.RepairOrder;
import com.gdcp.lab.business.mapper.RepairOrderMapper;
import com.gdcp.lab.common.exception.BizException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class RepairService {
    private final RepairOrderMapper orderMapper;
    private final RabbitTemplate rabbitTemplate;

    public RepairService(RepairOrderMapper orderMapper, RabbitTemplate rabbitTemplate) {
        this.orderMapper = orderMapper;
        this.rabbitTemplate = rabbitTemplate;
    }

    public RepairOrder create(RepairOrder o) {
        o.setStatus("PENDING");
        orderMapper.insert(o);
        return o;
    }

    public void assign(Long id, Long assigneeId) {
        RepairOrder o = must(id, "PENDING");
        o.setStatus("ASSIGNED");
        o.setAssigneeId(assigneeId);
        orderMapper.updateById(o);
    }

    public void reject(Long id, String reason) {
        RepairOrder o = must(id, "PENDING");
        o.setStatus("REJECTED");
        o.setResult(reason);
        orderMapper.updateById(o);
    }

    public void finish(Long id, String result) {
        RepairOrder o = must(id, "ASSIGNED");
        o.setStatus("COMPLETED");
        o.setResult(result);
        o.setCompletedAt(LocalDateTime.now());
        orderMapper.updateById(o);
        // 注意用 HashMap 避免 null value（orderId 在 insert 后已自增回填，但保险起见）
        Map<String, Object> msg = new HashMap<>();
        msg.put("orderId", o.getId());
        msg.put("deviceId", o.getDeviceId());
        rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE, "lab.stats.repair.completed", msg);
    }

    public void verify(Long id) {
        RepairOrder o = must(id, "COMPLETED");
        o.setStatus("VERIFIED");
        orderMapper.updateById(o);
    }

    public List<RepairOrder> list(Long deviceId, String status) {
        LambdaQueryWrapper<RepairOrder> qw = new LambdaQueryWrapper<>();
        if (deviceId != null) qw.eq(RepairOrder::getDeviceId, deviceId);
        if (status != null && !status.isBlank()) qw.eq(RepairOrder::getStatus, status);
        qw.orderByDesc(RepairOrder::getId);
        return orderMapper.selectList(qw);
    }

    private RepairOrder must(Long id, String expectedStatus) {
        RepairOrder o = orderMapper.selectById(id);
        if (o == null) throw new BizException("工单不存在");
        if (!expectedStatus.equals(o.getStatus())) throw new BizException("当前状态不可操作");
        return o;
    }
}
