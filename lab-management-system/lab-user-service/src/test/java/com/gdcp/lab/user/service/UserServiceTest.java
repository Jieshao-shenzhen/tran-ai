package com.gdcp.lab.user.service;

import com.gdcp.lab.common.exception.BizException;
import com.gdcp.lab.user.entity.SysUser;
import com.gdcp.lab.user.excel.UserImportListener;
import com.gdcp.lab.user.excel.UserImportRow;
import com.gdcp.lab.user.mapper.SysUserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;

class UserServiceTest {
    private SysUserMapper mapper;
    private UserService service;

    @BeforeEach
    void setUp() {
        mapper = Mockito.mock(SysUserMapper.class);
        service = new UserService(mapper, new BCryptPasswordEncoder());
    }

    @Test
    void createUser_duplicateUsername_throws() {
        Mockito.when(mapper.findByUsername("stu1")).thenReturn(Optional.of(new SysUser()));
        assertThrows(BizException.class, () -> service.create("stu1", "张三", "STUDENT"));
    }

    @Test
    void importRows_insertsEachUser() {
        Mockito.when(mapper.findByUsername(any())).thenReturn(Optional.empty());
        UserImportListener listener = new UserImportListener(mapper, new BCryptPasswordEncoder());
        listener.getRows().addAll(List.of(
            new UserImportRow("stu1001", "李四", "STUDENT"),
            new UserImportRow("tea2001", "王五", "TEACHER")
        ));
        listener.saveData();
        Mockito.verify(mapper, Mockito.times(2)).insert(any(SysUser.class));
    }

    @Test
    void toggleStatus_freeze_sets0() {
        SysUser u = new SysUser();
        u.setId(1L); u.setStatus(1);
        Mockito.when(mapper.selectById(1L)).thenReturn(u);
        service.toggleStatus(1L, 0);
        assertEquals(0, u.getStatus());
        Mockito.verify(mapper).updateById(u);
    }

    @Test
    void toggleStatus_userNotFound_throws() {
        Mockito.when(mapper.selectById(99L)).thenReturn(null);
        assertThrows(BizException.class, () -> service.toggleStatus(99L, 0));
    }

    @Test
    void deleteUser_success() {
        SysUser u = new SysUser();
        u.setId(2L); u.setRole("TEACHER");
        Mockito.when(mapper.selectById(2L)).thenReturn(u);
        service.deleteUser(2L, 1L, "SYSTEM_ADMIN");
        Mockito.verify(mapper).deleteById(2L);
    }

    @Test
    void deleteUser_notFound_throws() {
        Mockito.when(mapper.selectById(99L)).thenReturn(null);
        assertThrows(BizException.class, () -> service.deleteUser(99L, 1L, "SYSTEM_ADMIN"));
    }

    @Test
    void deleteUser_self_throws() {
        SysUser u = new SysUser();
        u.setId(1L); u.setRole("TEACHER");
        Mockito.when(mapper.selectById(1L)).thenReturn(u);
        assertThrows(BizException.class, () -> service.deleteUser(1L, 1L, "SYSTEM_ADMIN"));
    }

    @Test
    void deleteUser_systemAdmin_throws() {
        SysUser u = new SysUser();
        u.setId(2L); u.setRole("SYSTEM_ADMIN");
        Mockito.when(mapper.selectById(2L)).thenReturn(u);
        assertThrows(BizException.class, () -> service.deleteUser(2L, 1L, "SYSTEM_ADMIN"));
    }
}