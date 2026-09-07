# 广东交通职业技术学院 · 实训室管理系统 — 设计规格

- 日期：2026-09-07
- 状态：已获用户批准

## 1. 项目背景与目标

为广东交通职业技术学院建设一套实训室管理系统，用于统一管理学院实训室、设备、耗材资源，支撑学生/教师的实训室预约、设备的报修维修以及耗材流转，并为管理者提供数据统计。系统在学院真实环境使用。

## 2. 用户角色与权限

| 角色 | 主要职责 |
|---|---|
| 学生 | 浏览实训室/设备信息，提交实训室预约申请 |
| 教师 | 提交预约、报修，管理自己课程的实训安排 |
| 实训室管理员 | 审批预约、处理报修工单、维护设备与耗材台账 |
| 系统管理员 | 用户/角色/权限配置、操作日志、系统设置、查看全部统计 |

账号体系：系统内账号，管理员创建 + Excel 批量导入。

## 3. 功能模块范围

选定的 6 大模块：
1. 实训室/设备台账管理
2. 实训室预约/审批
3. 报修/维修管理
4. 耗材管理
5. 系统管理（用户/角色/权限/日志）
6. 数据统计报表

## 4. 总体架构

### 4.1 微服务拆分（完整微服务栈）

| 服务名 | 端口 | 职责 |
|---|---|---|
| `lab-gateway` | 9000 | 统一入口、JWT 解析与鉴权、路由转发 |
| `lab-user-service` | 9100 | 用户/角色/权限、登录、Excel 批量导入、操作日志 |
| `lab-resource-service` | 9200 | 实训室台账、设备台账、耗材出入库 |
| `lab-business-service` | 9300 | 预约审批（状态机）、报修维修（工单流转） |
| `lab-report-service` | 9400 | 统计报表，监听 MQ 事件维护汇总数据 |

配套基础设施：Nacos 注册中心（standalone）、RabbitMQ 消息队列、MySQL 8.0（每服务一 schema）。

### 4.2 技术版本

- Java 11、Spring Boot 2.7.x、Spring Cloud 2021.0.x、Spring Cloud Alibaba 2021.0.5.0
- 服务间调用 OpenFeign；消息：RabbitMQ
- 认证：登录 → user-service 签发 JWT(HS256) → 网关校验放行
- 数据库：MySQL 8.0，独立 schema：`lab_user` / `lab_resource` / `lab_business` / `lab_report`
- 构建：Maven Wrapper（无需预装 Maven）
- 前端：Vue3 + TypeScript + Element Plus + Pinia + Vite，`/api/*` 代理至网关 9000

### 4.3 数据流（示例：预约审批）

学生/教师提交预约 → business-service 校验冲突并落库 → 发"待审批"事件至 RabbitMQ → 管理员审批 → 状态变更再次发 MQ → 通知与统计服务消费更新。

## 5. 数据模型

### 5.1 权限模型（RBAC）

- 表：`sys_user`、`sys_role`、`sys_user_role`、`sys_menu`、`sys_role_menu`、`sys_operation_log`
- 控制：接口角色注解 + 前端路由守卫 + 菜单权限控制按钮显隐

### 5.2 实训室预约状态机

```
待审批 → (审批通过) → 已批准 → (使用) → 已完成
待审批 → (驳回/取消) → 已驳回/已取消
```

- 冲突检测：同一实训室 + 时间区间重叠判冲突；提交校验 + 审批二次校验
- 操作权限：审批仅实训室管理员/系统管理员；学生可取消自己的待审批

### 5.3 报修工单状态机

```
待派单 → 已派单(指派维修员) → 维修中 → 已完成 → (管理员验收) → 已验收
待派单 → 已驳回
```

- 报修来源：教师/管理员提交 → 关联设备 → 自动带出所属实训室
- 维修员：不新增第五类角色，派单时从在职用户（实训室管理员/教师/系统管理员)中选择担任维修员

### 5.4 核心表清单

`lab_user`（schema: lab_user）：
- `sys_user(id, username, password, real_name, employee_no, phone, dept, role, status, created_at)`
- `sys_role`、`sys_user_role`、`sys_menu`、`sys_role_menu`、`sys_operation_log`

`lab_resource`（schema: lab_resource）：
- `lab_room(id, code, name, building, floor, capacity, type, manager_id, status, remark)`
- `lab_device(id, code, name, category, room_id, brand, model_no, status, buy_date, price)`
- `material(id, code, name, spec, unit, stock, warn_threshold)`
- `material_record(id, material_id, type, quantity, operator_id, created_at)`（出入库流水）

`lab_business`（schema: lab_business）：
- `reservation(id, room_id, applicant_id, purpose, start_time, end_time, people_num, status, approver_id, reject_reason, created_at)`
- `repair_order(id, device_id, room_id, reporter_id, desc, status, assignee_id, result, completed_at)`

`lab_report`（schema: lab_report）：
- 汇总聚合表（如 `stat_daily_room_usage`），由 MQ 事件驱动刷新

## 6. 关键接口

- 认证：`POST /api/v1/auth/login`
- 用户：`POST /api/v1/users/import`（Excel 批量导入）
- 预约：`POST /api/v1/reservations`、`POST /api/v1/reservations/{id}/approve`、`POST /api/v1/reservations/{id}/reject`
- 报修：`POST /api/v1/repairs`、`POST /api/v1/repairs/{id}/assign`、`/finish`、`/verify`
- 统计：`GET /api/v1/stats/room-usage`、`GET /api/v1/stats/repair`、`GET /api/v1/stats/overview`

## 7. 前端设计

页面与角色权限矩阵：

| 页面/路由 | 可访问角色 |
|---|---|
| 登录页 `/login` | 所有人 |
| 工作台仪表盘 `/dashboard` | 所有人 |
| 实训室台账 `/rooms`、设备台账 `/devices` | 全角色（学生/教师只读，管理员可编辑） |
| 耗材管理 `/materials` | 实训室管理员、系统管理员 |
| 预约模块 `/reservations`（我的预约/审批中心） | 学生、教师提交；管理员审批 |
| 报修工单 `/repairs` | 教师、管理员提交/处理 |
| 系统管理 `/system/users` `/system/roles` `/system/logs` | 系统管理员 |
| 统计报表 `/reports` | 管理员/教师 |

- 目录结构：`api/` `router/` `store/` `views/` `components/` `utils/`
- Pinia 存 token 与用户信息；路由守卫基于角色过滤

## 8. 错误处理与安全

- 后端：`@RestControllerAdvice` 统一返回 `{ code, message, data }`
- 前端：axios 拦截器统一错误提示、401 清 token 跳登录
- 密码 BCrypt 加密；JWT HS256；logback 日志 + 操作日志写库

## 9. 测试与验收

- 后端：JUnit5 覆盖预约冲突检测、状态流转；服务启动冒烟
- 前端：`vite build` 可构建；主要页面自测
- 一键脚本：`start-all.cmd`（Nacos → 四个服务 → 前端）、`stop-all.cmd`
- 验收链路：
  1. 「学生/教师预约 → 管理员审批 → 统计报表可见」
  2. 「报修 → 派单 → 维修 → 验收」