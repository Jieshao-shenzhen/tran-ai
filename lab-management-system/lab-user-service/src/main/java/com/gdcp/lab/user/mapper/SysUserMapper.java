package com.gdcp.lab.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gdcp.lab.user.entity.SysUser;
import org.apache.ibatis.annotations.Select;

import java.util.Optional;

public interface SysUserMapper extends BaseMapper<SysUser> {
    @Select("SELECT * FROM sys_user WHERE username = #{username}")
    Optional<SysUser> findByUsername(String username);
}