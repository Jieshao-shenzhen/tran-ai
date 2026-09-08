package com.gdcp.lab.business.service;

import com.gdcp.lab.business.entity.RepairOrder;
import com.gdcp.lab.business.mapper.RepairOrderMapper;
import com.gdcp.lab.common.exception.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.junit.jupiter.api.Assertions.*;

class RepairServiceTest {
    private RepairOrderMapper mapper;
    private RepairService service;

    @BeforeEach
    void setUp() {
        mapper = Mockito.mock(RepairOrderMapper.class);
        service = new RepairService(mapper, Mockito.mock(RabbitTemplate.class));
    }

    private RepairOrder order(String status) {
        RepairOrder o = new RepairOrder();
        o.setId(1L); o.setDeviceId(2L); o.setStatus(status);
        return o;
    }

    @Test
    void approve_pending_byDirector_becomesDirectorApproved() {
        RepairOrder o = order("PENDING");
        Mockito.when(mapper.selectById(1L)).thenReturn(o);
        service.approve(1L, 9L, "DIRECTOR");
        assertEquals("DIRECTOR_APPROVED", o.getStatus());
        assertEquals(9L, o.getApproverId());
        Mockito.verify(mapper).updateById(o);
    }

    @Test
    void approve_directorApproved_byDean_becomesApproved() {
        RepairOrder o = order("DIRECTOR_APPROVED");
        Mockito.when(mapper.selectById(1L)).thenReturn(o);
        service.approve(1L, 10L, "DEAN");
        assertEquals("APPROVED", o.getStatus());
        assertEquals(10L, o.getSecondApproverId());
        Mockito.verify(mapper).updateById(o);
    }

    @Test
    void approve_pending_byTeacher_throws() {
        RepairOrder o = order("PENDING");
        Mockito.when(mapper.selectById(1L)).thenReturn(o);
        assertThrows(BizException.class, () -> service.approve(1L, 8L, "TEACHER"));
    }

    @Test
    void reject_pending_byDirector_setsRejected() {
        RepairOrder o = order("PENDING");
        Mockito.when(mapper.selectById(1L)).thenReturn(o);
        service.reject(1L, 9L, "DIRECTOR", "无需维修");
        assertEquals("REJECTED", o.getStatus());
        assertEquals("无需维修", o.getResult());
        assertEquals(9L, o.getApproverId());
    }

    @Test
    void reject_directorApproved_byDean_setsRejected() {
        RepairOrder o = order("DIRECTOR_APPROVED");
        Mockito.when(mapper.selectById(1L)).thenReturn(o);
        service.reject(1L, 10L, "DEAN", "预算不足");
        assertEquals("REJECTED", o.getStatus());
        assertEquals(10L, o.getSecondApproverId());
    }

    @Test
    void assign_requiresApproved() {
        RepairOrder o = order("APPROVED");
        Mockito.when(mapper.selectById(1L)).thenReturn(o);
        service.assign(1L, 7L);
        assertEquals("ASSIGNED", o.getStatus());
        assertEquals(7L, o.getAssigneeId());
        Mockito.verify(mapper).updateById(o);
    }

    @Test
    void assign_fromPending_throws() {
        RepairOrder o = order("PENDING");
        Mockito.when(mapper.selectById(1L)).thenReturn(o);
        assertThrows(BizException.class, () -> service.assign(1L, 7L));
    }

    @Test
    void finish_requiresAssigned() {
        RepairOrder o = order("PENDING");
        Mockito.when(mapper.selectById(1L)).thenReturn(o);
        assertThrows(BizException.class, () -> service.finish(1L, "已更换主板"));
    }

    @Test
    void verify_completedToVerified() {
        RepairOrder o = order("COMPLETED");
        Mockito.when(mapper.selectById(1L)).thenReturn(o);
        service.verify(1L);
        assertEquals("VERIFIED", o.getStatus());
        Mockito.verify(mapper).updateById(o);
    }
}
