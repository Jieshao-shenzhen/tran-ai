package com.gdcp.lab.resource.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("lab_room")
public class LabRoom {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String code;
    private String name;
    private String building;
    private Integer floor;
    private Integer capacity;
    private String type;
    private Long managerId;
    private Integer status;
    private String remark;
}
