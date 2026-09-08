package com.gdcp.lab.user.controller;

import com.gdcp.lab.common.exception.BizException;
import com.gdcp.lab.common.result.Result;
import com.gdcp.lab.user.entity.SysUser;
import com.gdcp.lab.user.mapper.SysUserMapper;
import com.gdcp.lab.user.service.AuthService;
import javax.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService authService;
    private final SysUserMapper userMapper;

    public AuthController(AuthService authService, SysUserMapper userMapper) {
        this.authService = authService;
        this.userMapper = userMapper;
    }

    @PostMapping("/login")
    public Result<Map<String, String>> login(@RequestBody Map<String, String> body, HttpServletRequest request) {
        String token = authService.login(body.get("username"), body.get("password"), resolveIp(request));
        return Result.ok(Map.of("token", token));
    }

    private String resolveIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    @GetMapping("/me")
    public Result<Map<String, Object>> me(@RequestHeader("X-User-Id") Long userId) {
        SysUser u = userMapper.selectById(userId);
        if (u == null) throw new BizException(401, "用户不存在");
        return Result.ok(Map.of(
                "id", u.getId(),
                "username", u.getUsername(),
                "realName", u.getRealName(),
                "role", u.getRole()
        ));
    }
}