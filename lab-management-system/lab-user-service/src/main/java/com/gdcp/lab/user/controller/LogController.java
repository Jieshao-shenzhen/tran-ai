package com.gdcp.lab.user.controller;

import com.gdcp.lab.common.result.Result;
import com.gdcp.lab.user.entity.OperationLog;
import com.gdcp.lab.user.service.OperationLogService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/logs")
public class LogController {
    private final OperationLogService logService;

    public LogController(OperationLogService logService) {
        this.logService = logService;
    }

    @GetMapping
    public Result<List<OperationLog>> list() {
        return Result.ok(logService.list());
    }
}