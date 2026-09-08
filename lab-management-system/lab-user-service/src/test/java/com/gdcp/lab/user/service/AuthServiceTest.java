package com.gdcp.lab.user.service;

import com.gdcp.lab.common.exception.BizException;
import com.gdcp.lab.user.entity.SysUser;
import com.gdcp.lab.user.mapper.SysUserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;

class AuthServiceTest {
    private SysUserMapper mapper;
    private OperationLogService logService;
    private AuthService service;

    @BeforeEach
    void setUp() {
        mapper = Mockito.mock(SysUserMapper.class);
        logService = Mockito.mock(OperationLogService.class);
        service = new AuthService(mapper,
                new BCryptPasswordEncoder(),
                "lab-secret-key-0123456789abcdef0123456789abcdef",
                logService);
    }

    @Test
    void login_success_returnsToken() {
        SysUser u = new SysUser();
        u.setId(1L); u.setUsername("admin");
        u.setPassword(new BCryptPasswordEncoder().encode("123456"));
        u.setRole("SYSTEM_ADMIN"); u.setStatus(1);
        Mockito.when(mapper.findByUsername("admin")).thenReturn(Optional.of(u));
        String token = service.login("admin", "123456");
        assertNotNull(token);
        assertEquals(3, token.split("\\.").length);
        Mockito.verify(logService).record(1L, "admin", "登录系统", null, "127.0.0.1");
    }

    @Test
    void login_success_recordsLogWithIp() {
        SysUser u = new SysUser();
        u.setId(1L); u.setUsername("admin");
        u.setPassword(new BCryptPasswordEncoder().encode("123456"));
        u.setRole("SYSTEM_ADMIN"); u.setStatus(1);
        Mockito.when(mapper.findByUsername("admin")).thenReturn(Optional.of(u));
        service.login("admin", "123456", "192.168.1.10");
        Mockito.verify(logService).record(1L, "admin", "登录系统", null, "192.168.1.10");
    }

    @Test
    void login_wrongPassword_throws() {
        SysUser u = new SysUser();
        u.setUsername("admin");
        u.setPassword(new BCryptPasswordEncoder().encode("123456"));
        u.setRole("SYSTEM_ADMIN"); u.setStatus(1);
        Mockito.when(mapper.findByUsername("admin")).thenReturn(Optional.of(u));
        assertThrows(BizException.class, () -> service.login("admin", "wrong"));
        Mockito.verify(logService).record(isNull(), any(), eq("登录失败"), any(), any());
    }

    @Test
    void login_disabledUser_throws() {
        SysUser u = new SysUser();
        u.setUsername("stu"); u.setPassword("x"); u.setStatus(0);
        Mockito.when(mapper.findByUsername("stu")).thenReturn(Optional.of(u));
        assertThrows(BizException.class, () -> service.login("stu", "123456"));
    }
}