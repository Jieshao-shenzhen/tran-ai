package com.gdcp.lab.business.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("repair_order")
public class RepairOrder {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long deviceId;
    private Long roomId;
    private Long reporterId;
    private String description;
    private String status;
    private Long approverId;          // 一级审批人(主任)
    private Long secondApproverId;    // 二级审批人(院长/副院长)
    private Long assigneeId;
    private String result;
    private LocalDateTime completedAt;
}
