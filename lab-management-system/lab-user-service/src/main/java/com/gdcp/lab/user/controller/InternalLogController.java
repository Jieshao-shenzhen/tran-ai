package com.gdcp.lab.user.controller;

import com.gdcp.lab.common.result.Result;
import com.gdcp.lab.user.service.OperationLogService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/internal")
public class InternalLogController {
    private final OperationLogService operationLogService;

    public InternalLogController(OperationLogService operationLogService) {
        this.operationLogService = operationLogService;
    }

    @PostMapping("/oplog")
    public Result<Void> record(@RequestBody Map<String, Object> body) {
        Long userId = body.get("userId") == null ? null : Long.valueOf(body.get("userId").toString());
        String ip = (String) body.get("ip");
        String action = (String) body.get("action");
        operationLogService.record(userId, null, action, null, ip);
        return Result.ok(null);
    }
}