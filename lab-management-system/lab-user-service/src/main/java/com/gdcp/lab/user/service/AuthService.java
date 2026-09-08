package com.gdcp.lab.user.service;

import com.gdcp.lab.common.exception.BizException;
import com.gdcp.lab.common.jwt.JwtUtil;
import com.gdcp.lab.user.entity.SysUser;
import com.gdcp.lab.user.mapper.SysUserMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    private final SysUserMapper userMapper;
    private final BCryptPasswordEncoder encoder;
    private final JwtUtil jwtUtil;
    private final OperationLogService operationLogService;

    public AuthService(SysUserMapper userMapper, BCryptPasswordEncoder encoder,
                       @Value("${lab.jwt.secret}") String secret,
                       OperationLogService operationLogService) {
        this.userMapper = userMapper;
        this.encoder = encoder;
        this.jwtUtil = new JwtUtil(secret);
        this.operationLogService = operationLogService;
    }

    public String login(String username, String password) {
        return login(username, password, "127.0.0.1");
    }

    public String login(String username, String password, String ip) {
        SysUser u = userMapper.findByUsername(username)
                .orElseThrow(() -> {
                    operationLogService.record(null, username, "登录失败", "用户不存在", ip);
                    return new BizException(401, "用户名或密码错误");
                });
        if (u.getStatus() == null || u.getStatus() != 1) {
            operationLogService.record(u.getId(), u.getUsername(), "登录失败", "账号已被禁用", ip);
            throw new BizException(401, "账号已被禁用");
        }
        if (!encoder.matches(password, u.getPassword())) {
            operationLogService.record(u.getId(), u.getUsername(), "登录失败", "账号或密码错误", ip);
            throw new BizException(401, "用户名或密码错误");
        }
        operationLogService.record(u.getId(), u.getUsername(), "登录系统", null, ip);
        return jwtUtil.generate(String.valueOf(u.getId()), u.getRole());
    }
}