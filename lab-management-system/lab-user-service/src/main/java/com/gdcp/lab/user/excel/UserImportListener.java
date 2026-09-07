package com.gdcp.lab.user.excel;

import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import com.gdcp.lab.user.entity.SysUser;
import com.gdcp.lab.user.mapper.SysUserMapper;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.ArrayList;
import java.util.List;

public class UserImportListener extends AnalysisEventListener<UserImportRow> {
    private final SysUserMapper userMapper;
    private final BCryptPasswordEncoder encoder;
    private final List<UserImportRow> rows = new ArrayList<>();

    public UserImportListener(SysUserMapper userMapper, BCryptPasswordEncoder encoder) {
        this.userMapper = userMapper;
        this.encoder = encoder;
    }

    @Override
    public void invoke(UserImportRow row, AnalysisContext context) {
        rows.add(row);
    }

    @Override
    public void doAfterAllAnalysed(AnalysisContext context) {
        saveData();
    }

    public void saveData() {
        for (UserImportRow row : rows) {
            if (row.getUsername() == null || row.getUsername().isBlank()) continue;
            if (userMapper.findByUsername(row.getUsername()).isPresent()) continue;
            SysUser u = new SysUser();
            u.setUsername(row.getUsername());
            u.setRealName(row.getRealName());
            u.setRole(row.getRole());
            u.setPassword(encoder.encode("123456"));
            u.setStatus(1);
            userMapper.insert(u);
        }
    }

    public List<UserImportRow> getRows() { return rows; }
}