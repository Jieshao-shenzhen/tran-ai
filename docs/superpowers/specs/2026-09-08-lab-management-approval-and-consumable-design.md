# 实训室管理系统改良 — 耗材增删 + 二级审批 设计规格

- 日期：2026-09-08
- 状态：已获用户批准

## 1. 背景与目标

在现有系统（预约单级审批、报修工单流程、耗材仅出入库）基础上做改良：

1. **耗材新增/删除**：补齐耗材台账维护能力。
2. **二级审批**：预约与报修改为「主任 → 院长/副院长」两级审批。
3. **时长分级**：预约时长 <24h 仅主任审批；≥24h 需主任 + 院长/副院长 两级审批。

## 2. 角色扩展

| 角色编码 | 名称 | 新增/已有 | 职责 |
|---------|------|-----------|------|
| `SYSTEM_ADMIN` | 系统管理员 | 已有 | 全权限，审批兜底 |
| `LAB_ADMIN` | 实训室管理员 | 已有 | 设备/耗材台账维护 |
| `TEACHER` | 教师 | 已有 | 提交预约/报修 |
| `STUDENT` | 学生 | 已有 | 提交预约 |
| `DIRECTOR` | 主任 | **新增** | 一级审批 |
| `DEAN` | 院长/副院长 | **新增** | 二级审批 |

- 用户创建/导入时角色选项增加 `DIRECTOR`、`DEAN`。
- 审批权限：一级阶段允许 `DIRECTOR / SYSTEM_ADMIN`；二级阶段允许 `DEAN / SYSTEM_ADMIN`。

## 3. 耗材新增/删除

### 3.1 后端接口（lab-resource-service）

| 接口 | 说明 |
|------|------|
| `POST /api/v1/materials` | 新增耗材：code/name/spec/unit/stock/warnThreshold |
| `DELETE /api/v1/materials/{id}` | 删除耗材 |

删除规则（已确认）：
- 该耗材在 `material_record` 中存在任何出入库流水 → **禁止删除**，抛 `BizException("该耗材已有出入库记录，不可删除")`。
- 无流水 → 允许删除（无论当前库存是否为零）。

新增校验：`code` 唯一（数据库唯一约束兜底 + 服务层查重提示友好错误）。

### 3.2 前端（Materials.vue）

- 卡片头部「新增」按钮 → 表单弹窗（编码/名称/规格/单位/初始库存/预警阈值）。
- 操作列增加「删除」按钮 → `ElMessageBox.confirm` → 调用删除接口，报错提示由拦截器统一处理。
- 权限：操作列 `isAdmin`（`LAB_ADMIN / SYSTEM_ADMIN`）保持不变。

## 4. 预约二级审批

### 4.1 状态机

```
PENDING（待主任审批）
  ├─ 主任通过 + 时长 < 24h ──────────────→ APPROVED ✅
  ├─ 主任通过 + 时长 ≥ 24h ──────────────→ DIRECTOR_APPROVED（待院长审批）
  │                                          ├─ 院长通过 → APPROVED ✅
  │                                          └─ 院长驳回 → REJECTED
  └─ 主任驳回 → REJECTED
```

- 时长 = `endTime - startTime`，`≥24h` 视为「2 天以上」需二级审批。
- 冲突检测、取消、完成逻辑保持不变；`complete` 仍要求 `APPROVED`。
- MQ 统计事件：仅在最终 `APPROVED` 时发 `lab.stats.reservation.approved`；`COMPLETED` 照旧。

### 4.2 表变更（lab_business.reservation）

复用现有 `approver_id` 作为一级（主任）审批人，仅新增一列：

```sql
ALTER TABLE reservation
  ADD COLUMN second_approver_id BIGINT NULL COMMENT '二级审批人(院长/副院长)';
```

- `approver_id`：一级审批人（主任），驳回时同样写入驳回人。
- `second_approver_id`：二级审批人（院长/副院长）。

### 4.3 接口行为（保留原有 URL）

| 接口 | 新行为 |
|------|--------|
| `POST /{id}/approve` | 主任批 PENDING →（<24h→APPROVED / ≥24h→DIRECTOR_APPROVED）；院长批 DIRECTOR_APPROVED → APPROVED。写入对应审批人字段 |
| `POST /{id}/reject` | PENDING 由主任驳；DIRECTOR_APPROVED 由院长驳 → REJECTED，写审批人 + 原因 |

## 5. 报修二级审批

### 5.1 状态机

```
PENDING（待主任审批）
  → 主任通过 → DIRECTOR_APPROVED（待院长审批）
  → 院长通过 → APPROVED（待派单）
  → 派单 ASSIGNED → 维修 COMPLETED → 验收 VERIFIED
  任一审批环节驳回 → REJECTED
```

- 报修无时长概念，统一两级审批。
- `assign` 前置状态由 `PENDING` 改为 `APPROVED`（院长批准后方可派单）。

### 5.2 表变更（lab_business.repair_order）

新增两列（与 reservation 命名一致）：

```sql
ALTER TABLE repair_order
  ADD COLUMN approver_id BIGINT NULL COMMENT '一级审批人(主任)',
  ADD COLUMN second_approver_id BIGINT NULL COMMENT '二级审批人(院长/副院长)';
```

### 5.3 接口行为

| 接口 | 新行为 |
|------|--------|
| `POST /{id}/approve` | 新增接口：主任批 PENDING → DIRECTOR_APPROVED；院长批 DIRECTOR_APPROVED → APPROVED |
| `POST /{id}/reject` | PENDING 主任驳；DIRECTOR_APPROVED 院长驳 → REJECTED |
| `POST /{id}/assign` | 仅 `APPROVED` 状态可派单 |
| `finish / verify` | 不变 |

## 6. 审批角色校验

- 网关注入 `X-User-Id` / `X-User-Role` 请求头。
- `ReservationService` / `RepairService` 校验操作者角色：
  - 审批 `PENDING`：角色 ∈ {DIRECTOR, SYSTEM_ADMIN}，否则抛「无权限审批」。
  - 审批 `DIRECTOR_APPROVED`：角色 ∈ {DEAN, SYSTEM_ADMIN}，否则抛「无权限审批」。

## 7. 前端变更

| 文件 | 变更 |
|------|------|
| `frontend/src/api/business.ts` | 增加 `approveRepair(id)`；`assignRepair` 语义不变 |
| `frontend/src/api/resource.ts` | 增加 `createMaterial(data)`、`deleteMaterial(id)` |
| `frontend/src/views/Reservations.vue` | 审批中心按角色过滤：`DIRECTOR` 看 `PENDING`、`DEAN` 看 `DIRECTOR_APPROVED`、`SYSTEM_ADMIN` 看全部待批（PENDING + DIRECTOR_APPROVED）；状态文案新增「待主任审批/待院长审批/主任已批待院长」；操作按钮按角色显隐 |
| `frontend/src/views/Repairs.vue` | 同上：主任/院长审批入口 + 派单仅在 `APPROVED` 显示 |
| `frontend/src/views/Materials.vue` | 新增/删除按钮 + 弹窗 |
| `frontend/src/views/SystemUsers.vue` | 角色下拉增加 DIRECTOR/DEAN |
| `frontend/src/router/index.ts` | 审批相关页面 roles 增加 DIRECTOR/DEAN（materials 保持 LAB_ADMIN/SYSTEM_ADMIN） |

## 8. 种子数据

- `DataInitializer` 保持 admin 不变。
- 验收账号：`dean / 123456`（DEAN）、`director / 123456`（DIRECTOR）——由验收步骤通过用户管理创建，或写入 init-db.sql 供手工初始化。

## 9. 测试策略

- 后端单测（business-service）：
  - 预约：<24h 主任批 → APPROVED；≥24h 主任批 → DIRECTOR_APPROVED → 院长批 → APPROVED；各阶段驳回；角色越权抛错。
  - 报修：主任批 → DIRECTOR_APPROVED → 院长批 → APPROVED → assign 成功；PENDING 直接 assign 抛错。
  - 资源服务：新增耗材、删除有流水耗材被拒、删除无流水耗材成功。
- 冒烟/端到端：登录 → 创建主任/院长账号 → 提交 ≥24h 预约 → 主任批 → 院长批 → APPROVED；提交报修 → 主任批 → 院长批 → 派单 → 完成 → 验收；新增/删除耗材。
