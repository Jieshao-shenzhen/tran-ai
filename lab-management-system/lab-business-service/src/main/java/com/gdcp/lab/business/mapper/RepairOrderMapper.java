package com.gdcp.lab.business.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gdcp.lab.business.entity.RepairOrder;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface RepairOrderMapper extends BaseMapper<RepairOrder> {
    @Select("SELECT * FROM repair_order WHERE status <> 'VERIFIED' ORDER BY id DESC")
    List<RepairOrder> findUnverified();
}
