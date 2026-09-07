package com.gdcp.lab.report.service;

import com.gdcp.lab.report.entity.StatDailyRepair;
import com.gdcp.lab.report.mapper.StatDailyRepairMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class StatServiceTest {
    private StatDailyRepairMapper repairMapper;
    private StatService service;

    @BeforeEach
    void setUp() {
        repairMapper = Mockito.mock(StatDailyRepairMapper.class);
        service = new StatService(null, repairMapper);
    }

    @Test
    void repairCompleted_incrementsTodayCount() {
        LocalDate today = LocalDate.of(2026, 9, 7);
        Mockito.when(repairMapper.findByDate(today)).thenReturn(null);
        service.onRepairCompleted(today);
        Mockito.verify(repairMapper).insert(Mockito.any(StatDailyRepair.class));
    }
}
