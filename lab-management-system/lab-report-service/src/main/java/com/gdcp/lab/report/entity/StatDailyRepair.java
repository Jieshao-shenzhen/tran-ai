package com.gdcp.lab.report.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;

@Data
@TableName("stat_daily_repair")
public class StatDailyRepair {
    @TableId(type = IdType.AUTO)
    private Long id;
    private LocalDate statDate;
    private Integer newCount;
    private Integer completedCount;
}