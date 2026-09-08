package com.gdcp.lab.business.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("reservation")
public class Reservation {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long roomId;
    private Long applicantId;
    private String purpose;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer peopleNum;
    private String status;
    private Long approverId;
    private Long secondApproverId;   // 二级审批人(院长/副院长)
    private String rejectReason;
    private LocalDateTime createdAt;
}
