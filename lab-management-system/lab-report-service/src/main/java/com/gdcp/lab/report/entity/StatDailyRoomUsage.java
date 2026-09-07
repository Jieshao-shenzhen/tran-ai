package com.gdcp.lab.report.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;

@Data
@TableName("stat_daily_room_usage")
public class StatDailyRoomUsage {
    @TableId(type = IdType.AUTO)
    private Long id;
    private LocalDate statDate;
    private Long roomId;
    private Integer usageCount;
    private Integer approvedCount;
}