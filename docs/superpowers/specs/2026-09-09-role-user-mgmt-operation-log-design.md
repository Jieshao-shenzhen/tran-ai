# 设计规格：角色拆分 / 用户管理增强 / 操作日志

日期：2026-09-09
状态：已获用户批准（用户回复"无"= 无修改意见）

## 背景

在已完成"预约/报修二级审批 + 耗材新增删除"的基础上，用户提出四项改进：

1. **副院长和院长分开**：当前 `DEAN` 角色为"院长/副院长"合并，需拆分为 `DEAN`（院长）、`VICE_DEAN`（副院长）。
2. **主页显示姓名**：Dashboard 欢迎语与顶栏用户信息当前显示 `username`，应显示 `realName`。
3. **用户管理页补充操作**：当前仅"新增"，需补"冻结/解冻"与"删除"。
4. **操作日志补全**：现有 `sys_operation_log` 表与 `OperationLogService.record()` 已存在但从未被调用，需真正启用，记录"谁、IP、干了什么、时间"。

## 决策（已确认）

- 二级审批权限：**院长(DEAN)、副院长(VICE_DEAN)、系统管理员(SYSTEM_ADMIN)** 均可审批。
- 用户管理：**冻结 + 删除 都要**。冻结=禁用登录（可解冻恢复），删除=物理删除账号。
- 日志范围：**写操作 + 登录**。查询(GET)不记录。
- 姓名显示：**主页 + 顶栏 都改**。

---

## 一、角色拆分（院长 / 副院长）

### 后端

`lab-business-service .../common/ApprovalRoleUtil.java`：

- 新增常量 `VICE_DEAN = "VICE_DEAN"`
- `canSecondApprove(role)` 改为：`DEAN / VICE_DEAN / SYSTEM_ADMIN` 返回 true

### 前端

- `SystemUsers.vue`：角色下拉新增 `<el-option value="VICE_DEAN" label="副院长" />`；`DEAN` 文案由"院长/副院长"改为"院长"；`roleTextMap`/`roleTagMap` 增加 `VICE_DEAN`。
- `Reservations.vue` / `Repairs.vue`：
  - `canApprove` 数组：`['DIRECTOR','DEAN','SYSTEM_ADMIN','VICE_DEAN']`
  - `canSecondApprove` 数组：`['DEAN','SYSTEM_ADMIN','VICE_DEAN']`

### 数据库

无需变更（`sys_user.role` 为字符串，`sys_role` 表无种子数据、未被使用）。

---

## 二、主页 / 顶栏显示姓名

- `frontend/src/store/user.ts`：新增 `realName` 状态；`setAuth` 增加 `realName` 参数并写入 localStorage；logout 时清除。
- `Login.vue`：登录后 `me()` 已返回 `realName`，调用 `store.setAuth(token, info.role, info.username, info.realName, info.id)`。
- `Dashboard.vue`：`欢迎，{{ store.realName || store.username }}`
- `layouts/MainLayout.vue`：`{{ store.realName || store.username }}`
- 前端类型：`api/auth.ts` 中 `me()` 返回类型补 `realName` 字段（如需要）。

---

## 三、用户管理：冻结 + 删除

### 后端 `lab-user-service`

`UserService`：

- `setStatus(Long id, boolean enabled)`：不存在抛"用户不存在"；`update` 设置 `status`（1/0）。
- `deleteUser(Long id, Long operatorId, String operatorRole)`：
  - 目标不存在 → 抛"用户不存在"
  - `id.equals(operatorId)` → 抛"不能删除当前登录用户"
  - 目标角色为 `SYSTEM_ADMIN` → 抛"不能删除系统管理员"
  - 否则 `deleteById`

`UserController`：

- `@PostMapping("/{id}/status")`：body `Map<String,Object>` 取 `enabled`（boolean），调用 `setStatus`；从 `X-User-Id` 头取操作人（网关注入）记录日志。
- `@DeleteMapping("/{id}")`：从 `X-User-Id`/`X-User-Role` 头取操作人，调用 `deleteUser`。

### 前端

- `api/user.ts`：新增 `setUserStatus(id, enabled)`、`deleteUser(id)`。
- `SystemUsers.vue`：
  - 操作列新增"冻结/解冻"按钮（`row.status === 1 ? '冻结' : '解冻'`）。
  - 操作列新增"删除"按钮（`ElMessageBox.confirm` 确认后调用）。
  - 删除/冻结仅对非当前登录用户可用（简单判断：不禁止后端，后端已保护）。

---

## 四、操作日志（谁、IP、干了什么、时间）

### 数据库

`lab_user.sys_operation_log` 增加列：

```sql
ALTER TABLE sys_operation_log ADD COLUMN ip VARCHAR(64) DEFAULT NULL COMMENT '客户端IP';
```

同步更新 `scripts/init-db.sql` 的 CREATE 语句。

### 实体

`OperationLog.java`：新增 `private String ip;`

### 网关统一记录写操作

`lab-gateway .../filter/AuthGlobalFilter.java`：

- 对**非 GET 且鉴权成功**的请求，异步记录日志（fire-and-forget，失败不影响主流程）：
  - `userId`：JWT 中 `userId`
  - `ip`：优先 `X-Forwarded-For` 首个地址，否则 `getRemoteAddress()`
  - `action`：`HTTP方法 路径`（如 `POST /api/v1/reservations/6/approve`）
- 调用方式：网关用 Spring WebFlux 自带 `WebClient`（或 `Mono.fromRunnable` + `WebClient.post`）调 `http://lab-user-service/api/v1/internal/oplog`（通过 LoadBalancer `lb://lab-user-service`）。发送失败仅打印 warn，不影响主链路。
- 登录路径 `/api/v1/auth/login` 不在此记录（登录日志在 user 服务记录，含 username）。

### 用户服务内部接口 + 登录日志

- `UserController` 或新增内部 Controller：`@PostMapping("/api/v1/internal/oplog")`，body `{userId, ip, action}`，调用 `operationLogService.record(userId, null, action, null)` 并设置 `ip`。username 不落库（前端 join 用户表显示姓名），或查询时动态补充。
- `AuthService.login`：
  - 登录成功：`record(user.getId(), user.getUsername(), "登录系统", ...)` + IP（从 `X-Forwarded-For` 头取）
  - 登录失败（密码错误/账号禁用）：可选记录 `action="登录失败"`（含尝试的 username）
  - 需要把 `HttpServletRequest` 传入 service（Controller 传递）。

### 前端

- `SystemLogs.vue`：
  - 增加"IP"列（`prop="ip"`）。
  - "操作人"列：复用 `listUsers()` 构建 `userId → realName(username)` 映射后显示姓名；日志实体保留 `username` 字段的兼容（登录日志有 username）。
- `api/user.ts`：`listLogs` 返回类型补 `ip` 字段。

---

## 数据流

```
浏览器 → 网关(9000) ──JWT校验──→ 注入 X-User-Id/X-User-Role 头
            │  （非GET写操作）异步: WebClient → lab-user-service /api/v1/internal/oplog
            │                                      └→ sys_operation_log(user_id, ip, action, created_at)
            └→ 下游服务执行业务
登录: user-service AuthService.login → record(username, ip, "登录系统") → sys_operation_log
```

## 测试

### 单元测试（JUnit5 + Mockito）

- `UserServiceTest` 新增：
  - 冻结成功（status=0）
  - 解冻成功（status=1）
  - 冻结不存在的用户 → 抛"用户不存在"
  - 删除成功
  - 删除自己 → 抛"不能删除当前登录用户"
  - 删除 SYSTEM_ADMIN → 抛"不能删除系统管理员"
- 现有 `ApprovalRoleUtil` 相关测试：确认 `VICE_DEAN` 可二级审批（若有直接测试则更新）。

### 运行级验收

- 前端 `npm run build` 通过。
- 5 服务 + Nacos + 前端启动后 API 走查：
  - 新增 `vicedean`（副院长）账号，登录后对 `DIRECTOR_APPROVED` 预约可审批。
  - 冻结某账号后该账号登录被拒（"账号已被禁用"）；解冻后可登录。
  - 删除普通账号成功；尝试删除自己/系统管理员被拒。
  - 执行预约/报修/用户操作后，`/api/v1/logs` 出现对应记录且含 IP；登录日志含 username。

## 范围外（YAGNI）

- 不做三级审批。
- 不做软删除（物理删除 + 保护规则已满足需求）。
- 日志不做分页/搜索/导出（现有 LIMIT 200 列表即可）。
- 不记录 GET 查询日志。
