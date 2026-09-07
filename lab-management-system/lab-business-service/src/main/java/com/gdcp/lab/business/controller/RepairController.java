package com.gdcp.lab.business.controller;

import com.gdcp.lab.business.entity.RepairOrder;
import com.gdcp.lab.business.service.RepairService;
import com.gdcp.lab.common.result.Result;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/repairs")
public class RepairController {
    private final RepairService repairService;

    public RepairController(RepairService repairService) {
        this.repairService = repairService;
    }

    @PostMapping
    public Result<RepairOrder> create(@RequestBody RepairOrder o,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        o.setReporterId(userId == null ? 0L : userId);
        return Result.ok(repairService.create(o));
    }

    @PostMapping("/{id}/assign")
    public Result<Void> assign(@PathVariable Long id, @RequestParam Long assigneeId) {
        repairService.assign(id, assigneeId);
        return Result.ok(null);
    }

    @PostMapping("/{id}/reject")
    public Result<Void> reject(@PathVariable Long id, @RequestParam String reason) {
        repairService.reject(id, reason);
        return Result.ok(null);
    }

    @PostMapping("/{id}/finish")
    public Result<Void> finish(@PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body) {
        repairService.finish(id, body == null ? null : body.get("result"));
        return Result.ok(null);
    }

    @PostMapping("/{id}/verify")
    public Result<Void> verify(@PathVariable Long id) {
        repairService.verify(id);
        return Result.ok(null);
    }

    @GetMapping
    public Result<List<RepairOrder>> list(@RequestParam(required = false) Long deviceId,
            @RequestParam(required = false) String status) {
        return Result.ok(repairService.list(deviceId, status));
    }
}
