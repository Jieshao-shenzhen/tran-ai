package com.gdcp.lab.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gdcp.lab.user.entity.OperationLog;
import com.gdcp.lab.user.mapper.OperationLogMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class OperationLogService {
    private final OperationLogMapper logMapper;

    public OperationLogService(OperationLogMapper logMapper) { this.logMapper = logMapper; }

    public void record(Long userId, String username, String action, String detail) {
        OperationLog log = new OperationLog();
        log.setUserId(userId); log.setUsername(username);
        log.setAction(action); log.setDetail(detail);
        logMapper.insert(log);
    }

    public List<OperationLog> list() {
        return logMapper.selectList(new LambdaQueryWrapper<OperationLog>()
                .orderByDesc(OperationLog::getCreatedAt).last("LIMIT 200"));
    }
}