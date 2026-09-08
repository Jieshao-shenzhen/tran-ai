package com.gdcp.lab.resource.service;

import com.gdcp.lab.common.exception.BizException;
import com.gdcp.lab.resource.entity.Material;
import com.gdcp.lab.resource.mapper.MaterialMapper;
import com.gdcp.lab.resource.mapper.MaterialRecordMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ResourceServiceTest {
    private MaterialMapper materialMapper;
    private ResourceService service;

    @BeforeEach
    void setUp() {
        materialMapper = Mockito.mock(MaterialMapper.class);
        service = new ResourceService(null, null, materialMapper, Mockito.mock(MaterialRecordMapper.class));
    }

    @Test
    void stockOut_insufficient_throws() {
        Material m = new Material();
        m.setId(1L); m.setStock(2);
        Mockito.when(materialMapper.selectById(1L)).thenReturn(m);
        assertThrows(BizException.class, () -> service.stock(1L, "OUT", 5, 9L));
    }

    @Test
    void stockIn_increasesStock() {
        Material m = new Material();
        m.setId(1L); m.setStock(2);
        Mockito.when(materialMapper.selectById(1L)).thenReturn(m);
        service.stock(1L, "IN", 3, 9L);
        assertEquals(5, m.getStock());
        Mockito.verify(materialMapper).updateById(m);
    }

    @Test
    void createMaterial_duplicateCode_throws() {
        Material m = new Material();
        m.setCode("M001");
        Mockito.when(materialMapper.selectCount(Mockito.any())).thenReturn(1L);
        assertThrows(BizException.class, () -> service.createMaterial(m));
    }

    @Test
    void createMaterial_success_inserts() {
        Material m = new Material();
        m.setCode("M002"); m.setName("鼠标"); m.setStock(10); m.setWarnThreshold(2);
        Mockito.when(materialMapper.selectCount(Mockito.any())).thenReturn(0L);
        service.createMaterial(m);
        assertEquals(10, m.getStock());
        Mockito.verify(materialMapper).insert(m);
    }

    @Test
    void createMaterial_nullStock_defaultsZero() {
        Material m = new Material();
        m.setCode("M003"); m.setName("键盘");
        Mockito.when(materialMapper.selectCount(Mockito.any())).thenReturn(0L);
        service.createMaterial(m);
        assertEquals(0, m.getStock());
        assertEquals(0, m.getWarnThreshold());
        Mockito.verify(materialMapper).insert(m);
    }

    @Test
    void deleteMaterial_withRecords_throws() {
        MaterialRecordMapper recordMapper = Mockito.mock(MaterialRecordMapper.class);
        ResourceService svc = new ResourceService(null, null, materialMapper, recordMapper);
        Material m = new Material();
        m.setId(1L);
        Mockito.when(materialMapper.selectById(1L)).thenReturn(m);
        Mockito.when(recordMapper.selectCount(Mockito.any())).thenReturn(3L);
        assertThrows(BizException.class, () -> svc.deleteMaterial(1L));
    }

    @Test
    void deleteMaterial_withoutRecords_deletes() {
        MaterialRecordMapper recordMapper = Mockito.mock(MaterialRecordMapper.class);
        ResourceService svc = new ResourceService(null, null, materialMapper, recordMapper);
        Material m = new Material();
        m.setId(1L);
        Mockito.when(materialMapper.selectById(1L)).thenReturn(m);
        Mockito.when(recordMapper.selectCount(Mockito.any())).thenReturn(0L);
        svc.deleteMaterial(1L);
        Mockito.verify(materialMapper).deleteById(1L);
    }
}
