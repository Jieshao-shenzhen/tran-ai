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
    void assign_pendingToAssigned() {
        RepairOrder o = order("PENDING");
        Mockito.when(mapper.selectById(1L)).thenReturn(o);
        service.assign(1L, 7L);
        assertEquals("ASSIGNED", o.getStatus());
        assertEquals(7L, o.getAssigneeId());
        Mockito.verify(mapper).updateById(o);
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
