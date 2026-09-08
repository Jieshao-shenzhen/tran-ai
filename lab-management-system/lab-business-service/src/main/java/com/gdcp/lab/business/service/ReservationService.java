package com.gdcp.lab.business.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gdcp.lab.business.common.ApprovalRoleUtil;
import com.gdcp.lab.business.config.RabbitConfig;
import com.gdcp.lab.business.entity.Reservation;
import com.gdcp.lab.business.mapper.ReservationMapper;
import com.gdcp.lab.common.exception.BizException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ReservationService {
    private final ReservationMapper reservationMapper;
    private final RabbitTemplate rabbitTemplate;

    public ReservationService(ReservationMapper reservationMapper, RabbitTemplate rabbitTemplate) {
        this.reservationMapper = reservationMapper;
        this.rabbitTemplate = rabbitTemplate;
    }

    public Reservation create(Reservation r, Long applicantId) {
        if (r.getStartTime() == null || r.getEndTime() == null
                || !r.getStartTime().isBefore(r.getEndTime())) {
            throw new BizException("预约时间段不合法");
        }
        if (hasConflict(r)) {
            throw new BizException("该实训室在此时间段已被预约");
        }
        r.setApplicantId(applicantId);
        r.setStatus("PENDING");
        reservationMapper.insert(r);
        publish("lab.notify.reservation.created", r, "APPROVED");
        return r;
    }

    public boolean hasConflict(Reservation r) {
        List<Reservation> active = reservationMapper.findActiveByRoom(r.getRoomId());
        for (Reservation a : active) {
            if (r.getStartTime().isBefore(a.getEndTime()) && r.getEndTime().isAfter(a.getStartTime())) {
                return true;
            }
        }
        return false;
    }

    public void approve(Long id, Long approverId, String approverRole) {
        Reservation r = reservationMapper.selectById(id);
        if (r == null) {
            throw new BizException("预约不存在");
        }
        if ("PENDING".equals(r.getStatus())) {
            if (!ApprovalRoleUtil.canFirstApprove(approverRole)) {
                throw new BizException("无权限审批");
            }
            r.setApproverId(approverId);
            if (Duration.between(r.getStartTime(), r.getEndTime()).toHours() >= 24) {
                r.setStatus("DIRECTOR_APPROVED");
                reservationMapper.updateById(r);
            } else {
                r.setStatus("APPROVED");
                reservationMapper.updateById(r);
                publish("lab.stats.reservation.approved", r, "APPROVED");
            }
        } else if ("DIRECTOR_APPROVED".equals(r.getStatus())) {
            if (!ApprovalRoleUtil.canSecondApprove(approverRole)) {
                throw new BizException("无权限审批");
            }
            r.setSecondApproverId(approverId);
            r.setStatus("APPROVED");
            reservationMapper.updateById(r);
            publish("lab.stats.reservation.approved", r, "APPROVED");
        } else {
            throw new BizException("当前状态不可审批");
        }
    }

    public void reject(Long id, Long approverId, String approverRole, String reason) {
        Reservation r = reservationMapper.selectById(id);
        if (r == null) {
            throw new BizException("预约不存在");
        }
        if ("PENDING".equals(r.getStatus())) {
            if (!ApprovalRoleUtil.canFirstApprove(approverRole)) {
                throw new BizException("无权限审批");
            }
            r.setApproverId(approverId);
        } else if ("DIRECTOR_APPROVED".equals(r.getStatus())) {
            if (!ApprovalRoleUtil.canSecondApprove(approverRole)) {
                throw new BizException("无权限审批");
            }
            r.setSecondApproverId(approverId);
        } else {
            throw new BizException("当前状态不可审批");
        }
        r.setStatus("REJECTED");
        r.setRejectReason(reason);
        reservationMapper.updateById(r);
    }

    public void cancel(Long id, Long operatorId) {
        Reservation r = reservationMapper.selectById(id);
        if (r == null) {
            throw new BizException("预约不存在");
        }
        if (!"PENDING".equals(r.getStatus())) {
            throw new BizException("当前状态不可取消");
        }
        r.setStatus("CANCELLED");
        reservationMapper.updateById(r);
    }

    public void complete(Long id) {
        Reservation r = reservationMapper.selectById(id);
        if (r == null || !"APPROVED".equals(r.getStatus())) {
            throw new BizException("状态非法");
        }
        r.setStatus("COMPLETED");
        reservationMapper.updateById(r);
        publish("lab.stats.reservation.completed", r, "COMPLETED");
    }

    public List<Reservation> list(Long roomId, Long applicantId, String status) {
        LambdaQueryWrapper<Reservation> qw = new LambdaQueryWrapper<>();
        qw.eq(roomId != null, Reservation::getRoomId, roomId)
          .eq(applicantId != null, Reservation::getApplicantId, applicantId)
          .eq(status != null && !status.isEmpty(), Reservation::getStatus, status)
          .orderByDesc(Reservation::getCreatedAt);
        return reservationMapper.selectList(qw);
    }

    private void publish(String routingKey, Reservation r, String status) {
        Map<String, Object> msg = new HashMap<>();
        msg.put("_routingKey", routingKey);
        msg.put("reservationId", r.getId());
        msg.put("roomId", r.getRoomId());
        msg.put("status", status);
        msg.put("applicantId", r.getApplicantId());
        rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE, routingKey, msg);
    }
}
