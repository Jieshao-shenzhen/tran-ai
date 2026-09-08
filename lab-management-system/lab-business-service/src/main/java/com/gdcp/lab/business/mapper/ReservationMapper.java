package com.gdcp.lab.business.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gdcp.lab.business.entity.Reservation;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface ReservationMapper extends BaseMapper<Reservation> {
    @Select("SELECT * FROM reservation WHERE room_id = #{roomId} AND status IN ('PENDING','DIRECTOR_APPROVED','APPROVED')")
    List<Reservation> findActiveByRoom(Long roomId);
}
