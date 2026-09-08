package com.gdcp.lab.resource.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gdcp.lab.common.exception.BizException;
import com.gdcp.lab.resource.entity.LabDevice;
import com.gdcp.lab.resource.entity.LabRoom;
import com.gdcp.lab.resource.entity.Material;
import com.gdcp.lab.resource.entity.MaterialRecord;
import com.gdcp.lab.resource.mapper.LabDeviceMapper;
import com.gdcp.lab.resource.mapper.LabRoomMapper;
import com.gdcp.lab.resource.mapper.MaterialMapper;
import com.gdcp.lab.resource.mapper.MaterialRecordMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ResourceService {
    private final LabRoomMapper roomMapper;
    private final LabDeviceMapper deviceMapper;
    private final MaterialMapper materialMapper;
    private final MaterialRecordMapper recordMapper;

    public ResourceService(LabRoomMapper roomMapper, LabDeviceMapper deviceMapper,
                           MaterialMapper materialMapper, MaterialRecordMapper recordMapper) {
        this.roomMapper = roomMapper;
        this.deviceMapper = deviceMapper;
        this.materialMapper = materialMapper;
        this.recordMapper = recordMapper;
    }

    public List<LabRoom> listRooms(String keyword) {
        LambdaQueryWrapper<LabRoom> qw = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            qw.and(w -> w.like(LabRoom::getName, keyword).or().like(LabRoom::getCode, keyword));
        }
        return roomMapper.selectList(qw);
    }

    public List<LabDevice> listDevices(Long roomId) {
        LambdaQueryWrapper<LabDevice> qw = new LambdaQueryWrapper<>();
        if (roomId != null) qw.eq(LabDevice::getRoomId, roomId);
        return deviceMapper.selectList(qw);
    }

    public void saveRoom(LabRoom room) {
        if (room.getId() == null) { roomMapper.insert(room); } else { roomMapper.updateById(room); }
    }
    public void saveDevice(LabDevice device) {
        if (device.getId() == null) { deviceMapper.insert(device); } else { deviceMapper.updateById(device); }
    }

    public List<Material> listMaterials(String keyword) {
        LambdaQueryWrapper<Material> qw = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            qw.and(w -> w.like(Material::getName, keyword).or().like(Material::getCode, keyword));
        }
        return materialMapper.selectList(qw);
    }

    public Material createMaterial(Material m) {
        if (m.getCode() == null || m.getCode().isBlank()) throw new BizException("编码不能为空");
        if (m.getName() == null || m.getName().isBlank()) throw new BizException("名称不能为空");
        LambdaQueryWrapper<Material> qw = new LambdaQueryWrapper<>();
        qw.eq(Material::getCode, m.getCode());
        if (materialMapper.selectCount(qw) > 0) {
            throw new BizException("耗材编码已存在: " + m.getCode());
        }
        if (m.getStock() == null) m.setStock(0);
        if (m.getWarnThreshold() == null) m.setWarnThreshold(0);
        materialMapper.insert(m);
        return m;
    }

    public void deleteMaterial(Long id) {
        if (materialMapper.selectById(id) == null) {
            throw new BizException("耗材不存在");
        }
        LambdaQueryWrapper<MaterialRecord> qw = new LambdaQueryWrapper<>();
        qw.eq(MaterialRecord::getMaterialId, id);
        if (recordMapper.selectCount(qw) > 0) {
            throw new BizException("该耗材已有出入库记录，不可删除");
        }
        materialMapper.deleteById(id);
    }

    @Transactional
    public void stock(Long materialId, String type, int quantity, Long operatorId) {
        if (quantity <= 0) throw new BizException("数量必须大于0");
        Material m = materialMapper.selectById(materialId);
        if (m == null) throw new BizException("耗材不存在");
        if ("IN".equalsIgnoreCase(type)) {
            m.setStock(m.getStock() + quantity);
        } else if ("OUT".equalsIgnoreCase(type)) {
            if (m.getStock() < quantity) throw new BizException("库存不足");
            m.setStock(m.getStock() - quantity);
        } else {
            throw new BizException("无效的出入库类型");
        }
        materialMapper.updateById(m);
        MaterialRecord record = new MaterialRecord();
        record.setMaterialId(materialId);
        record.setType(type.toUpperCase());
        record.setQuantity(quantity);
        record.setOperatorId(operatorId);
        recordMapper.insert(record);
    }
}
