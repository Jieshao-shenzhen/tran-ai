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
import static org.mockito.Mockito.*;

class ReservationServiceTest {
    private ReservationMapper mapper;
    private RabbitTemplate rabbit;
    private ReservationService service;

    @BeforeEach
    void setUp() {
        mapper = Mockito.mock(ReservationMapper.class);
        rabbit = Mockito.mock(RabbitTemplate.class);
        service = new ReservationService(mapper, rabbit);
    }

    private Reservation res(String st, String et, String status) {
        Reservation r = new Reservation();
        r.setId(1L); r.setRoomId(1L); r.setStatus(status);
        r.setStartTime(LocalDateTime.parse(st));
        r.setEndTime(LocalDateTime.parse(et));
        return r;
    }

    @Test
    void create_overlappingTime_throws() {
        Mockito.when(mapper.findActiveByRoom(1L)).thenReturn(List.of(
                res("2026-09-08T09:00:00", "2026-09-08T11:00:00", "APPROVED")));
        Reservation newRes = res("2026-09-08T10:00:00", "2026-09-08T12:00:00", "PENDING");
        assertThrows(BizException.class, () -> service.create(newRes, 5L));
    }

    @Test
    void create_noConflict_saves() {
        Mockito.when(mapper.findActiveByRoom(1L)).thenReturn(List.of());
        Reservation newRes = res("2026-09-08T13:00:00", "2026-09-08T14:00:00", "PENDING");
        service.create(newRes, 5L);
        Mockito.verify(mapper).insert(newRes);
    }

    @Test
    void approve_shortDuration_byDirector_becomesApproved() {
        Reservation r = res("2026-09-08T09:00:00", "2026-09-08T17:00:00", "PENDING");
        Mockito.when(mapper.selectById(1L)).thenReturn(r);
        service.approve(1L, 9L, "DIRECTOR");
        assertEquals("APPROVED", r.getStatus());
        assertEquals(9L, r.getApproverId());
        verify(rabbit).convertAndSend(anyString(), anyString(), anyMap());
    }

    @Test
    void approve_longDuration_byDirector_becomesDirectorApproved() {
        Reservation r = res("2026-09-08T09:00:00", "2026-09-09T10:00:00", "PENDING"); // 25h
        Mockito.when(mapper.selectById(1L)).thenReturn(r);
        service.approve(1L, 9L, "DIRECTOR");
        assertEquals("DIRECTOR_APPROVED", r.getStatus());
        assertEquals(9L, r.getApproverId());
        verify(rabbit, never()).convertAndSend(anyString(), anyString(), anyMap());
    }

    @Test
    void approve_directorApproved_byDean_becomesApproved() {
        Reservation r = res("2026-09-08T09:00:00", "2026-09-09T10:00:00", "DIRECTOR_APPROVED");
        Mockito.when(mapper.selectById(1L)).thenReturn(r);
        service.approve(1L, 10L, "DEAN");
        assertEquals("APPROVED", r.getStatus());
        assertEquals(10L, r.getSecondApproverId());
        verify(rabbit).convertAndSend(anyString(), anyString(), anyMap());
    }

    @Test
    void approve_pending_byTeacher_throws() {
        Reservation r = res("2026-09-08T09:00:00", "2026-09-08T17:00:00", "PENDING");
        Mockito.when(mapper.selectById(1L)).thenReturn(r);
        assertThrows(BizException.class, () -> service.approve(1L, 8L, "TEACHER"));
    }

    @Test
    void approve_directorApproved_byDirector_throws() {
        Reservation r = res("2026-09-08T09:00:00", "2026-09-09T10:00:00", "DIRECTOR_APPROVED");
        Mockito.when(mapper.selectById(1L)).thenReturn(r);
        assertThrows(BizException.class, () -> service.approve(1L, 9L, "DIRECTOR"));
    }

    @Test
    void reject_pending_byDirector_setsRejected() {
        Reservation r = res("2026-09-08T09:00:00", "2026-09-08T17:00:00", "PENDING");
        Mockito.when(mapper.selectById(1L)).thenReturn(r);
        service.reject(1L, 9L, "DIRECTOR", "时间冲突");
        assertEquals("REJECTED", r.getStatus());
        assertEquals("时间冲突", r.getRejectReason());
        assertEquals(9L, r.getApproverId());
    }

    @Test
    void reject_directorApproved_byDean_setsRejected() {
        Reservation r = res("2026-09-08T09:00:00", "2026-09-09T10:00:00", "DIRECTOR_APPROVED");
        Mockito.when(mapper.selectById(1L)).thenReturn(r);
        service.reject(1L, 10L, "DEAN", "经费不足");
        assertEquals("REJECTED", r.getStatus());
        assertEquals(10L, r.getSecondApproverId());
    }

    @Test
    void approve_alreadyApproved_throws() {
        Reservation r = res("2026-09-08T09:00:00", "2026-09-08T10:00:00", "APPROVED");
        Mockito.when(mapper.selectById(1L)).thenReturn(r);
        assertThrows(BizException.class, () -> service.approve(1L, 9L, "DIRECTOR"));
    }
}
