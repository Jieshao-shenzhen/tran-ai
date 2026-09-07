package com.gdcp.lab.user.excel;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class UserImportRow {
    @ExcelProperty("用户名") private String username;
    @ExcelProperty("姓名") private String realName;
    @ExcelProperty("角色") private String role;
}