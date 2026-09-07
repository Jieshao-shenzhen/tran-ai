package com.gdcp.lab.report.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gdcp.lab.report.entity.StatDailyRepair;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;

public interface StatDailyRepairMapper extends BaseMapper<StatDailyRepair> {
    @Select("SELECT * FROM stat_daily_repair WHERE stat_date = #{date}")
    StatDailyRepair findByDate(LocalDate date);
}