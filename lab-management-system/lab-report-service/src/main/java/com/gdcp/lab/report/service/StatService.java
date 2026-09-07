package com.gdcp.lab.report.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gdcp.lab.report.entity.StatDailyRepair;
import com.gdcp.lab.report.entity.StatDailyRoomUsage;
import com.gdcp.lab.report.mapper.StatDailyRepairMapper;
import com.gdcp.lab.report.mapper.StatDailyRoomUsageMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class StatService {
    private final StatDailyRoomUsageMapper usageMapper;
    private final StatDailyRepairMapper repairMapper;

    public StatService(StatDailyRoomUsageMapper usageMapper, StatDailyRepairMapper repairMapper) {
        this.usageMapper = usageMapper;
        this.repairMapper = repairMapper;
    }

    public void onReservationApproved(LocalDate date, Long roomId) {
        StatDailyRoomUsage row = usageMapper.findByDateAndRoom(date, roomId);
        if (row == null) {
            row = new StatDailyRoomUsage();
            row.setStatDate(date); row.setRoomId(roomId);
            row.setApprovedCount(1); row.setUsageCount(0);
            usageMapper.insert(row);
        } else {
            row.setApprovedCount(row.getApprovedCount() + 1);
            usageMapper.updateById(row);
        }
    }

    public void onReservationCompleted(LocalDate date, Long roomId) {
        StatDailyRoomUsage row = usageMapper.findByDateAndRoom(date, roomId);
        if (row == null) {
            row = new StatDailyRoomUsage();
            row.setStatDate(date); row.setRoomId(roomId);
            row.setApprovedCount(0); row.setUsageCount(1);
            usageMapper.insert(row);
        } else {
            row.setUsageCount(row.getUsageCount() + 1);
            usageMapper.updateById(row);
        }
    }

    public void onRepairCompleted(LocalDate date) {
        StatDailyRepair row = repairMapper.findByDate(date);
        if (row == null) {
            row = new StatDailyRepair();
            row.setStatDate(date); row.setCompletedCount(1); row.setNewCount(0);
            repairMapper.insert(row);
        } else {
            row.setCompletedCount(row.getCompletedCount() + 1);
            repairMapper.updateById(row);
        }
    }

    public Map<String, Object> overview() {
        List<StatDailyRoomUsage> usage = usageMapper.selectList(null);
        List<StatDailyRepair> repairs = repairMapper.selectList(null);
        int approved = usage.stream().mapToInt(StatDailyRoomUsage::getApprovedCount).sum();
        int used = usage.stream().mapToInt(StatDailyRoomUsage::getUsageCount).sum();
        int completed = repairs.stream().mapToInt(StatDailyRepair::getCompletedCount).sum();
        Map<String, Object> m = new HashMap<>();
        m.put("totalApproved", approved);
        m.put("totalUsed", used);
        m.put("repairCompleted", completed);
        return m;
    }

    public List<StatDailyRoomUsage> roomUsage(LocalDate from, LocalDate to) {
        return usageMapper.selectList(new LambdaQueryWrapper<StatDailyRoomUsage>()
                .between(StatDailyRoomUsage::getStatDate, from, to)
                .orderByAsc(StatDailyRoomUsage::getStatDate));
    }
}