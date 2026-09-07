package com.gdcp.lab.resource.service;

import com.gdcp.lab.common.exception.BizException;
import com.gdcp.lab.resource.entity.Material;
import com.gdcp.lab.resource.mapper.MaterialMapper;
import com.gdcp.lab.resource.mapper.MaterialRecordMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;

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
}
