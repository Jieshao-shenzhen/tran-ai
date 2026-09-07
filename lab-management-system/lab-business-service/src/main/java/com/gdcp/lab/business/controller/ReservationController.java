package com.gdcp.lab.business.controller;

import com.gdcp.lab.business.entity.Reservation;
import com.gdcp.lab.business.service.ReservationService;
import com.gdcp.lab.common.result.Result;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/reservations")
public class ReservationController {
    private final ReservationService reservationService;

    public ReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @PostMapping
    public Result<Reservation> create(@RequestBody Reservation r,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return Result.ok(reservationService.create(r, userId == null ? 0L : userId));
    }

    @PostMapping("/{id}/approve")
    public Result<Void> approve(@PathVariable Long id,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        reservationService.approve(id, userId == null ? 0L : userId);
        return Result.ok(null);
    }

    @PostMapping("/{id}/reject")
    public Result<Void> reject(@PathVariable Long id, @RequestParam String reason,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        reservationService.reject(id, userId == null ? 0L : userId, reason);
        return Result.ok(null);
    }

    @DeleteMapping("/{id}")
    public Result<Void> cancel(@PathVariable Long id,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        reservationService.cancel(id, userId == null ? 0L : userId);
        return Result.ok(null);
    }

    @PostMapping("/{id}/complete")
    public Result<Void> complete(@PathVariable Long id) {
        reservationService.complete(id);
        return Result.ok(null);
    }

    @GetMapping
    public Result<List<Reservation>> list(@RequestParam(required = false) Long roomId,
            @RequestParam(required = false) Long applicantId,
            @RequestParam(required = false) String status) {
        return Result.ok(reservationService.list(roomId, applicantId, status));
    }
}
