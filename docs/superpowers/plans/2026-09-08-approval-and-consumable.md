# 耗材增删 + 二级审批 实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 为实训室管理系统新增耗材的新增/删除功能，并将预约与报修改为「主任 → 院长/副院长」两级审批（预约时长 ≥24h 需两级，<24h 仅主任）。

**架构：** 在既有 Spring Cloud 微服务上增量修改：`lab-business-service` 负责预约/报修审批状态机与角色校验，`lab-resource-service` 负责耗材增删；前端 Vue3 页面按角色展示审批入口与耗材管理按钮。角色为字符串字段，新增 `DIRECTOR`（主任）、`DEAN`（院长/副院长）。

**技术栈：** Java 11 / Spring Boot 2.7.18 / MyBatis-Plus / JUnit5 + Mockito / Vue3 + Element Plus / Vite / TypeScript

---

## 文件结构

**后端（lab-business-service）**
- 修改 `lab-business-service/src/main/java/com/gdcp/lab/business/entity/Reservation.java`：加 `secondApproverId`
- 修改 `lab-business-service/src/main/java/com/gdcp/lab/business/entity/RepairOrder.java`：加 `approverId`、`secondApproverId`
- 修改 `lab-business-service/src/main/java/com/gdcp/lab/business/mapper/ReservationMapper.java`：冲突检测包含 `DIRECTOR_APPROVED`
- 创建 `lab-business-service/src/main/java/com/gdcp/lab/business/common/ApprovalRoleUtil.java`：角色校验工具
- 修改 `lab-business-service/src/main/java/com/gdcp/lab/business/service/ReservationService.java`：两级审批状态机
- 修改 `lab-business-service/src/main/java/com/gdcp/lab/business/service/RepairService.java`：两级审批状态机 + assign 前置状态
- 修改 `lab-business-service/src/main/java/com/gdcp/lab/business/controller/ReservationController.java`：审批接口传 `X-User-Role`
- 修改 `lab-business-service/src/main/java/com/gdcp/lab/business/controller/RepairController.java`：新增 approve 接口、reject 传角色

**后端（lab-resource-service）**
- 修改 `lab-resource-service/src/main/java/com/gdcp/lab/resource/service/ResourceService.java`：`createMaterial` / `deleteMaterial`
- 修改 `lab-resource-service/src/main/java/com/gdcp/lab/resource/controller/ResourceController.java`：`POST /materials`、`DELETE /materials/{id}`

**数据库**
- 修改 `scripts/init-db.sql`：reservation 加 `second_approver_id`；repair_order 加 `approver_id`、`second_approver_id`（CREATE 语句 + 注释提供 ALTER 供已初始化库升级）

**测试**
- 修改 `lab-business-service/src/test/java/com/gdcp/lab/business/service/ReservationServiceTest.java`
- 修改 `lab-business-service/src/test/java/com/gdcp/lab/business/service/RepairServiceTest.java`
- 修改 `lab-resource-service/src/test/java/com/gdcp/lab/resource/service/ResourceServiceTest.java`

**前端（frontend）**
- 修改 `frontend/src/api/business.ts`：加 `approveRepair`
- 修改 `frontend/src/api/resource.ts`：加 `createMaterial`、`deleteMaterial`
- 修改 `frontend/src/views/SystemUsers.vue`：角色下拉与名称映射加 DIRECTOR/DEAN
- 修改 `frontend/src/views/Reservations.vue`：审批中心按角色过滤 + 状态文案 + 按钮显隐
- 修改 `frontend/src/views/Repairs.vue`：审批按钮 + 派单条件改 APPROVED + 状态文案
- 修改 `frontend/src/views/Materials.vue`：新增/删除按钮与弹窗

---

## 数据库变更（所有任务共用前置知识）

`scripts/init-db.sql` 中 `lab_business` 库：

```sql
-- reservation 增加二级审批人列
ALTER TABLE reservation ADD COLUMN second_approver_id BIGINT NULL COMMENT '二级审批人(院长/副院长)';
-- repair_order 增加两级审批人列
ALTER TABLE repair_order ADD COLUMN approver_id BIGINT NULL COMMENT '一级审批人(主任)',
                        ADD COLUMN second_approver_id BIGINT NULL COMMENT '二级审批人(院长/副院长)';
```

状态机约定：
- 预约：`PENDING`（待主任）→ 主任批：<24h 直接 `APPROVED`；≥24h 进 `DIRECTOR_APPROVED`（待院长）→ 院长批 → `APPROVED`；任一环节可 `REJECTED`
- 报修：`PENDING` → 主任批 → `DIRECTOR_APPROVED` → 院长批 → `APPROVED` → `ASSIGNED` → `COMPLETED` → `VERIFIED`；审批环节可 `REJECTED`

角色常量与校验（`ApprovalRoleUtil`）：
```java
public static final String DIRECTOR = "DIRECTOR";
public static final String DEAN = "DEAN";
public static final String SYSTEM_ADMIN = "SYSTEM_ADMIN";
// 一级审批：主任或系统管理员
public static boolean canFirstApprove(String role) { return DIRECTOR.equals(role) || SYSTEM_ADMIN.equals(role); }
// 二级审批：院长/副院长或系统管理员
public static boolean canSecondApprove(String role) { return DEAN.equals(role) || SYSTEM_ADMIN.equals(role); }
```

---

### 任务 1：实体与数据库字段

**文件：**
- 修改：`scripts/init-db.sql`（CREATE 语句加列 + 注释给出 ALTER）
- 修改：`lab-business-service/src/main/java/com/gdcp/lab/business/entity/Reservation.java`
- 修改：`lab-business-service/src/main/java/com/gdcp/lab/business/entity/RepairOrder.java`
- 修改：`lab-business-service/src/main/java/com/gdcp/lab/business/mapper/ReservationMapper.java`

- [ ] **步骤 1：修改 init-db.sql**

`lab_business` 库两张表 CREATE 语句分别追加：

```sql
-- reservation 表（原 approver_id 之后）
  second_approver_id BIGINT COMMENT '二级审批人(院长/副院长)',
-- repair_order 表（原 assignee_id 之前）
  approver_id BIGINT COMMENT '一级审批人(主任)',
  second_approver_id BIGINT COMMENT '二级审批人(院长/副院长)',
```

并在 `lab_business` 段末尾追加注释块（供已初始化库升级）：

```sql
-- 已初始化库升级（可选执行）：
-- ALTER TABLE reservation ADD COLUMN second_approver_id BIGINT NULL;
-- ALTER TABLE repair_order ADD COLUMN approver_id BIGINT NULL,
--                         ADD COLUMN second_approver_id BIGINT NULL;
```

- [ ] **步骤 2：Reservation 实体加字段**

```java
    private Long approverId;
    private Long secondApproverId;   // 新增：二级审批人(院长/副院长)
    private String rejectReason;
```

- [ ] **步骤 3：RepairOrder 实体加字段**

```java
    private String status;
    private Long approverId;          // 新增：一级审批人(主任)
    private Long secondApproverId;    // 新增：二级审批人(院长/副院长)
    private Long assigneeId;
```

- [ ] **步骤 4：ReservationMapper 冲突检测纳入 DIRECTOR_APPROVED**

```java
@Select("SELECT * FROM reservation WHERE room_id = #{roomId} AND status IN ('PENDING','DIRECTOR_APPROVED','APPROVED')")
List<Reservation> findActiveByRoom(Long roomId);
```

- [ ] **步骤 5：编译验证 + Commit**

运行（`lab-management-system` 目录）：
`.\mvnw.cmd -pl lab-business-service -am compile -q`
预期：BUILD SUCCESS

```bash
git add scripts/init-db.sql lab-business-service/src/main/java/com/gdcp/lab/business/entity/Reservation.java lab-business-service/src/main/java/com/gdcp/lab/business/entity/RepairOrder.java lab-business-service/src/main/java/com/gdcp/lab/business/mapper/ReservationMapper.java
git commit -m "feat: 预约/报修表增加二级审批人字段，冲突检测纳入待院长审批"
```

---

### 任务 2：预约二级审批（TDD）

**文件：**
- 修改：`lab-business-service/src/main/java/com/gdcp/lab/business/service/ReservationService.java`
- 修改：`lab-business-service/src/main/java/com/gdcp/lab/business/controller/ReservationController.java`
- 创建：`lab-business-service/src/main/java/com/gdcp/lab/business/common/ApprovalRoleUtil.java`
- 修改：`lab-business-service/src/test/java/com/gdcp/lab/business/service/ReservationServiceTest.java`

- [ ] **步骤 1：创建 ApprovalRoleUtil**

```java
package com.gdcp.lab.business.common;

public final class ApprovalRoleUtil {
    public static final String DIRECTOR = "DIRECTOR";
    public static final String DEAN = "DEAN";
    public static final String SYSTEM_ADMIN = "SYSTEM_ADMIN";

    private ApprovalRoleUtil() {}

    public static boolean canFirstApprove(String role) {
        return DIRECTOR.equals(role) || SYSTEM_ADMIN.equals(role);
    }

    public static boolean canSecondApprove(String role) {
        return DEAN.equals(role) || SYSTEM_ADMIN.equals(role);
    }
}
```

- [ ] **步骤 2：编写失败测试（改写 ReservationServiceTest）**

```java
package com.gdcp.lab.business.service;

import com.gdcp.lab.business.entity.Reservation;
import com.gdcp.lab.business.mapper.ReservationMapper;
import com.gdcp.lab.common.exception.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReservationServiceTest {
    private ReservationMapper mapper;
    private RabbitTemplate rabbit;
    private ReservationService service;

    @BeforeEach
    void setUp() {
        mapper = Mockito.mock(ReservationMapper.class);
        rabbit = Mockito.mock(RabbitTemplate.class);
        service = new ReservationService(mapper, rabbit);
    }

    private Reservation res(String st, String et, String status) {
        Reservation r = new Reservation();
        r.setId(1L); r.setRoomId(1L); r.setStatus(status);
        r.setStartTime(LocalDateTime.parse(st));
        r.setEndTime(LocalDateTime.parse(et));
        return r;
    }

    @Test
    void create_overlappingTime_throws() {
        Mockito.when(mapper.findActiveByRoom(1L)).thenReturn(List.of(
                res("2026-09-08T09:00:00", "2026-09-08T11:00:00", "APPROVED")));
        Reservation newRes = res("2026-09-08T10:00:00", "2026-09-08T12:00:00", "PENDING");
        assertThrows(BizException.class, () -> service.create(newRes, 5L));
    }

    @Test
    void approve_shortDuration_byDirector_becomesApproved() {
        Reservation r = res("2026-09-08T09:00:00", "2026-09-08T17:00:00", "PENDING");
        Mockito.when(mapper.selectById(1L)).thenReturn(r);
        service.approve(1L, 9L, "DIRECTOR");
        assertEquals("APPROVED", r.getStatus());
        assertEquals(9L, r.getApproverId());
        verify(rabbit).convertAndSend(any(), anyString(), any());
    }

    @Test
    void approve_longDuration_byDirector_becomesDirectorApproved() {
        Reservation r = res("2026-09-08T09:00:00", "2026-09-09T10:00:00", "PENDING"); // 25h
        Mockito.when(mapper.selectById(1L)).thenReturn(r);
        service.approve(1L, 9L, "DIRECTOR");
        assertEquals("DIRECTOR_APPROVED", r.getStatus());
        assertEquals(9L, r.getApproverId());
        verify(rabbit, never()).convertAndSend(any(), anyString(), any());
    }

    @Test
    void approve_directorApproved_byDean_becomesApproved() {
        Reservation r = res("2026-09-08T09:00:00", "2026-09-09T10:00:00", "DIRECTOR_APPROVED");
        Mockito.when(mapper.selectById(1L)).thenReturn(r);
        service.approve(1L, 10L, "DEAN");
        assertEquals("APPROVED", r.getStatus());
        assertEquals(10L, r.getSecondApproverId());
        verify(rabbit).convertAndSend(any(), anyString(), any());
    }

    @Test
    void approve_pending_byTeacher_throws() {
        Reservation r = res("2026-09-08T09:00:00", "2026-09-08T17:00:00", "PENDING");
        Mockito.when(mapper.selectById(1L)).thenReturn(r);
        assertThrows(BizException.class, () -> service.approve(1L, 8L, "TEACHER"));
    }

    @Test
    void approve_directorApproved_byDirector_throws() {
        Reservation r = res("2026-09-08T09:00:00", "2026-09-09T10:00:00", "DIRECTOR_APPROVED");
        Mockito.when(mapper.selectById(1L)).thenReturn(r);
        assertThrows(BizException.class, () -> service.approve(1L, 9L, "DIRECTOR"));
    }

    @Test
    void reject_pending_byDirector_setsRejected() {
        Reservation r = res("2026-09-08T09:00:00", "2026-09-08T17:00:00", "PENDING");
        Mockito.when(mapper.selectById(1L)).thenReturn(r);
        service.reject(1L, 9L, "DIRECTOR", "时间冲突");
        assertEquals("REJECTED", r.getStatus());
        assertEquals("时间冲突", r.getRejectReason());
        assertEquals(9L, r.getApproverId());
    }

    @Test
    void reject_directorApproved_byDean_setsRejected() {
        Reservation r = res("2026-09-08T09:00:00", "2026-09-09T10:00:00", "DIRECTOR_APPROVED");
        Mockito.when(mapper.selectById(1L)).thenReturn(r);
        service.reject(1L, 10L, "DEAN", "经费不足");
        assertEquals("REJECTED", r.getStatus());
        assertEquals(10L, r.getSecondApproverId());
    }

    @Test
    void approve_alreadyApproved_throws() {
        Reservation r = res("2026-09-08T09:00:00", "2026-09-08T10:00:00", "APPROVED");
        Mockito.when(mapper.selectById(1L)).thenReturn(r);
        assertThrows(BizException.class, () -> service.approve(1L, 9L, "DIRECTOR"));
    }
}
```

注意：原 `create_noConflict_saves` 用例因 `service` 构造签名不变仍适用，保留（`create` 方法签名不变）。

- [ ] **步骤 3：运行测试验证失败**

`.\mvnw.cmd -pl lab-business-service -am test -Dtest=ReservationServiceTest -q`
预期：编译失败（`approve(id, role)` 签名不匹配 / `secondApproverId` 属性缺失等）

- [ ] **步骤 4：实现预约二级审批（ReservationService）**

```java
import com.gdcp.lab.business.common.ApprovalRoleUtil;
import java.time.Duration;

public void approve(Long id, Long approverId, String approverRole) {
    Reservation r = reservationMapper.selectById(id);
    if (r == null) {
        throw new BizException("预约不存在");
    }
    if ("PENDING".equals(r.getStatus())) {
        if (!ApprovalRoleUtil.canFirstApprove(approverRole)) {
            throw new BizException("无权限审批");
        }
        r.setApproverId(approverId);
        if (Duration.between(r.getStartTime(), r.getEndTime()).toHours() >= 24) {
            r.setStatus("DIRECTOR_APPROVED");
            reservationMapper.updateById(r);
        } else {
            r.setStatus("APPROVED");
            reservationMapper.updateById(r);
            publish("lab.stats.reservation.approved", r, "APPROVED");
        }
    } else if ("DIRECTOR_APPROVED".equals(r.getStatus())) {
        if (!ApprovalRoleUtil.canSecondApprove(approverRole)) {
            throw new BizException("无权限审批");
        }
        r.setSecondApproverId(approverId);
        r.setStatus("APPROVED");
        reservationMapper.updateById(r);
        publish("lab.stats.reservation.approved", r, "APPROVED");
    } else {
        throw new BizException("当前状态不可审批");
    }
}

public void reject(Long id, Long approverId, String approverRole, String reason) {
    Reservation r = reservationMapper.selectById(id);
    if (r == null) {
        throw new BizException("预约不存在");
    }
    if ("PENDING".equals(r.getStatus())) {
        if (!ApprovalRoleUtil.canFirstApprove(approverRole)) {
            throw new BizException("无权限审批");
        }
        r.setApproverId(approverId);
    } else if ("DIRECTOR_APPROVED".equals(r.getStatus())) {
        if (!ApprovalRoleUtil.canSecondApprove(approverRole)) {
            throw new BizException("无权限审批");
        }
        r.setSecondApproverId(approverId);
    } else {
        throw new BizException("当前状态不可审批");
    }
    r.setStatus("REJECTED");
    r.setRejectReason(reason);
    reservationMapper.updateById(r);
}
```

删除原 `mustPending` 私有方法（不再使用）；保留 `create/hasConflict/cancel/complete/list/publish`。

- [ ] **步骤 5：更新 ReservationController 传入角色**

```java
@PostMapping("/{id}/approve")
public Result<Void> approve(@PathVariable Long id,
        @RequestHeader(value = "X-User-Id", required = false) Long userId,
        @RequestHeader(value = "X-User-Role", required = false) String userRole) {
    reservationService.approve(id, userId == null ? 0L : userId, userRole);
    return Result.ok(null);
}

@PostMapping("/{id}/reject")
public Result<Void> reject(@PathVariable Long id, @RequestParam String reason,
        @RequestHeader(value = "X-User-Id", required = false) Long userId,
        @RequestHeader(value = "X-User-Role", required = false) String userRole) {
    reservationService.reject(id, userId == null ? 0L : userId, userRole, reason);
    return Result.ok(null);
}
```

- [ ] **步骤 6：运行测试验证通过**

`.\mvnw.cmd -pl lab-business-service -am test -Dtest=ReservationServiceTest -q`
预期：BUILD SUCCESS（全部用例 PASS）

- [ ] **步骤 7：Commit**

```bash
git add lab-business-service/src/main/java/com/gdcp/lab/business/common/ApprovalRoleUtil.java lab-business-service/src/main/java/com/gdcp/lab/business/service/ReservationService.java lab-business-service/src/main/java/com/gdcp/lab/business/controller/ReservationController.java lab-business-service/src/test/java/com/gdcp/lab/business/service/ReservationServiceTest.java
git commit -m "feat: 预约二级审批（主任→院长），时长≥24h 需两级"
```

---

### 任务 3：报修二级审批（TDD）

**文件：**
- 修改：`lab-business-service/src/main/java/com/gdcp/lab/business/service/RepairService.java`
- 修改：`lab-business-service/src/main/java/com/gdcp/lab/business/controller/RepairController.java`
- 修改：`lab-business-service/src/test/java/com/gdcp/lab/business/service/RepairServiceTest.java`

- [ ] **步骤 1：编写失败测试（改写 RepairServiceTest）**

```java
package com.gdcp.lab.business.service;

import com.gdcp.lab.business.entity.RepairOrder;
import com.gdcp.lab.business.mapper.RepairOrderMapper;
import com.gdcp.lab.common.exception.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.junit.jupiter.api.Assertions.*;

class RepairServiceTest {
    private RepairOrderMapper mapper;
    private RepairService service;

    @BeforeEach
    void setUp() {
        mapper = Mockito.mock(RepairOrderMapper.class);
        service = new RepairService(mapper, Mockito.mock(RabbitTemplate.class));
    }

    private RepairOrder order(String status) {
        RepairOrder o = new RepairOrder();
        o.setId(1L); o.setDeviceId(2L); o.setStatus(status);
        return o;
    }

    @Test
    void approve_pending_byDirector_becomesDirectorApproved() {
        RepairOrder o = order("PENDING");
        Mockito.when(mapper.selectById(1L)).thenReturn(o);
        service.approve(1L, 9L, "DIRECTOR");
        assertEquals("DIRECTOR_APPROVED", o.getStatus());
        assertEquals(9L, o.getApproverId());
        Mockito.verify(mapper).updateById(o);
    }

    @Test
    void approve_directorApproved_byDean_becomesApproved() {
        RepairOrder o = order("DIRECTOR_APPROVED");
        Mockito.when(mapper.selectById(1L)).thenReturn(o);
        service.approve(1L, 10L, "DEAN");
        assertEquals("APPROVED", o.getStatus());
        assertEquals(10L, o.getSecondApproverId());
        Mockito.verify(mapper).updateById(o);
    }

    @Test
    void approve_pending_byTeacher_throws() {
        RepairOrder o = order("PENDING");
        Mockito.when(mapper.selectById(1L)).thenReturn(o);
        assertThrows(BizException.class, () -> service.approve(1L, 8L, "TEACHER"));
    }

    @Test
    void reject_pending_byDirector_setsRejected() {
        RepairOrder o = order("PENDING");
        Mockito.when(mapper.selectById(1L)).thenReturn(o);
        service.reject(1L, 9L, "DIRECTOR", "无需维修");
        assertEquals("REJECTED", o.getStatus());
        assertEquals("无需维修", o.getResult());
    }

    @Test
    void reject_directorApproved_byDean_setsRejected() {
        RepairOrder o = order("DIRECTOR_APPROVED");
        Mockito.when(mapper.selectById(1L)).thenReturn(o);
        service.reject(1L, 10L, "DEAN", "预算不足");
        assertEquals("REJECTED", o.getStatus());
        assertEquals(10L, o.getSecondApproverId());
    }

    @Test
    void assign_requiresApproved() {
        RepairOrder o = order("APPROVED");
        Mockito.when(mapper.selectById(1L)).thenReturn(o);
        service.assign(1L, 7L);
        assertEquals("ASSIGNED", o.getStatus());
        assertEquals(7L, o.getAssigneeId());
        Mockito.verify(mapper).updateById(o);
    }

    @Test
    void assign_fromPending_throws() {
        RepairOrder o = order("PENDING");
        Mockito.when(mapper.selectById(1L)).thenReturn(o);
        assertThrows(BizException.class, () -> service.assign(1L, 7L));
    }

    @Test
    void finish_requiresAssigned() {
        RepairOrder o = order("PENDING");
        Mockito.when(mapper.selectById(1L)).thenReturn(o);
        assertThrows(BizException.class, () -> service.finish(1L, "已更换主板"));
    }

    @Test
    void verify_completedToVerified() {
        RepairOrder o = order("COMPLETED");
        Mockito.when(mapper.selectById(1L)).thenReturn(o);
        service.verify(1L);
        assertEquals("VERIFIED", o.getStatus());
        Mockito.verify(mapper).updateById(o);
    }
}
```

- [ ] **步骤 2：运行测试验证失败**

`.\mvnw.cmd -pl lab-business-service -am test -Dtest=RepairServiceTest -q`
预期：编译失败（`approve` 不存在、`reject` 签名不匹配）

- [ ] **步骤 3：实现报修二级审批（RepairService）**

```java
public void approve(Long id, Long approverId, String approverRole) {
    RepairOrder o = orderMapper.selectById(id);
    if (o == null) throw new BizException("工单不存在");
    if ("PENDING".equals(o.getStatus())) {
        if (!ApprovalRoleUtil.canFirstApprove(approverRole)) throw new BizException("无权限审批");
        o.setApproverId(approverId);
        o.setStatus("DIRECTOR_APPROVED");
        orderMapper.updateById(o);
    } else if ("DIRECTOR_APPROVED".equals(o.getStatus())) {
        if (!ApprovalRoleUtil.canSecondApprove(approverRole)) throw new BizException("无权限审批");
        o.setSecondApproverId(approverId);
        o.setStatus("APPROVED");
        orderMapper.updateById(o);
    } else {
        throw new BizException("当前状态不可审批");
    }
}

public void reject(Long id, Long approverId, String approverRole, String reason) {
    RepairOrder o = orderMapper.selectById(id);
    if (o == null) throw new BizException("工单不存在");
    if ("PENDING".equals(o.getStatus())) {
        if (!ApprovalRoleUtil.canFirstApprove(approverRole)) throw new BizException("无权限审批");
        o.setApproverId(approverId);
    } else if ("DIRECTOR_APPROVED".equals(o.getStatus())) {
        if (!ApprovalRoleUtil.canSecondApprove(approverRole)) throw new BizException("无权限审批");
        o.setSecondApproverId(approverId);
    } else {
        throw new BizException("当前状态不可审批");
    }
    o.setStatus("REJECTED");
    o.setResult(reason);
    orderMapper.updateById(o);
}
```

同时：`assign` 中 `must(id, "PENDING")` 改为 `must(id, "APPROVED")`；删除原三参数 `reject(Long, String)`；`import com.gdcp.lab.business.common.ApprovalRoleUtil;`

- [ ] **步骤 4：更新 RepairController**

```java
@PostMapping("/{id}/approve")
public Result<Void> approve(@PathVariable Long id,
        @RequestHeader(value = "X-User-Id", required = false) Long userId,
        @RequestHeader(value = "X-User-Role", required = false) String userRole) {
    repairService.approve(id, userId == null ? 0L : userId, userRole);
    return Result.ok(null);
}

@PostMapping("/{id}/reject")
public Result<Void> reject(@PathVariable Long id, @RequestParam String reason,
        @RequestHeader(value = "X-User-Id", required = false) Long userId,
        @RequestHeader(value = "X-User-Role", required = false) String userRole) {
    repairService.reject(id, userId == null ? 0L : userId, userRole, reason);
    return Result.ok(null);
}
```

- [ ] **步骤 5：运行测试验证通过**

`.\mvnw.cmd -pl lab-business-service -am test -Dtest=RepairServiceTest -q`
预期：BUILD SUCCESS

- [ ] **步骤 6：Commit**

```bash
git add lab-business-service/src/main/java/com/gdcp/lab/business/service/RepairService.java lab-business-service/src/main/java/com/gdcp/lab/business/controller/RepairController.java lab-business-service/src/test/java/com/gdcp/lab/business/service/RepairServiceTest.java
git commit -m "feat: 报修二级审批（主任→院长），派单前置状态改为已批准"
```

---

### 任务 4：耗材新增/删除（TDD）

**文件：**
- 修改：`lab-resource-service/src/main/java/com/gdcp/lab/resource/service/ResourceService.java`
- 修改：`lab-resource-service/src/main/java/com/gdcp/lab/resource/controller/ResourceController.java`
- 修改：`lab-resource-service/src/test/java/com/gdcp/lab/resource/service/ResourceServiceTest.java`

- [ ] **步骤 1：编写失败测试（追加到 ResourceServiceTest）**

```java
    @Test
    void createMaterial_duplicateCode_throws() {
        Material m = new Material();
        m.setCode("M001");
        Mockito.when(materialMapper.selectCount(Mockito.any())).thenReturn(1L);
        assertThrows(BizException.class, () -> service.createMaterial(m));
    }

    @Test
    void createMaterial_success_inserts() {
        Material m = new Material();
        m.setCode("M002"); m.setName("鼠标"); m.setStock(10); m.setWarnThreshold(2);
        Mockito.when(materialMapper.selectCount(Mockito.any())).thenReturn(0L);
        service.createMaterial(m);
        assertEquals(0, m.getStock() == null ? 0 : m.getStock());
        Mockito.verify(materialMapper).insert(m);
    }

    @Test
    void deleteMaterial_withRecords_throws() {
        MaterialRecordMapper recordMapper = Mockito.mock(MaterialRecordMapper.class);
        ResourceService svc = new ResourceService(null, null, materialMapper, recordMapper);
        Material m = new Material();
        m.setId(1L);
        Mockito.when(materialMapper.selectById(1L)).thenReturn(m);
        Mockito.when(recordMapper.selectCount(Mockito.any())).thenReturn(3L);
        assertThrows(BizException.class, () -> svc.deleteMaterial(1L));
    }

    @Test
    void deleteMaterial_withoutRecords_deletes() {
        MaterialRecordMapper recordMapper = Mockito.mock(MaterialRecordMapper.class);
        ResourceService svc = new ResourceService(null, null, materialMapper, recordMapper);
        Material m = new Material();
        m.setId(1L);
        Mockito.when(materialMapper.selectById(1L)).thenReturn(m);
        Mockito.when(recordMapper.selectCount(Mockito.any())).thenReturn(0L);
        svc.deleteMaterial(1L);
        Mockito.verify(materialMapper).deleteById(1L);
    }
```

注意：原 `setUp()` 中 `service = new ResourceService(null, null, materialMapper, Mockito.mock(MaterialRecordMapper.class));` —— 删除耗材用例需自行构造 `svc`（以便断言 recordMapper），保持不变。

- [ ] **步骤 2：运行测试验证失败**

`.\mvnw.cmd -pl lab-resource-service -am test -Dtest=ResourceServiceTest -q`
预期：编译失败（`createMaterial` / `deleteMaterial` 不存在）

- [ ] **步骤 3：实现耗材增删（ResourceService）**

```java
public Material createMaterial(Material m) {
    if (m.getCode() == null || m.getCode().isBlank()) throw new BizException("编码不能为空");
    if (m.getName() == null || m.getName().isBlank()) throw new BizException("名称不能为空");
    LambdaQueryWrapper<Material> qw = new LambdaQueryWrapper<>();
    qw.eq(Material::getCode, m.getCode());
    if (materialMapper.selectCount(qw) > 0) {
        throw new BizException("耗材编码已存在: " + m.getCode());
    }
    if (m.getStock() == null) m.setStock(0);
    if (m.getWarnThreshold() == null) m.setWarnThreshold(0);
    materialMapper.insert(m);
    return m;
}

public void deleteMaterial(Long id) {
    if (materialMapper.selectById(id) == null) {
        throw new BizException("耗材不存在");
    }
    LambdaQueryWrapper<MaterialRecord> qw = new LambdaQueryWrapper<>();
    qw.eq(MaterialRecord::getMaterialId, id);
    if (recordMapper.selectCount(qw) > 0) {
        throw new BizException("该耗材已有出入库记录，不可删除");
    }
    materialMapper.deleteById(id);
}
```

- [ ] **步骤 4：更新 ResourceController**

```java
@PostMapping("/materials")
public Result<Material> createMaterial(@RequestBody Material m) {
    return Result.ok(resourceService.createMaterial(m));
}

@DeleteMapping("/materials/{id}")
public Result<Void> deleteMaterial(@PathVariable Long id) {
    resourceService.deleteMaterial(id);
    return Result.ok(null);
}
```

- [ ] **步骤 5：运行测试验证通过**

`.\mvnw.cmd -pl lab-resource-service -am test -Dtest=ResourceServiceTest -q`
预期：BUILD SUCCESS

- [ ] **步骤 6：Commit**

```bash
git add lab-resource-service/src/main/java/com/gdcp/lab/resource/service/ResourceService.java lab-resource-service/src/main/java/com/gdcp/lab/resource/controller/ResourceController.java lab-resource-service/src/test/java/com/gdcp/lab/resource/service/ResourceServiceTest.java
git commit -m "feat: 耗材新增与删除（有出入库记录禁止删除）"
```

---

### 任务 5：前端 API 与角色映射

**文件：**
- 修改：`frontend/src/api/business.ts`
- 修改：`frontend/src/api/resource.ts`
- 修改：`frontend/src/views/SystemUsers.vue`

- [ ] **步骤 1：business.ts 增加 approveRepair**

```ts
export const listRepairs = (params?: any) => request.get('/repairs', { params })
export const createRepair = (data: any) => request.post('/repairs', data)
export const approveRepair = (id: number) => request.post(`/repairs/${id}/approve`)
export const assignRepair = (id: number, assigneeId: number) =>
  request.post(`/repairs/${id}/assign`, null, { params: { assigneeId } })
```

- [ ] **步骤 2：resource.ts 增加耗材增删**

```ts
export const listMaterials = (params?: any) => request.get('/materials', { params })
export const createMaterial = (data: any) => request.post('/materials', data)
export const deleteMaterial = (id: number) => request.delete(`/materials/${id}`)
export const stockMaterial = (id: number, type: string, quantity: number) =>
  request.post(`/materials/${id}/stock`, null, { params: { type, quantity } })
```

- [ ] **步骤 3：SystemUsers.vue 角色扩展**

角色下拉追加：

```html
<el-option value="DIRECTOR" label="主任" />
<el-option value="DEAN" label="院长/副院长" />
```

映射表追加：

```ts
const roleTextMap: Record<string, string> = {
  SYSTEM_ADMIN: '系统管理员',
  LAB_ADMIN: '实验室管理员',
  DIRECTOR: '主任',
  DEAN: '院长/副院长',
  TEACHER: '教师',
  STUDENT: '学生'
}
const roleTagMap: Record<string, 'primary' | 'success' | 'warning' | 'info' | 'danger'> = {
  SYSTEM_ADMIN: 'danger',
  LAB_ADMIN: 'warning',
  DIRECTOR: 'primary',
  DEAN: 'danger',
  TEACHER: 'primary',
  STUDENT: 'success'
}
```

- [ ] **步骤 4：构建验证**

`frontend` 目录：`npm run build`
预期：BUILD SUCCESS（vue-tsc 类型检查通过）

- [ ] **步骤 5：Commit**

```bash
git add frontend/src/api/business.ts frontend/src/api/resource.ts frontend/src/views/SystemUsers.vue
git commit -m "feat: 前端增加报修审批、耗材增删 API 与主任/院长角色"
```

---

### 任务 6：预约页面二级审批

**文件：**
- 修改：`frontend/src/views/Reservations.vue`

- [ ] **步骤 1：状态与角色逻辑**

`isAdmin` 拆分为管理/审批两套判断：

```ts
const role = computed(() => store.role)
const isAdmin = computed(() => ['LAB_ADMIN', 'SYSTEM_ADMIN'].includes(role.value))
const canApprove = computed(() => ['DIRECTOR', 'DEAN', 'SYSTEM_ADMIN'].includes(role.value))
const canFirstApprove = computed(() => ['DIRECTOR', 'SYSTEM_ADMIN'].includes(role.value))
const canSecondApprove = computed(() => ['DEAN', 'SYSTEM_ADMIN'].includes(role.value))
```

状态文案/标签追加：

```ts
function statusText(s: string) {
  const map: Record<string, string> = {
    PENDING: '待主任审批',
    DIRECTOR_APPROVED: '待院长审批',
    APPROVED: '已通过',
    REJECTED: '已驳回',
    CANCELLED: '已取消',
    COMPLETED: '已完成'
  }
  return map[s] || s
}
function statusTag(s: string) {
  const map: Record<string, 'warning' | 'success' | 'danger' | 'info' | 'primary'> = {
    PENDING: 'warning',
    DIRECTOR_APPROVED: 'warning',
    APPROVED: 'success',
    REJECTED: 'danger',
    CANCELLED: 'info',
    COMPLETED: 'primary'
  }
  return map[s] || 'info'
}
```

- [ ] **步骤 2：审批中心按角色加载**

模板中 `v-if="isAdmin"` 的 tab 区改为 `v-if="isAdmin || canApprove"`（"审批中心"tab 对主任/院长也可见）；`rows` 计算属性中 `approval` 分支不变（仍用 `pendingReservations`）。

```ts
async function loadApproval() {
  if (canFirstApprove.value && canSecondApprove.value) {
    // SYSTEM_ADMIN：全部待批
    const [p1, p2] = await Promise.all([
      listReservations({ status: 'PENDING' }),
      listReservations({ status: 'DIRECTOR_APPROVED' })
    ])
    pendingReservations.value = [...(p1 as ReservationRow[]), ...(p2 as ReservationRow[])]
  } else if (canFirstApprove.value) {
    pendingReservations.value = (await listReservations({ status: 'PENDING' })) as ReservationRow[]
  } else if (canSecondApprove.value) {
    pendingReservations.value = (await listReservations({ status: 'DIRECTOR_APPROVED' })) as ReservationRow[]
  } else {
    pendingReservations.value = []
  }
}
```

`loadAll` 中 `isAdmin.value ? loadPending() : Promise.resolve()` 改为 `(isAdmin.value || canApprove.value) ? loadApproval() : Promise.resolve()`；删除原 `loadPending`。`watch(activeTab)` 同样改用 `loadApproval`。

- [ ] **步骤 3：审批按钮按角色显隐**

操作列替换为：

```html
<template v-if="isAdmin && activeTab === 'approval'">
  <!-- 原 LAB_ADMIN 无审批权限时按钮不再无条件显示 -->
</template>
<template v-if="canApproveRow(row)">
  <el-button size="small" type="success" @click="handleApprove(row)">通过</el-button>
  <el-button size="small" type="danger" plain @click="handleReject(row)">驳回</el-button>
</template>
```

```ts
function canApproveRow(row: ReservationRow) {
  if (!canApprove.value || activeTab.value !== 'approval') return false
  if (row.status === 'PENDING') return canFirstApprove.value
  if (row.status === 'DIRECTOR_APPROVED') return canSecondApprove.value
  return false
}
```

说明：原 `LAB_ADMIN` 走"审批中心"tab 但无审批权限 → `canApproveRow` 返回 false，不显示按钮；`SYSTEM_ADMIN` 两阶段均可批。

- [ ] **步骤 4：构建验证**

`frontend` 目录：`npm run build` → 预期 SUCCESS

- [ ] **步骤 5：Commit**

```bash
git add frontend/src/views/Reservations.vue
git commit -m "feat: 预约审批中心支持主任/院长分级审批"
```

---

### 任务 7：报修页面二级审批

**文件：**
- 修改：`frontend/src/views/Repairs.vue`

- [ ] **步骤 1：API 与角色逻辑**

```ts
import { approveRepair } from '../api/business'   // 追加到现有 import

const role = computed(() => store.role)
const isAdmin = computed(() => ['LAB_ADMIN', 'SYSTEM_ADMIN'].includes(role.value))
const canFirstApprove = computed(() => ['DIRECTOR', 'SYSTEM_ADMIN'].includes(role.value))
const canSecondApprove = computed(() => ['DEAN', 'SYSTEM_ADMIN'].includes(role.value))
```

状态文案/标签：

```ts
function statusText(s: string) {
  const map: Record<string, string> = {
    PENDING: '待主任审批',
    DIRECTOR_APPROVED: '待院长审批',
    APPROVED: '已批准待派单',
    ASSIGNED: '已派单',
    COMPLETED: '已完成',
    REJECTED: '已驳回',
    VERIFIED: '已验收'
  }
  return map[s] || s
}
function statusTag(s: string) {
  const map: Record<string, 'warning' | 'primary' | 'success' | 'danger' | 'info'> = {
    PENDING: 'warning',
    DIRECTOR_APPROVED: 'warning',
    APPROVED: 'success',
    ASSIGNED: 'primary',
    COMPLETED: 'success',
    REJECTED: 'danger',
    VERIFIED: 'info'
  }
  return map[s] || 'info'
}
```

- [ ] **步骤 2：操作列按状态/角色显隐**

替换操作列：

```html
<template #default="{ row }">
  <template v-if="row.status === 'PENDING' && canFirstApprove">
    <el-button size="small" type="success" @click="handleApprove(row)">通过</el-button>
    <el-button size="small" type="danger" plain @click="handleReject(row)">驳回</el-button>
  </template>
  <template v-else-if="row.status === 'DIRECTOR_APPROVED' && canSecondApprove">
    <el-button size="small" type="success" @click="handleApprove(row)">通过</el-button>
    <el-button size="small" type="danger" plain @click="handleReject(row)">驳回</el-button>
  </template>
  <template v-else-if="isAdmin">
    <el-button v-if="row.status === 'APPROVED'" size="small" type="primary" @click="openAssign(row)">派单</el-button>
    <el-button v-if="row.status === 'ASSIGNED'" size="small" type="success" @click="handleFinish(row)">完工</el-button>
    <el-button v-if="row.status === 'COMPLETED'" size="small" type="info" plain @click="handleVerify(row)">验收</el-button>
  </template>
</template>
```

新增处理函数：

```ts
async function handleApprove(row: RepairRow) {
  await approveRepair(row.id)
  ElMessage.success('已通过')
  await load()
}
```

（`handleReject` 已有，复用；驳回原因提示不变。）

- [ ] **步骤 3：构建验证**

`npm run build` → 预期 SUCCESS

- [ ] **步骤 4：Commit**

```bash
git add frontend/src/views/Repairs.vue
git commit -m "feat: 报修工单支持主任/院长分级审批，派单需已批准"
```

---

### 任务 8：耗材页面新增/删除

**文件：**
- 修改：`frontend/src/views/Materials.vue`

- [ ] **步骤 1：模板新增按钮与弹窗**

头部按钮区（搜索框右侧）追加：

```html
<el-button v-if="isAdmin" type="success" @click="openCreate">新增耗材</el-button>
```

操作列追加（入库/出库之前）：

```html
<el-button size="small" type="danger" plain @click="handleDelete(row)">删除</el-button>
```

新增对话框（复用现有库存对话框之后）：

```html
<el-dialog v-model="createVisible" title="新增耗材" width="460px">
  <el-form :model="createForm" label-width="80px">
    <el-form-item label="编码" required>
      <el-input v-model="createForm.code" placeholder="唯一编码，如 M001" />
    </el-form-item>
    <el-form-item label="名称" required>
      <el-input v-model="createForm.name" placeholder="耗材名称" />
    </el-form-item>
    <el-form-item label="规格">
      <el-input v-model="createForm.spec" placeholder="如 500g/瓶" />
    </el-form-item>
    <el-form-item label="单位">
      <el-input v-model="createForm.unit" placeholder="如 瓶/盒/个" />
    </el-form-item>
    <el-form-item label="初始库存">
      <el-input-number v-model="createForm.stock" :min="0" :max="1000000" style="width: 100%" />
    </el-form-item>
    <el-form-item label="预警阈值">
      <el-input-number v-model="createForm.warnThreshold" :min="0" :max="1000000" style="width: 100%" />
    </el-form-item>
  </el-form>
  <template #footer>
    <el-button @click="createVisible = false">取消</el-button>
    <el-button type="primary" :loading="creating" @click="handleCreate">确定</el-button>
  </template>
</el-dialog>
```

- [ ] **步骤 2：脚本逻辑**

```ts
import { listMaterials, stockMaterial, createMaterial, deleteMaterial } from '../api/resource'
import { ElMessage, ElMessageBox } from 'element-plus'

const createVisible = ref(false)
const creating = ref(false)
const createForm = ref({
  code: '', name: '', spec: '', unit: '',
  stock: 0, warnThreshold: 0
})

function openCreate() {
  createForm.value = { code: '', name: '', spec: '', unit: '', stock: 0, warnThreshold: 0 }
  createVisible.value = true
}

async function handleCreate() {
  if (!createForm.value.code || !createForm.value.name) {
    ElMessage.warning('请填写编码和名称')
    return
  }
  creating.value = true
  try {
    await createMaterial({ ...createForm.value })
    ElMessage.success('新增成功')
    createVisible.value = false
    await load()
  } finally {
    creating.value = false
  }
}

async function handleDelete(row: Material) {
  try {
    await ElMessageBox.confirm(`确定删除耗材「${row.name}」吗？已有出入库记录的耗材不可删除。`, '提示', { type: 'warning' })
  } catch {
    return
  }
  await deleteMaterial(row.id)
  ElMessage.success('已删除')
  await load()
}
```

- [ ] **步骤 3：构建验证**

`npm run build` → 预期 SUCCESS

- [ ] **步骤 4：Commit**

```bash
git add frontend/src/views/Materials.vue
git commit -m "feat: 耗材管理支持新增与删除"
```

---

### 任务 9：全量验证与提交

- [ ] **步骤 1：全量后端测试**

`.\mvnw.cmd -pl lab-user-service,lab-resource-service,lab-business-service,lab-report-service,lab-gateway -am test -q`
预期：BUILD SUCCESS（全部模块测试通过）

- [ ] **步骤 2：前端构建**

`frontend` 目录：`npm run build` → 预期 SUCCESS

- [ ] **步骤 3：打包全部服务**

`.\mvnw.cmd -pl lab-user-service,lab-resource-service,lab-business-service,lab-report-service,lab-gateway -am package -DskipTests -q`
预期：5 个新 jar 生成到各 `target/` 目录

- [ ] **步骤 4：提交全部变更**

```bash
git add -A
git commit -m "feat: 耗材增删 + 预约/报修二级审批（主任→院长）"
git push
```

- [ ] **步骤 5：运行级验收（需数据库升级列）**

1. 对运行中的 `lab_business` 库执行任务前置知识中的两条 ALTER 语句（或重新执行 `init-db.sql`）。
2. 通过用户管理创建 `director`（主任）、`dean`（院长/副院长）账号。
3. 登录系统走查：提交 ≥24h 预约 → 主任批 → 院长批 → 已通过；提交报修 → 主任批 → 院长批 → 派单 → 完工 → 验收；耗材新增/删除（删除有流水耗材应被拒）。
