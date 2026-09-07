package com.gdcp.lab.report.listener;

import com.gdcp.lab.report.service.StatService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Map;

@Component
public class StatListener {
    private final StatService statService;

    public StatListener(StatService statService) { this.statService = statService; }

    @RabbitListener(queues = "lab.stats.queue")
    public void onStatsEvent(Map<String, Object> payload) {
        String routing = (String) payload.getOrDefault("_routingKey", "");
        Long roomId = payload.get("roomId") == null ? null : Long.valueOf(payload.get("roomId").toString());
        if (routing.endsWith(".reservation.approved")) {
            statService.onReservationApproved(LocalDate.now(), roomId);
        } else if (routing.endsWith(".reservation.completed")) {
            statService.onReservationCompleted(LocalDate.now(), roomId);
        } else if (routing.endsWith(".repair.completed")) {
            statService.onRepairCompleted(LocalDate.now());
        }
    }
}