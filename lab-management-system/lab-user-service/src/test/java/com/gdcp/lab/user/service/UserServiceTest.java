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
}