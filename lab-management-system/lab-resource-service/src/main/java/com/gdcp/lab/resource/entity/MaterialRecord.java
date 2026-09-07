package com.gdcp.lab.resource.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("material_record")
public class MaterialRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long materialId;
    private String type;
    private Integer quantity;
    private Long operatorId;
    private LocalDateTime createdAt;
}
