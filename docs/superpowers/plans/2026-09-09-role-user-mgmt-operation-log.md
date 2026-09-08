# 角色拆分 + 姓名显示 + 用户冻结/删除 + 操作日志 实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 按已批准设计（`docs/superpowers/specs/2026-09-09-role-user-mgmt-operation-log-design.md`）实现四项改进：① 副院长(VICE_DEAN)与院长(DEAN)角色拆分；② 主页/顶栏显示真实姓名；③ 用户管理补冻结/解冻与删除；④ 真正启用操作日志（谁、IP、干了什么、时间）。

**架构：** 增量修改既有 Spring Cloud 微服务。角色为 `sys_user.role` 字符串字段（无需改表）。操作日志在网关层统一记录写操作（异步 fire-and-forget）+ user 服务记录登录日志，落库 `sys_operation_log`。

**技术栈：** Java 11 / Spring Boot 2.7.18 / Spring Cloud Gateway(WebFlux) / MyBatis-Plus / JUnit5 + Mockito / Vue3 + Element Plus / Vite / TypeScript

---

## 文件结构

**后端（lab-business-service）**
- 修改 `lab-business-service/src/main/java/com/gdcp/lab/business/common/ApprovalRoleUtil.java`：加 `VICE_DEAN` 常量并纳入 `canSecondApprove`

**后端（lab-user-service）**
- 修改 `.../entity/OperationLog.java`：加 `ip` 字段
- 修改 `.../service/OperationLogService.java`：`record` 增加带 ip 的重载
- 修改 `.../service/UserService.java`：新增 `deleteUser(id, operatorId, operatorRole)`（`toggleStatus` 已存在复用）
- 修改 `.../controller/UserController.java`：新增 `POST /{id}/status`、`DELETE /{id}`
- 新建 `.../controller/InternalLogController.java`：内部日志端点 `POST /api/v1/internal/oplog`（独立控制器，避免类前缀拼接问题）
- 修改 `.../service/AuthService.java`：登录成功/失败记录日志（含 username + IP）
- 修改 `.../controller/AuthController.java`：从请求头取 IP 传入 login（注意 Spring Boot 2.7 用 `javax.servlet`）
- 修改 `lab-user-service/src/test/.../UserServiceTest.java`：新增冻结/删除用例
- 修改 `lab-user-service/src/test/.../AuthServiceTest.java`：适配新构造签名 + 登录日志用例

**后端（lab-gateway）**
- 修改 `lab-gateway/.../filter/AuthGlobalFilter.java`：非 GET 且鉴权成功后异步 WebClient 调内部日志接口
- 新建 `lab-gateway/.../config/WebClientConfig.java`：`@LoadBalanced WebClient.Builder` bean
- 修改 `application.yml`：user-service 路由 predicates 追加 `/api/v1/internal/**`

**数据库**
- 修改 `scripts/init-db.sql`：`sys_operation_log` CREATE 加 `ip VARCHAR(64)`
- 对运行中 `lab_user` 库执行 `ALTER TABLE sys_operation_log ADD COLUMN ip VARCHAR(64)`

**前端（frontend）**
- 修改 `src/store/user.ts`：加 `realName` 状态 + `setAuth` 参数 + localStorage + logout 清理
- 修改 `src/views/Login.vue`：`setAuth` 传 `info.realName`
- 修改 `src/views/Dashboard.vue`、`src/layouts/MainLayout.vue`：显示 `realName || username`；Dashboard 角色文案补 DIRECTOR/DEAN/VICE_DEAN
- 修改 `src/api/user.ts`：加 `setUserStatus(id, enabled)`、`deleteUser(id)`；`listLogs` 类型补 `ip`
- 修改 `src/views/SystemUsers.vue`：角色下拉加 VICE_DEAN、DEAN 文案改"院长"；操作列加冻结/解冻/删除
- 修改 `src/views/Reservations.vue`、`src/views/Repairs.vue`：审批数组加 `VICE_DEAN`
- 修改 `src/views/SystemLogs.vue`：加 IP 列；操作人列 join 用户表显示"姓名(用户名)"

---

## 数据库变更（任务 4 前置）

运行中 `lab_user` 库执行：

```sql
ALTER TABLE sys_operation_log ADD COLUMN ip VARCHAR(64) DEFAULT NULL COMMENT '客户端IP';
```

`scripts/init-db.sql` 的 `sys_operation_log` CREATE 语句同步追加：

```sql
CREATE TABLE sys_operation_log (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT, username VARCHAR(64), action VARCHAR(128),
  detail VARCHAR(512), ip VARCHAR(64), created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);
```

---

### 任务 1：角色拆分（VICE_DEAN）

**文件：**
- 修改：`lab-business-service/src/main/java/com/gdcp/lab/business/common/ApprovalRoleUtil.java`
- 修改：`frontend/src/views/SystemUsers.vue`（仅角色下拉与映射，操作列在任务 3）
- 修改：`frontend/src/views/Reservations.vue`、`frontend/src/views/Repairs.vue`（审批数组）

- [x] **步骤 1：ApprovalRoleUtil 加 VICE_DEAN**

```java
public static final String VICE_DEAN = "VICE_DEAN";
// 二级审批：院长/副院长或系统管理员
public static boolean canSecondApprove(String role) {
    return DEAN.equals(role) || VICE_DEAN.equals(role) || SYSTEM_ADMIN.equals(role);
}
```

- [x] **步骤 2：SystemUsers.vue 角色下拉与映射**

```html
<el-option value="DEAN" label="院长" />
<el-option value="VICE_DEAN" label="副院长" />
```

`roleTextMap`：`DEAN: '院长'`，新增 `VICE_DEAN: '副院长'`；`roleTagMap`：`DEAN: 'danger'`，`VICE_DEAN: 'danger'`。

- [x] **步骤 3：Reservations.vue / Repairs.vue 审批数组**

```ts
// Reservations.vue
const canApprove = computed(() => ['DIRECTOR', 'DEAN', 'SYSTEM_ADMIN', 'VICE_DEAN'].includes(role.value))
const canSecondApprove = computed(() => ['DEAN', 'SYSTEM_ADMIN', 'VICE_DEAN'].includes(role.value))
// Repairs.vue
const canSecondApprove = computed(() => ['DEAN', 'SYSTEM_ADMIN', 'VICE_DEAN'].includes(role.value))
```

- [x] **步骤 4：验证 + Commit**

`.\mvnw.cmd -pl lab-business-service -am compile -q` → BUILD SUCCESS；`frontend` 目录 `npm run build` → SUCCESS。

```bash
git add lab-business-service/src/main/java/com/gdcp/lab/business/common/ApprovalRoleUtil.java frontend/src/views/SystemUsers.vue frontend/src/views/Reservations.vue frontend/src/views/Repairs.vue
git commit -m "feat: 拆分副院长角色（VICE_DEAN），二级审批支持院长/副院长/系统管理员"
```
（**已提交** `8d7b78d`）

---

### 任务 2：主页/顶栏显示姓名

**文件：**
- 修改：`frontend/src/store/user.ts`
- 修改：`frontend/src/views/Login.vue`
- 修改：`frontend/src/views/Dashboard.vue`
- 修改：`frontend/src/layouts/MainLayout.vue`

- [x] **步骤 1：store/user.ts 加 realName**

```ts
state: () => ({
  token: localStorage.getItem('token') || '',
  role: localStorage.getItem('role') || '',
  username: localStorage.getItem('username') || '',
  realName: localStorage.getItem('realName') || '',
  userId: Number(localStorage.getItem('userId')) || 0
}),
setAuth(token: string, role: string, username: string, realName: string, userId: number) {
  // ...设置全部字段 + localStorage 写入 realName
},
logout() {
  // ...清空 + localStorage.removeItem('realName')
}
```

- [x] **步骤 2：Login.vue 传 realName**

```ts
store.setAuth(token, info.role, info.username, info.realName, info.id)
```

- [x] **步骤 3：Dashboard.vue / MainLayout.vue 显示姓名**

`欢迎，{{ store.realName || store.username }}`；顶栏 `{{ store.realName || store.username }}`。Dashboard 的 `roleText` map 补 `DIRECTOR: '主任'`、`DEAN: '院长'`、`VICE_DEAN: '副院长'`。

- [x] **步骤 4：验证 + Commit**

`frontend` 目录 `npm run build` → SUCCESS。

```bash
git add frontend/src/store/user.ts frontend/src/views/Login.vue frontend/src/views/Dashboard.vue frontend/src/layouts/MainLayout.vue
git commit -m "feat: 主页与顶栏显示真实姓名（回退用户名）"
```
（**已提交** `deeed2f`）

---

### 任务 3：用户冻结/删除（TDD）

**文件：**
- 修改：`lab-user-service/src/main/java/com/gdcp/lab/user/service/UserService.java`
- 修改：`lab-user-service/src/main/java/com/gdcp/lab/user/controller/UserController.java`
- 修改：`lab-user-service/src/test/java/com/gdcp/lab/user/service/UserServiceTest.java`
- 修改：`frontend/src/api/user.ts`
- 修改：`frontend/src/views/SystemUsers.vue`

- [x] **步骤 1：编写失败测试（UserServiceTest 追加）**

```java
@Test
void toggleStatus_freeze_sets0() {
    SysUser u = new SysUser(); u.setId(1L); u.setStatus(1);
    Mockito.when(mapper.selectById(1L)).thenReturn(u);
    service.toggleStatus(1L, 0);
    assertEquals(0, u.getStatus());
    Mockito.verify(mapper).updateById(u);
}

@Test
void toggleStatus_userNotFound_throws() {
    Mockito.when(mapper.selectById(99L)).thenReturn(null);
    assertThrows(BizException.class, () -> service.toggleStatus(99L, 0));
}

@Test
void deleteUser_success() {
    SysUser u = new SysUser(); u.setId(2L); u.setRole("TEACHER");
    Mockito.when(mapper.selectById(2L)).thenReturn(u);
    service.deleteUser(2L, 1L, "SYSTEM_ADMIN");
    Mockito.verify(mapper).deleteById(2L);
}

@Test
void deleteUser_self_throws() {
    SysUser u = new SysUser(); u.setId(1L); u.setRole("TEACHER");
    Mockito.when(mapper.selectById(1L)).thenReturn(u);
    assertThrows(BizException.class, () -> service.deleteUser(1L, 1L, "SYSTEM_ADMIN"));
}

@Test
void deleteUser_systemAdmin_throws() {
    SysUser u = new SysUser(); u.setId(2L); u.setRole("SYSTEM_ADMIN");
    Mockito.when(mapper.selectById(2L)).thenReturn(u);
    assertThrows(BizException.class, () -> service.deleteUser(2L, 1L, "SYSTEM_ADMIN"));
}
```

- [x] **步骤 2：运行测试验证失败**

`.\mvnw.cmd -pl lab-user-service -am test -Dtest=UserServiceTest -q`
预期：编译失败（`deleteUser` 不存在）

- [x] **步骤 3：实现 UserService.deleteUser**

```java
public void deleteUser(Long id, Long operatorId, String operatorRole) {
    SysUser u = userMapper.selectById(id);
    if (u == null) throw new BizException("用户不存在");
    if (id.equals(operatorId)) throw new BizException("不能删除当前登录用户");
    if ("SYSTEM_ADMIN".equals(u.getRole())) throw new BizException("不能删除系统管理员");
    userMapper.deleteById(id);
}
```

（`toggleStatus` 已存在，不动。）

- [x] **步骤 4：UserController 新增端点**

```java
@PostMapping("/{id}/status")
public Result<Void> setStatus(@PathVariable Long id, @RequestBody Map<String, Object> body) {
    boolean enabled = Boolean.TRUE.equals(body.get("enabled"));
    userService.toggleStatus(id, enabled ? 1 : 0);
    return Result.ok(null);
}

@DeleteMapping("/{id}")
public Result<Void> delete(@PathVariable Long id,
        @RequestHeader(value = "X-User-Id", required = false) Long operatorId,
        @RequestHeader(value = "X-User-Role", required = false) String operatorRole) {
    userService.deleteUser(id, operatorId == null ? 0L : operatorId, operatorRole);
    return Result.ok(null);
}
```

- [x] **步骤 5：运行测试验证通过**

`.\mvnw.cmd -pl lab-user-service -am test -Dtest=UserServiceTest -q` → BUILD SUCCESS

- [x] **步骤 6：前端 api/user.ts**

```ts
export const setUserStatus = (id: number, enabled: boolean) => request.post(`/users/${id}/status`, { enabled })
export const deleteUser = (id: number) => request.delete(`/users/${id}`)
```

- [x] **步骤 7：SystemUsers.vue 操作列**

表格追加操作列（当前用户与 SYSTEM_ADMIN 行禁用删除）：

```html
<el-table-column label="操作" width="150" fixed="right">
  <template #default="{ row }">
    <el-button size="small" :type="row.status === 1 ? 'warning' : 'success'" plain
               @click="handleToggleStatus(row)">{{ row.status === 1 ? '冻结' : '解冻' }}</el-button>
    <el-button size="small" type="danger" plain :disabled="row.id === store.userId || row.role === 'SYSTEM_ADMIN'"
               @click="handleDelete(row)">删除</el-button>
  </template>
</el-table-column>
```

脚本：引入 `setUserStatus, deleteUser`、`useUserStore`、`ElMessageBox`；`handleToggleStatus` 调用后 `ElMessage.success(row.status === 1 ? '已冻结' : '已解冻')` 并 `load()`；`handleDelete` 用 `ElMessageBox.confirm` 确认后 `deleteUser(row.id)`，失败时 `ElMessage.error(e.message)`（后端错误提示透出）。

- [x] **步骤 8：验证 + Commit**

`frontend` 目录 `npm run build` → SUCCESS。

```bash
git add lab-user-service/src/main/java/com/gdcp/lab/user/service/UserService.java lab-user-service/src/main/java/com/gdcp/lab/user/controller/UserController.java lab-user-service/src/test/java/com/gdcp/lab/user/service/UserServiceTest.java frontend/src/api/user.ts frontend/src/views/SystemUsers.vue
git commit -m "feat: 用户管理支持冻结/解冻与删除（禁止删自己与系统管理员）"
```
（**已提交** `478e38b`）

---

### 任务 4：操作日志（谁、IP、干了什么、时间）

**文件：**
- 修改：`scripts/init-db.sql`
- 修改：`lab-user-service/src/main/java/com/gdcp/lab/user/entity/OperationLog.java`
- 修改：`lab-user-service/src/main/java/com/gdcp/lab/user/service/OperationLogService.java`
- 新建：`lab-user-service/src/main/java/com/gdcp/lab/user/controller/InternalLogController.java`
- 修改：`lab-user-service/src/main/java/com/gdcp/lab/user/service/AuthService.java`
- 修改：`lab-user-service/src/main/java/com/gdcp/lab/user/controller/AuthController.java`
- 修改：`lab-user-service/src/test/java/com/gdcp/lab/user/service/AuthServiceTest.java`
- 修改：`lab-gateway/src/main/java/com/gdcp/lab/gateway/filter/AuthGlobalFilter.java`
- 新建：`lab-gateway/src/main/java/com/gdcp/lab/gateway/config/WebClientConfig.java`
- 修改：`lab-gateway/src/main/resources/application.yml`
- 修改：`frontend/src/api/user.ts`、`frontend/src/views/SystemLogs.vue`

- [x] **步骤 1：数据库 + 实体**

init-db.sql 的 `sys_operation_log` CREATE 加 `ip VARCHAR(64)`；对运行中 `lab_user` 库执行 `ALTER TABLE sys_operation_log ADD COLUMN ip VARCHAR(64) DEFAULT NULL COMMENT '客户端IP';`。

`OperationLog.java` 加：

```java
private String ip;
```

- [x] **步骤 2：OperationLogService 加带 ip 的重载**

```java
public void record(Long userId, String username, String action, String detail, String ip) {
    OperationLog log = new OperationLog();
    log.setUserId(userId); log.setUsername(username);
    log.setAction(action); log.setDetail(detail); log.setIp(ip);
    logMapper.insert(log);
}
```

（原 4 参方法改为委托调用，保持兼容。）

- [x] **步骤 3：内部日志端点（落地为独立控制器）**

实现说明：最初计划在 UserController 加 `@PostMapping("/api/v1/internal/oplog")`，但类级 `@RequestMapping("/api/v1/users")` 会拼接成 `/api/v1/users/api/v1/internal/oplog` 导致 404。**最终落地为独立 `InternalLogController`**（`@RequestMapping("/api/v1/internal")`）：

```java
@PostMapping("/oplog")
public Result<Void> record(@RequestBody Map<String, Object> body) {
    Long userId = body.get("userId") == null ? null : Long.valueOf(body.get("userId").toString());
    String ip = (String) body.get("ip");
    String action = (String) body.get("action");
    operationLogService.record(userId, null, action, null, ip);
    return Result.ok(null);
}
```

同时：UserController 移除该端点并还原构造器；网关 `application.yml` 的 user-service 路由 predicates 追加 `/api/v1/internal/**`。

- [x] **步骤 4：AuthService 登录日志（先改测试）**

AuthServiceTest `setUp` 改为 4 参构造（新增 `logService = Mockito.mock(OperationLogService.class)`），新增用例验证 `record(...)` 调用与 IP 传参。

实现（3 参 + 2 参委托；禁用账号区分提示）：

```java
public String login(String username, String password, String ip) {
    SysUser u = userMapper.findByUsername(username).orElse(null);
    if (u == null) { logService.record(null, username, "登录失败", "用户不存在", ip);
                     throw new BizException(401, "用户名或密码错误"); }
    if (u.getStatus() == null || u.getStatus() != 1) {
        logService.record(u.getId(), u.getUsername(), "登录失败", "账号已被禁用", ip);
        throw new BizException(401, "账号已被禁用");
    }
    if (!encoder.matches(password, u.getPassword())) {
        logService.record(u.getId(), u.getUsername(), "登录失败", "账号或密码错误", ip);
        throw new BizException(401, "用户名或密码错误");
    }
    logService.record(u.getId(), u.getUsername(), "登录系统", null, ip);
    return jwtUtil.generate(String.valueOf(u.getId()), u.getRole());
}
```

AuthController.login 加 `HttpServletRequest request`（**Spring Boot 2.7 必须 `javax.servlet`**），用 `resolveIp`（X-Forwarded-For 首个或 getRemoteAddr）传 IP。

- [x] **步骤 5：运行 user-service 测试**

`.\mvnw.cmd -pl lab-user-service -am test -q` → BUILD SUCCESS（UserServiceTest + AuthServiceTest 全过）

- [x] **步骤 6：网关异步记录写操作**

创建 `WebClientConfig.java`：

```java
@Configuration
public class WebClientConfig {
    @Bean
    @LoadBalanced
    public WebClient.Builder webClientBuilder() { return WebClient.builder(); }
}
```

`AuthGlobalFilter` 注入 `WebClient.Builder`；JWT 解析成功后、放行前，非 GET 请求异步 fire-and-forget：

```java
if (!"GET".equals(method)) {
    recordLog(claims.get("userId").toString(), resolveClientIp(exchange.getRequest()), method.trim() + " " + path);
}
```

`recordLog` 用 `webClient.post().uri("http://lab-user-service/api/v1/internal/oplog")` 异步调用，失败仅 `log.warn` 不阻塞请求。

`resolveClientIp`：优先 `X-Forwarded-For` 第一个地址，否则 `getRemoteAddress().getAddress().getHostAddress()`。

- [x] **步骤 7：前端 SystemLogs.vue**

接口类型加 `ip`；加 IP 列 `width="140"`；操作人列通过 `listUsers()` join 显示"姓名(用户名)"（`nameOf(row.userId)`）。

- [x] **步骤 8：验证 + Commit**

`.\mvnw.cmd -pl lab-user-service -am test -q` + `frontend` 目录 `npm run build` → 全 SUCCESS。

**走查证据（DB 实测）：** 登录成功 `登录系统` / 失败 `登录失败`（detail: 用户不存在/账号已被禁用/账号或密码错误）均含 IP；网关写操作 `POST /api/v1/users`、`DELETE /api/v1/users/11`、`POST /api/v1/users/13/status` 均已记录；GET 不记录。

---

### 任务 5：全量验证与提交

- [x] **步骤 1：全量后端测试**

`.\mvnw.cmd -pl lab-user-service,lab-resource-service,lab-business-service,lab-report-service,lab-gateway -am test -DfailIfNoTests=false` → BUILD SUCCESS

- [x] **步骤 2：前端构建**

`frontend` 目录 `npm run build` → SUCCESS

- [x] **步骤 3：打包全部服务**

`.\mvnw.cmd clean package -DskipTests` → BUILD SUCCESS（注意：增量 test/package 会跳过“认为未变更”的 class 重新编译导致源码报错静默通过，必须 `clean`）

- [x] **步骤 4：重启服务 + 数据库升级**

5 服务 + Nacos 全部启动（Nacos READY_200）；`lab_user` 库已执行 `ALTER TABLE sys_operation_log ADD COLUMN ip VARCHAR(64)`。

- [x] **步骤 5：运行级验收走查**

覆盖：登录（成功/失败跑 IP）→ `me` 返回 realName → 用户创建/删除 → 冻结账号登录被拒（业务码 401"账号已被禁用"）→ 解冻后登录成功 → 操作日志含操作人/姓名/IP/动作/时间 → GET 不产生日志。

采用脚本：`lab-management-system/logs/walkthrough.ps1`（登录/me/列表/创建/删除/日志）与 `walkthrough2.ps1`（冻结→登录被拒→解冻→登录成功→清理）。全部通过，DB 日志证据完整（见任务 4 步骤 8）。

- [x] **步骤 6：提交全部变更**

```bash
git add -A
git commit -m "feat: 操作日志正式启用（网关记录写操作+登录日志，含IP）并更新实现计划状态"
git push
```
（**已提交** `0952aa5`，已推送 `82bcab4..0952aa5 main -> main`）