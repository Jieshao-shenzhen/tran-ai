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

    public AuthService(SysUserMapper userMapper, BCryptPasswordEncoder encoder,
                       @Value("${lab.jwt.secret}") String secret) {
        this.userMapper = userMapper;
        this.encoder = encoder;
        this.jwtUtil = new JwtUtil(secret);
    }

    public String login(String username, String password) {
        SysUser u = userMapper.findByUsername(username)
                .orElseThrow(() -> new BizException(401, "用户名或密码错误"));
        if (u.getStatus() == null || u.getStatus() != 1) {
            throw new BizException(401, "账号已被禁用");
        }
        if (!encoder.matches(password, u.getPassword())) {
            throw new BizException(401, "用户名或密码错误");
        }
        return jwtUtil.generate(String.valueOf(u.getId()), u.getRole());
    }
}