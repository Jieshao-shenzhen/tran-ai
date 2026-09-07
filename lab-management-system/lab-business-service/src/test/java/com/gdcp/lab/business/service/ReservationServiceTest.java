package com.gdcp.lab.business.service;

import com.gdcp.lab.business.entity.Reservation;
import com.gdcp.lab.business.mapper.ReservationMapper;
import com.gdcp.lab.common.exception.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ReservationServiceTest {
    private ReservationMapper mapper;
    private ReservationService service;

    @BeforeEach
    void setUp() {
        mapper = Mockito.mock(ReservationMapper.class);
        service = new ReservationService(mapper, Mockito.mock(RabbitTemplate.class));
    }

    private Reservation res(long id, String st, String et, String status) {
        Reservation r = new Reservation();
        r.setId(id); r.setRoomId(1L); r.setStatus(status);
        r.setStartTime(LocalDateTime.parse(st));
        r.setEndTime(LocalDateTime.parse(et));
        return r;
    }

    @Test
    void create_overlappingTime_throws() {
        Mockito.when(mapper.findActiveByRoom(1L)).thenReturn(List.of(
                res(1L, "2026-09-08T09:00:00", "2026-09-08T11:00:00", "APPROVED")));
        Reservation newRes = res(0L, "2026-09-08T10:00:00", "2026-09-08T12:00:00", "PENDING");
        assertThrows(BizException.class, () -> service.create(newRes, 5L));
    }

    @Test
    void create_noConflict_saves() {
        Mockito.when(mapper.findActiveByRoom(1L)).thenReturn(List.of());
        Reservation newRes = res(0L, "2026-09-08T13:00:00", "2026-09-08T14:00:00", "PENDING");
        service.create(newRes, 5L);
        Mockito.verify(mapper).insert(newRes);
    }

    @Test
    void approve_validTransition_changesStatus() {
        Reservation r = res(1L, "2026-09-08T09:00:00", "2026-09-08T10:00:00", "PENDING");
        Mockito.when(mapper.selectById(1L)).thenReturn(r);
        service.approve(1L, 9L);
        assertEquals("APPROVED", r.getStatus());
        assertEquals(9L, r.getApproverId());
        Mockito.verify(mapper).updateById(r);
    }

    @Test
    void approve_alreadyApproved_throws() {
        Reservation r = res(1L, "2026-09-08T09:00:00", "2026-09-08T10:00:00", "APPROVED");
        Mockito.when(mapper.selectById(1L)).thenReturn(r);
        assertThrows(BizException.class, () -> service.approve(1L, 9L));
    }
}
