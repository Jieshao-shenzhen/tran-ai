package com.gdcp.lab.resource.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@TableName("lab_device")
public class LabDevice {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String code;
    private String name;
    private String category;
    private Long roomId;
    private String brand;
    private String modelNo;
    private Integer status;
    private LocalDate buyDate;
    private BigDecimal price;
}
