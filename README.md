# 广东交通职业技术学院 实训室管理系统

基于 **Spring Cloud 微服务** + **Vue3** 的实训室综合管理系统，覆盖设备资产管理、实训室预约、报修工单、统计报表等核心业务场景。

## 技术栈

| 层级 | 技术 |
|------|------|
| 前端 | Vue3 · TypeScript · Vite · Element Plus · Pinia · ECharts |
| 网关 | Spring Cloud Gateway |
| 后端 | Spring Boot 2.7.18 · Spring Cloud 2021.0.9 · Spring Cloud Alibaba |
| 注册中心 | Nacos |
| 消息队列 | RabbitMQ |
| ORM | MyBatis-Plus |
| 数据库 | MySQL 5.7+ |
| 鉴权 | JWT |

## 系统架构

```
浏览器
   │
   ▼
Vue3 前端 (5173)
   │  /api/**
   ▼
Gateway 网关 (9000) ──── JWT 鉴权 + 路由转发
   │
   ├──► user-service    用户/角色/登录   (9100)
   ├──► resource-service 设备/实训室     (9200)
   ├──► business-service 预约/报修       (9300)
   └──► report-service   统计报表        (9400)
           │
           └── RabbitMQ 事件聚合 → Nacos 服务注册发现
```

## 模块说明

| 模块 | 端口 | 职责 |
|------|------|------|
| `lab-gateway` | 9000 | 统一入口、JWT 鉴权、路由转发 |
| `lab-user-service` | 9100 | 用户、角色、登录认证 |
| `lab-resource-service` | 9200 | 设备、实训室资产管理 |
| `lab-business-service` | 9300 | 预约、报修工单 |
| `lab-report-service` | 9400 | 统计数据与报表 |
| `lab-common` | - | 公共依赖（JWT 工具、统一返回等） |
| `frontend` | 5173 | Vue3 管理前端 |

## 环境要求

- JDK 11+
- Maven 3.6+（也可用项目内 `mvnw.cmd`）
- Node.js 16+ 与 npm
- MySQL 5.7+（本机 `127.0.0.1:3306`，账号 `root`）
- Nacos（默认安装在 `C:\nacos`）
- RabbitMQ（本机 Windows 服务）

## 快速开始（Windows）

### 1. 初始化数据库

创建 4 个业务库并导入表结构：

```
lab_user
lab_resource
lab_business
lab_report
```

执行根目录脚本 `scripts\init-db.sql` 初始化全部库表。

### 2. 启动中间件

- **Nacos**（注册中心）：`C:\nacos\nacos\bin\startup.cmd -m standalone`，访问 `http://localhost:8848/nacos`
- **RabbitMQ**：启动 Windows 服务 `RabbitMQ`，管理台 `http://localhost:15672`（默认 guest/guest）

> Nacos 首次启动需等待约 20~30 秒就绪，之后才能注册服务。

### 3. 一键启动

在项目根目录运行：

```
lab-management-system\scripts\start-all.cmd
```

脚本将依次执行：启动 Nacos → 启动 RabbitMQ → Maven 打包（产物已存在则跳过）→ 启动 5 个后端服务 → 启动前端。

停止全部服务：

```
lab-management-system\scripts\stop-all.cmd
```

### 4. 访问系统

| 入口 | 地址 |
|------|------|
| 管理系统 | http://localhost:5173 |
| 网关 | http://localhost:9000 |
| Nacos 控制台 | http://localhost:8848/nacos |
| RabbitMQ 管理台 | http://localhost:15672 |

### 5. 默认账号

| 账号 | 密码 | 角色 |
|------|------|------|
| `admin` | `123456` | 系统管理员（SYSTEM_ADMIN） |

## 手动构建与启动（可选）

```bash
# 后端打包
cd lab-management-system
mvnw.cmd -f pom.xml -pl lab-user-service,lab-resource-service,lab-business-service,lab-report-service,lab-gateway -am package -DskipTests

# 依次启动服务（或直接 java -jar 各 target 目录下的 jar）
java -jar lab-user-service/target/lab-user-service-1.0.0.jar
java -jar lab-resource-service/target/lab-resource-service-1.0.0.jar
java -jar lab-business-service/target/lab-business-service-1.0.0.jar
java -jar lab-report-service/target/lab-report-service-1.0.0.jar
java -jar lab-gateway/target/lab-gateway-1.0.0.jar

# 前端
cd lab-management-system/frontend
npm install
npm run dev
```

## 主要功能

- **设备管理**：实训室设备台账、状态维护
- **预约管理**：实训室预约与审批
- **报修工单**：设备报修、指派、完工闭环
- **统计报表**：使用率、报修趋势等数据图表
- **RBAC 权限**：系统管理员 / 实验室管理员 / 教师 / 学生

## 常见问题

**启动后登录提示「登录已过期，请重新登录」**

多为服务未全部就绪所致。请确认 Nacos（8848）已启动完成，5 个后端服务窗口无报错，再刷新浏览器登录。

**服务启动报「连接注册中心失败」**

Nacos 需要约 20~30 秒完成初始化，服务需在 Nacos 就绪后再启动（`start-all.cmd` 已处理该时序）。
