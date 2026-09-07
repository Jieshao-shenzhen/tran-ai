package com.gdcp.lab.user.config;

import com.gdcp.lab.user.entity.SysUser;
import com.gdcp.lab.user.mapper.SysUserMapper;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements CommandLineRunner {
    private final SysUserMapper userMapper;
    private final BCryptPasswordEncoder encoder;

    public DataInitializer(SysUserMapper userMapper, BCryptPasswordEncoder encoder) {
        this.userMapper = userMapper;
        this.encoder = encoder;
    }

    @Override
    public void run(String... args) {
        if (userMapper.findByUsername("admin").isEmpty()) {
            SysUser admin = new SysUser();
            admin.setUsername("admin");
            admin.setPassword(encoder.encode("123456"));
            admin.setRealName("系统管理员");
            admin.setRole("SYSTEM_ADMIN");
            admin.setStatus(1);
            userMapper.insert(admin);
        }
    }
}