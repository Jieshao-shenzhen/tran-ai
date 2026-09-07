package com.gdcp.lab.user.controller;

import com.alibaba.excel.EasyExcel;
import com.gdcp.lab.common.result.Result;
import com.gdcp.lab.user.entity.SysUser;
import com.gdcp.lab.user.excel.UserImportListener;
import com.gdcp.lab.user.excel.UserImportRow;
import com.gdcp.lab.user.mapper.SysUserMapper;
import com.gdcp.lab.user.service.UserService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {
    private final UserService userService;
    private final SysUserMapper userMapper;

    public UserController(UserService userService, SysUserMapper userMapper) {
        this.userService = userService;
        this.userMapper = userMapper;
    }

    @GetMapping
    public Result<List<SysUser>> list(@RequestParam(required = false) String role) {
        return Result.ok(userService.list(role));
    }

    @PostMapping
    public Result<SysUser> create(@RequestBody Map<String, String> body) {
        return Result.ok(userService.create(body.get("username"), body.get("realName"), body.get("role")));
    }

    @PostMapping("/import")
    public Result<String> importExcel(@RequestParam("file") MultipartFile file) throws IOException {
        UserImportListener listener = new UserImportListener(userMapper, new BCryptPasswordEncoder());
        EasyExcel.read(file.getInputStream(), UserImportRow.class, listener).sheet().doRead();
        return Result.ok("导入完成，共 " + listener.getRows().size() + " 条");
    }
}