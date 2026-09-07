package com.gdcp.lab.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gdcp.lab.common.exception.BizException;
import com.gdcp.lab.user.entity.SysUser;
import com.gdcp.lab.user.mapper.SysUserMapper;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserService {
    private final SysUserMapper userMapper;
    private final BCryptPasswordEncoder encoder;

    public UserService(SysUserMapper userMapper, BCryptPasswordEncoder encoder) {
        this.userMapper = userMapper;
        this.encoder = encoder;
    }

    public SysUser create(String username, String realName, String role) {
        if (userMapper.findByUsername(username).isPresent()) {
            throw new BizException("用户名已存在: " + username);
        }
        SysUser u = new SysUser();
        u.setUsername(username);
        u.setPassword(encoder.encode("123456"));
        u.setRealName(realName);
        u.setRole(role);
        u.setStatus(1);
        userMapper.insert(u);
        return u;
    }

    public List<SysUser> list(String role) {
        LambdaQueryWrapper<SysUser> qw = new LambdaQueryWrapper<>();
        if (role != null && !role.isBlank()) qw.eq(SysUser::getRole, role);
        return userMapper.selectList(qw);
    }

    public void toggleStatus(Long id, Integer status) {
        SysUser u = userMapper.selectById(id);
        if (u == null) throw new BizException("用户不存在");
        u.setStatus(status);
        userMapper.updateById(u);
    }
}