package com.gdcp.lab.report.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gdcp.lab.report.entity.StatDailyRoomUsage;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;

public interface StatDailyRoomUsageMapper extends BaseMapper<StatDailyRoomUsage> {
    @Select("SELECT * FROM stat_daily_room_usage WHERE stat_date = #{date} AND room_id = #{roomId}")
    StatDailyRoomUsage findByDateAndRoom(LocalDate date, Long roomId);
}