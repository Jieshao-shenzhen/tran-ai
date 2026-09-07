package com.gdcp.lab.resource.controller;

import com.gdcp.lab.common.result.Result;
import com.gdcp.lab.resource.entity.LabDevice;
import com.gdcp.lab.resource.entity.LabRoom;
import com.gdcp.lab.resource.entity.Material;
import com.gdcp.lab.resource.service.ResourceService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class ResourceController {
    private final ResourceService resourceService;

    public ResourceController(ResourceService resourceService) {
        this.resourceService = resourceService;
    }

    @GetMapping("/rooms")
    public Result<List<LabRoom>> listRooms(@RequestParam(required = false) String keyword) {
        return Result.ok(resourceService.listRooms(keyword));
    }

    @PostMapping("/rooms")
    public Result<Void> saveRoom(@RequestBody LabRoom room) {
        resourceService.saveRoom(room);
        return Result.ok(null);
    }

    @GetMapping("/devices")
    public Result<List<LabDevice>> listDevices(@RequestParam(required = false) Long roomId) {
        return Result.ok(resourceService.listDevices(roomId));
    }

    @PostMapping("/devices")
    public Result<Void> saveDevice(@RequestBody LabDevice device) {
        resourceService.saveDevice(device);
        return Result.ok(null);
    }

    @GetMapping("/materials")
    public Result<List<Material>> listMaterials(@RequestParam(required = false) String keyword) {
        return Result.ok(resourceService.listMaterials(keyword));
    }

    @PostMapping("/materials/{id}/stock")
    public Result<Void> stock(@PathVariable Long id, @RequestParam String type,
                              @RequestParam int quantity, @RequestParam(required = false) Long operatorId) {
        resourceService.stock(id, type, quantity, operatorId);
        return Result.ok(null);
    }
}
