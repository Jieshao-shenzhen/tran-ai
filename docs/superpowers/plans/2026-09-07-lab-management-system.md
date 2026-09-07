# 实训室管理系统实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 交付广东交通职业技术学院实训室管理系统：网关 + 4 个 Spring Boot 微服务 + Vue3 前端，支持实训室/设备/耗材台账、预约审批、报修维修、RBAC 权限、统计报表。

**架构：** Spring Cloud Gateway 统一入口并做 JWT 鉴权；4 个独立 Spring Boot 服务（用户/资源/业务/统计）注册到 Nacos，业务事件经 RabbitMQ 推送；每服务独立 MySQL schema；前端 Vite + Vue3 通过网关访问。

**技术栈：** Java 11、Spring Boot 2.7.18、Spring Cloud 2021.0.9、Spring Cloud Alibaba 2021.0.5.0、MyBatis-Plus 3.5.5、MySQL 8.0、Nacos 2.2.3、RabbitMQ、jjwt 0.11.5、EasyExcel 3.3.4、Maven Wrapper 3.2.0；前端 Vue3 + TypeScript + Vite 5 + Element Plus + Pinia + Vue Router 4 + axios。

---

## 文件结构（工程根目录 `lab-management-system/`）

```
lab-management-system/
├── pom.xml                        # 父 POM（依赖管理、统一版本）
├── lab-common/                    # 通用模块：统一返回、异常、JWT 工具、常量
├── lab-gateway/                   # 端口 9000：路由转发 + JWT 全局过滤器
├── lab-user-service/              # 端口 9100：登录/JWT、用户/角色/权限、Excel 导入、操作日志
├── lab-resource-service/          # 端口 9200：实训室、设备、耗材 CRUD + 出入库
├── lab-business-service/          # 端口 9300：预约/报修状态机、RabbitMQ 事件发布
├── lab-report-service/            # 端口 9400：RabbitMQ 事件消费、聚合统计、统计接口
├── frontend/                      # Vue3 + TS SPA
└── scripts/
    ├── init-db.sql                # 4 个 schema + 全部建表语句
    ├── start-all.cmd              # 一键启动
    └── stop-all.cmd               # 一键停止
```

Java 包根：`com.gdcp.lab.*`（各服务独立子包，如 `com.gdcp.lab.user`）。

---

## 任务 1：基础设施准备（Nacos、RabbitMQ、MySQL 初始化）

**文件：**
- 创建：`scripts/init-db.sql`

- [ ] **步骤 1：初始化数据库 schema 与建表**

运行：`mysql -uroot -p < scripts/init-db.sql`

```sql
CREATE DATABASE IF NOT EXISTS lab_user DEFAULT CHARACTER SET utf8mb4;
CREATE DATABASE IF NOT EXISTS lab_resource DEFAULT CHARACTER SET utf8mb4;
CREATE DATABASE IF NOT EXISTS lab_business DEFAULT CHARACTER SET utf8mb4;
CREATE DATABASE IF NOT EXISTS lab_report DEFAULT CHARACTER SET utf8mb4;

USE lab_user;
CREATE TABLE sys_user (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  username VARCHAR(64) NOT NULL UNIQUE,
  password VARCHAR(100) NOT NULL,
  real_name VARCHAR(64) NOT NULL,
  employee_no VARCHAR(32),
  phone VARCHAR(20),
  dept VARCHAR(64),
  role VARCHAR(32) NOT NULL,
  status TINYINT DEFAULT 1,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE sys_role (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  code VARCHAR(32) NOT NULL UNIQUE,
  name VARCHAR(64) NOT NULL
);
CREATE TABLE sys_user_role (
  user_id BIGINT NOT NULL,
  role_id BIGINT NOT NULL,
  PRIMARY KEY (user_id, role_id)
);
CREATE TABLE sys_menu (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  path VARCHAR(128), name VARCHAR(64), title VARCHAR(64),
  icon VARCHAR(64), parent_id BIGINT DEFAULT 0, sort INT DEFAULT 0
);
CREATE TABLE sys_role_menu (
  role_id BIGINT NOT NULL, menu_id BIGINT NOT NULL,
  PRIMARY KEY (role_id, menu_id)
);
CREATE TABLE sys_operation_log (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT, username VARCHAR(64), action VARCHAR(128),
  detail VARCHAR(512), created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

USE lab_resource;
CREATE TABLE lab_room (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  code VARCHAR(32) NOT NULL UNIQUE, name VARCHAR(128) NOT NULL,
  building VARCHAR(64), floor INT, capacity INT, type VARCHAR(32),
  manager_id BIGINT, status TINYINT DEFAULT 1, remark VARCHAR(255)
);
CREATE TABLE lab_device (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  code VARCHAR(32) NOT NULL UNIQUE, name VARCHAR(128) NOT NULL,
  category VARCHAR(32), room_id BIGINT, brand VARCHAR(64),
  model_no VARCHAR(64), status TINYINT DEFAULT 1,
  buy_date DATE, price DECIMAL(10,2)
);
CREATE TABLE material (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  code VARCHAR(32) NOT NULL UNIQUE, name VARCHAR(128) NOT NULL,
  spec VARCHAR(64), unit VARCHAR(16), stock INT DEFAULT 0,
  warn_threshold INT DEFAULT 0
);
CREATE TABLE material_record (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  material_id BIGINT NOT NULL, type VARCHAR(16) NOT NULL,
  quantity INT NOT NULL, operator_id BIGINT, created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

USE lab_business;
CREATE TABLE reservation (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  room_id BIGINT NOT NULL, applicant_id BIGINT NOT NULL,
  purpose VARCHAR(255), start_time DATETIME NOT NULL, end_time DATETIME NOT NULL,
  people_num INT, status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  approver_id BIGINT, reject_reason VARCHAR(255), created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE repair_order (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  device_id BIGINT NOT NULL, room_id BIGINT NOT NULL, reporter_id BIGINT NOT NULL,
  description VARCHAR(512) NOT NULL, status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  assignee_id BIGINT, result VARCHAR(512), completed_at DATETIME
);

USE lab_report;
CREATE TABLE stat_daily_room_usage (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  stat_date DATE NOT NULL, room_id BIGINT, usage_count INT DEFAULT 0,
  approved_count INT DEFAULT 0
);
CREATE TABLE stat_daily_repair (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  stat_date DATE NOT NULL, new_count INT DEFAULT 0, completed_count INT DEFAULT 0
);
```

- [ ] **步骤 2：安装并启动 RabbitMQ**

运行：
```powershell
winget install Erlang.ErlangOTP
winget install RabbitMQ.RabbitMQServer
& 'C:\Program Files\RabbitMQ Server\rabbitmq_server-*\sbin\rabbitmq-service.bat' install
& 'C:\Program Files\RabbitMQ Server\rabbitmq_server-*\sbin\rabbitmq-service.bat' start
```
预期：`rabbitmq-service.bat start` 无报错；`http://localhost:15672`（guest/guest）可打开管理台。

- [ ] **步骤 3：下载并启动 Nacos 单机版**

运行：
```powershell
Invoke-WebRequest -Uri https://github.com/alibaba/nacos/releases/download/2.2.3/nacos-server-2.2.3.zip -OutFile $env:TEMP\nacos.zip
Expand-Archive $env:TEMP\nacos.zip -DestinationPath C:\nacos -Force
cd C:\nacos\nacos\bin
.\startup.cmd -m standalone
```
预期：控制台输出 `Nacos started successfully`；`http://localhost:8848/nacos`（nacos/nacos）可打开。

- [ ] **步骤 4：验证 MySQL 连接**

运行：`mysql -uroot -p -e "SHOW DATABASES;"`
预期：输出 `lab_user`、`lab_resource`、`lab_business`、`lab_report` 四个库。

- [ ] **步骤 5：Commit**

```bash
git add scripts/init-db.sql
git commit -m "feat: 实训室系统数据库初始化脚本"
```

---

## 任务 2：父 POM、Maven Wrapper、lab-common 通用模块

**文件：**
- 创建：`lab-management-system/pom.xml`
- 创建：`lab-management-system/lab-common/pom.xml`
- 创建：`lab-common/src/main/java/com/gdcp/lab/common/result/Result.java`
- 创建：`lab-common/src/main/java/com/gdcp/lab/common/result/ResultCode.java`
- 创建：`lab-common/src/main/java/com/gdcp/lab/common/exception/BizException.java`
- 创建：`lab-common/src/main/java/com/gdcp/lab/common/exception/GlobalExceptionHandler.java`
- 创建：`lab-common/src/main/java/com/gdcp/lab/common/jwt/JwtUtil.java`
- 创建：`lab-common/src/main/java/com/gdcp/lab/common/constant/AuthConst.java`
- 测试：`lab-common/src/test/java/com/gdcp/lab/common/jwt/JwtUtilTest.java`

- [ ] **步骤 1：编写失败的 JWT 测试**

```java
package com.gdcp.lab.common.jwt;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class JwtUtilTest {
    private static final String SECRET = "lab-secret-key-0123456789abcdef0123456789abcdef";

    @Test
    void generateAndParse_shouldRoundTripUserIdAndRole() {
        JwtUtil util = new JwtUtil(SECRET);
        String token = util.generate("1", "ADMIN");
        var claims = util.parse(token);
        assertEquals("1", claims.get("userId"));
        assertEquals("ADMIN", claims.get("role"));
    }

    @Test
    void parse_shouldRejectTemperedToken() {
        JwtUtil util = new JwtUtil(SECRET);
        String token = util.generate("1", "ADMIN") + "x";
        assertThrows(Exception.class, () -> util.parse(token));
    }
}
```

- [ ] **步骤 2：运行测试验证失败**

运行：`.\mvnw.cmd -f lab-management-system/pom.xml -pl lab-common test`
预期：编译失败，报"程序包不存在"。

- [ ] **步骤 3：编写父 POM 与 Maven Wrapper**

创建 `pom.xml`：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <groupId>com.gdcp.lab</groupId>
  <artifactId>lab-parent</artifactId>
  <version>1.0.0</version>
  <packaging>pom</packaging>
  <modules>
    <module>lab-common</module>
    <module>lab-gateway</module>
    <module>lab-user-service</module>
    <module>lab-resource-service</module>
    <module>lab-business-service</module>
    <module>lab-report-service</module>
  </modules>
  <parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>2.7.18</version>
    <relativePath/>
  </parent>
  <properties>
    <java.version>11</java.version>
    <spring-cloud.version>2021.0.9</spring-cloud.version>
    <spring-cloud-alibaba.version>2021.0.5.0</spring-cloud-alibaba.version>
    <mybatis-plus.version>3.5.5</mybatis-plus.version>
    <jjwt.version>0.11.5</jjwt.version>
    <easyexcel.version>3.3.4</easyexcel.version>
  </properties>
  <dependencyManagement>
    <dependencies>
      <dependency>
        <groupId>org.springframework.cloud</groupId>
        <artifactId>spring-cloud-dependencies</artifactId>
        <version>${spring-cloud.version}</version>
        <type>pom</type><scope>import</scope>
      </dependency>
      <dependency>
        <groupId>com.alibaba.cloud</groupId>
        <artifactId>spring-cloud-alibaba-dependencies</artifactId>
        <version>${spring-cloud-alibaba.version}</version>
        <type>pom</type><scope>import</scope>
      </dependency>
    </dependencies>
  </dependencyManagement>
</project>
```

获取 Maven Wrapper（无需预装 Maven）：
```powershell
New-Item -ItemType Directory -Force .mvn\wrapper | Out-Null
Invoke-WebRequest -Uri https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.2.0/maven-wrapper-3.2.0.jar -OutFile .mvn\wrapper\maven-wrapper.jar
Invoke-WebRequest -Uri https://raw.githubusercontent.com/apache/maven-wrapper/maven-wrapper-3.2.0/maven-wrapper-distribution/src/resources/mvnw.cmd -OutFile mvnw.cmd
```
创建 `.mvn/wrapper/maven-wrapper.properties`：
```properties
wrapperVersion=3.2.0
distributionUrl=https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.8.8/apache-maven-3.8.8-bin.zip
```

- [ ] **步骤 4：编写 lab-common 实现代码**

`lab-common/pom.xml`：

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>com.gdcp.lab</groupId>
    <artifactId>lab-parent</artifactId>
    <version>1.0.0</version>
  </parent>
  <artifactId>lab-common</artifactId>
  <dependencies>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
      <groupId>io.jsonwebtoken</groupId>
      <artifactId>jjwt-api</artifactId>
      <version>${jjwt.version}</version>
    </dependency>
    <dependency>
      <groupId>io.jsonwebtoken</groupId>
      <artifactId>jjwt-impl</artifactId>
      <version>${jjwt.version}</version>
      <scope>runtime</scope>
    </dependency>
    <dependency>
      <groupId>io.jsonwebtoken</groupId>
      <artifactId>jjwt-jackson</artifactId>
      <version>${jjwt.version}</version>
      <scope>runtime</scope>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-test</artifactId>
      <scope>test</scope>
    </dependency>
  </dependencies>
</project>
```

`Result.java`：

```java
package com.gdcp.lab.common.result;

import lombok.Data;

@Data
public class Result<T> {
    private int code;
    private String message;
    private T data;

    public static <T> Result<T> ok(T data) {
        Result<T> r = new Result<>();
        r.code = 200; r.message = "success"; r.data = data;
        return r;
    }
    public static <T> Result<T> fail(int code, String message) {
        Result<T> r = new Result<>();
        r.code = code; r.message = message;
        return r;
    }
}
```

`ResultCode.java`：

```java
package com.gdcp.lab.common.result;

public final class ResultCode {
    public static final int SUCCESS = 200;
    public static final int UNAUTHORIZED = 401;
    public static final int FORBIDDEN = 403;
    public static final int ERROR = 500;
    private ResultCode() {}
}
```

`BizException.java`：

```java
package com.gdcp.lab.common.exception;

public class BizException extends RuntimeException {
    private final int code;
    public BizException(String message) { this(500, message); }
    public BizException(int code, String message) { super(message); this.code = code; }
    public int getCode() { return code; }
}
```

`GlobalExceptionHandler.java`：

```java
package com.gdcp.lab.common.exception;

import com.gdcp.lab.common.result.Result;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(BizException.class)
    public Result<Void> handleBiz(BizException e) {
        return Result.fail(e.getCode(), e.getMessage());
    }
    @ExceptionHandler(Exception.class)
    public Result<Void> handleOther(Exception e) {
        return Result.fail(500, "系统异常: " + e.getMessage());
    }
}
```

`JwtUtil.java`：

```java
package com.gdcp.lab.common.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

public class JwtUtil {
    private final SecretKey key;
    private final long expireMs = 1000L * 60 * 60 * 24;

    public JwtUtil(String secret) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generate(String userId, String role) {
        Date now = new Date();
        return Jwts.builder()
                .claim("userId", userId)
                .claim("role", role)
                .setIssuedAt(now)
                .setExpiration(new Date(now.getTime() + expireMs))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    public Claims parse(String token) {
        return Jwts.parserBuilder().setSigningKey(key).build()
                .parseClaimsJws(token).getBody();
    }
}
```

`AuthConst.java`：

```java
package com.gdcp.lab.common.constant;

public final class AuthConst {
    public static final String AUTH_HEADER = "Authorization";
    public static final String TOKEN_PREFIX = "Bearer ";
    private AuthConst() {}
}
```

- [ ] **步骤 5：运行测试验证通过**

运行：`.\mvnw.cmd -f lab-management-system/pom.xml -pl lab-common test`
预期：2 个 JWT 测试 PASS。

- [ ] **步骤 6：Commit**

```bash
git add lab-management-system
git commit -m "feat: 父POM/通用模块/JWT工具与测试"
```

---

## 任务 3：user-service——登录、JWT 签发、默认管理员初始化

**文件：**
- 创建：`lab-user-service/pom.xml`
- 创建：`lab-user-service/src/main/resources/application.yml`
- 创建：`.../UserApplication.java`
- 创建：`.../entity/SysUser.java`
- 创建：`.../mapper/SysUserMapper.java`
- 创建：`.../config/SecurityConfig.java`（BCrypt Bean）
- 创建：`.../config/DataInitializer.java`（默认 admin/123456）
- 创建：`.../service/AuthService.java`
- 创建：`.../controller/AuthController.java`
- 测试：`.../service/AuthServiceTest.java`

- [ ] **步骤 1：编写失败的登录测试**

```java
package com.gdcp.lab.user.service;

import com.gdcp.lab.common.exception.BizException;
import com.gdcp.lab.user.entity.SysUser;
import com.gdcp.lab.user.mapper.SysUserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class AuthServiceTest {
    private SysUserMapper mapper;
    private AuthService service;

    @BeforeEach
    void setUp() {
        mapper = Mockito.mock(SysUserMapper.class);
        service = new AuthService(mapper,
                new BCryptPasswordEncoder(),
                "lab-secret-key-0123456789abcdef0123456789abcdef");
    }

    @Test
    void login_success_returnsToken() {
        SysUser u = new SysUser();
        u.setId(1L); u.setUsername("admin");
        u.setPassword(new BCryptPasswordEncoder().encode("123456"));
        u.setRole("SYSTEM_ADMIN"); u.setStatus(1);
        Mockito.when(mapper.findByUsername("admin")).thenReturn(Optional.of(u));
        String token = service.login("admin", "123456");
        assertNotNull(token);
        assertEquals(3, token.split("\\.").length);
    }

    @Test
    void login_wrongPassword_throws() {
        SysUser u = new SysUser();
        u.setUsername("admin");
        u.setPassword(new BCryptPasswordEncoder().encode("123456"));
        u.setRole("SYSTEM_ADMIN"); u.setStatus(1);
        Mockito.when(mapper.findByUsername("admin")).thenReturn(Optional.of(u));
        assertThrows(BizException.class, () -> service.login("admin", "wrong"));
    }

    @Test
    void login_disabledUser_throws() {
        SysUser u = new SysUser();
        u.setUsername("stu"); u.setPassword("x"); u.setStatus(0);
        Mockito.when(mapper.findByUsername("stu")).thenReturn(Optional.of(u));
        assertThrows(BizException.class, () -> service.login("stu", "123456"));
    }
}
```

- [ ] **步骤 2：运行测试验证失败**

运行：`.\mvnw.cmd -f lab-management-system/pom.xml -pl lab-user-service test`
预期：编译失败（类不存在）。

- [ ] **步骤 3：编写实现代码**

`lab-user-service/pom.xml`：

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <parent><groupId>com.gdcp.lab</groupId><artifactId>lab-parent</artifactId><version>1.0.0</version></parent>
  <artifactId>lab-user-service</artifactId>
  <dependencies>
    <dependency><groupId>com.gdcp.lab</groupId><artifactId>lab-common</artifactId><version>1.0.0</version></dependency>
    <dependency><groupId>com.alibaba.cloud</groupId><artifactId>spring-cloud-starter-alibaba-nacos-discovery</artifactId></dependency>
    <dependency><groupId>com.baomidou</groupId><artifactId>mybatis-plus-boot-starter</artifactId><version>${mybatis-plus.version}</version></dependency>
    <dependency><groupId>com.mysql</groupId><artifactId>mysql-connector-j</artifactId><scope>runtime</scope></dependency>
    <dependency><groupId>org.springframework.security</groupId><artifactId>spring-security-crypto</artifactId></dependency>
    <dependency><groupId>com.alibaba</groupId><artifactId>easyexcel</artifactId><version>${easyexcel.version}</version></dependency>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-test</artifactId><scope>test</scope></dependency>
  </dependencies>
</project>
```

`application.yml`：

```yaml
server:
  port: 9100
spring:
  application:
    name: lab-user-service
  cloud:
    nacos:
      discovery:
        server-addr: 127.0.0.1:8848
  datasource:
    url: jdbc:mysql://127.0.0.1:3306/lab_user?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
    username: root
    password: ${MYSQL_PASSWORD:root}
mybatis-plus:
  configuration:
    map-underscore-to-camel-case: true
lab:
  jwt:
    secret: lab-secret-key-0123456789abcdef0123456789abcdef
```

`UserApplication.java`：

```java
package com.gdcp.lab.user;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.gdcp.lab")
@MapperScan("com.gdcp.lab.user.mapper")
public class UserApplication {
    public static void main(String[] args) {
        SpringApplication.run(UserApplication.class, args);
    }
}
```

`SysUser.java`：

```java
package com.gdcp.lab.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_user")
public class SysUser {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String username;
    private String password;
    private String realName;
    private String employeeNo;
    private String phone;
    private String dept;
    private String role;
    private Integer status;
    private LocalDateTime createdAt;
}
```

`SysUserMapper.java`：

```java
package com.gdcp.lab.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gdcp.lab.user.entity.SysUser;
import org.apache.ibatis.annotations.Select;

import java.util.Optional;

public interface SysUserMapper extends BaseMapper<SysUser> {
    @Select("SELECT * FROM sys_user WHERE username = #{username}")
    Optional<SysUser> findByUsername(String username);
}
```

`SecurityConfig.java`：

```java
package com.gdcp.lab.user.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@Configuration
public class SecurityConfig {
    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
```

`DataInitializer.java`：

```java
package com.gdcp.lab.user.config;

import com.gdcp.lab.user.entity.SysUser;
import com.gdcp.lab.user.mapper.SysUserMapper;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements CommandLineRunner {
    private final SysUserMapper userMapper;
    private final BCryptPasswordEncoder encoder;

    public DataInitializer(SysUserMapper userMapper, BCryptPasswordEncoder encoder) {
        this.userMapper = userMapper;
        this.encoder = encoder;
    }

    @Override
    public void run(String... args) {
        if (userMapper.findByUsername("admin").isEmpty()) {
            SysUser admin = new SysUser();
            admin.setUsername("admin");
            admin.setPassword(encoder.encode("123456"));
            admin.setRealName("系统管理员");
            admin.setRole("SYSTEM_ADMIN");
            admin.setStatus(1);
            userMapper.insert(admin);
        }
    }
}
```

`AuthService.java`：

```java
package com.gdcp.lab.user.service;

import com.gdcp.lab.common.exception.BizException;
import com.gdcp.lab.common.jwt.JwtUtil;
import com.gdcp.lab.user.entity.SysUser;
import com.gdcp.lab.user.mapper.SysUserMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    private final SysUserMapper userMapper;
    private final BCryptPasswordEncoder encoder;
    private final JwtUtil jwtUtil;

    public AuthService(SysUserMapper userMapper, BCryptPasswordEncoder encoder,
                       @Value("${lab.jwt.secret}") String secret) {
        this.userMapper = userMapper;
        this.encoder = encoder;
        this.jwtUtil = new JwtUtil(secret);
    }

    public String login(String username, String password) {
        SysUser u = userMapper.findByUsername(username)
                .orElseThrow(() -> new BizException(401, "用户名或密码错误"));
        if (u.getStatus() == null || u.getStatus() != 1) {
            throw new BizException(401, "账号已被禁用");
        }
        if (!encoder.matches(password, u.getPassword())) {
            throw new BizException(401, "用户名或密码错误");
        }
        return jwtUtil.generate(String.valueOf(u.getId()), u.getRole());
    }
}
```

`AuthController.java`：

```java
package com.gdcp.lab.user.controller;

import com.gdcp.lab.common.result.Result;
import com.gdcp.lab.user.service.AuthService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) { this.authService = authService; }

    @PostMapping("/login")
    public Result<Map<String, String>> login(@RequestBody Map<String, String> body) {
        String token = authService.login(body.get("username"), body.get("password"));
        return Result.ok(Map.of("token", token));
    }
}
```

- [ ] **步骤 4：运行测试验证通过**

运行：`.\mvnw.cmd -f lab-management-system/pom.xml -pl lab-user-service test`
预期：3 个登录测试 PASS。

- [ ] **步骤 5：启动验证登录接口**

运行：`.\mvnw.cmd -f lab-management-system/pom.xml -pl lab-user-service spring-boot:run`
```powershell
$b = '{"username":"admin","password":"123456"}'
$r = Invoke-RestMethod -Method Post -ContentType 'application/json' -Body $b http://localhost:9100/api/v1/auth/login
$r.code              # 期望 200
$r.data.token.Length # 期望 > 0
```

- [ ] **步骤 6：Commit**

```bash
git add lab-management-system/lab-user-service
git commit -m "feat: user-service 登录与JWT签发"
```

---

## 任务 4：user-service——用户管理 CRUD、Excel 批量导入、操作日志

**文件：**
- 创建：`.../controller/UserController.java`
- 创建：`.../service/UserService.java`
- 创建：`.../excel/UserImportRow.java`
- 创建：`.../excel/UserImportListener.java`
- 创建：`.../entity/OperationLog.java`
- 创建：`.../mapper/OperationLogMapper.java`
- 创建：`.../service/OperationLogService.java`
- 测试：`.../service/UserServiceTest.java`

- [ ] **步骤 1：编写失败的测试**

```java
package com.gdcp.lab.user.service;

import com.gdcp.lab.common.exception.BizException;
import com.gdcp.lab.user.entity.SysUser;
import com.gdcp.lab.user.excel.UserImportListener;
import com.gdcp.lab.user.excel.UserImportRow;
import com.gdcp.lab.user.mapper.SysUserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;

class UserServiceTest {
    private SysUserMapper mapper;
    private UserService service;

    @BeforeEach
    void setUp() {
        mapper = Mockito.mock(SysUserMapper.class);
        service = new UserService(mapper, new BCryptPasswordEncoder());
    }

    @Test
    void createUser_duplicateUsername_throws() {
        Mockito.when(mapper.findByUsername("stu1")).thenReturn(Optional.of(new SysUser()));
        assertThrows(BizException.class, () -> service.create("stu1", "张三", "STUDENT"));
    }

    @Test
    void importRows_insertsEachUser() {
        Mockito.when(mapper.findByUsername(any())).thenReturn(Optional.empty());
        UserImportListener listener = new UserImportListener(mapper, new BCryptPasswordEncoder());
        listener.getRows().addAll(List.of(
            new UserImportRow("stu1001", "李四", "STUDENT"),
            new UserImportRow("tea2001", "王五", "TEACHER")
        ));
        listener.saveData();
        Mockito.verify(mapper, Mockito.times(2)).insert(any(SysUser.class));
    }
}
```

- [ ] **步骤 2：运行测试验证失败**

运行：`.\mvnw.cmd -f lab-management-system/pom.xml -pl lab-user-service test`
预期：编译失败。

- [ ] **步骤 3：编写实现代码**

`UserService.java`：

```java
package com.gdcp.lab.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gdcp.lab.common.exception.BizException;
import com.gdcp.lab.user.entity.SysUser;
import com.gdcp.lab.user.mapper.SysUserMapper;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserService {
    private final SysUserMapper userMapper;
    private final BCryptPasswordEncoder encoder;

    public UserService(SysUserMapper userMapper, BCryptPasswordEncoder encoder) {
        this.userMapper = userMapper;
        this.encoder = encoder;
    }

    public SysUser create(String username, String realName, String role) {
        if (userMapper.findByUsername(username).isPresent()) {
            throw new BizException("用户名已存在: " + username);
        }
        SysUser u = new SysUser();
        u.setUsername(username);
        u.setPassword(encoder.encode("123456"));
        u.setRealName(realName);
        u.setRole(role);
        u.setStatus(1);
        userMapper.insert(u);
        return u;
    }

    public List<SysUser> list(String role) {
        LambdaQueryWrapper<SysUser> qw = new LambdaQueryWrapper<>();
        if (role != null && !role.isBlank()) qw.eq(SysUser::getRole, role);
        return userMapper.selectList(qw);
    }

    public void toggleStatus(Long id, Integer status) {
        SysUser u = userMapper.selectById(id);
        if (u == null) throw new BizException("用户不存在");
        u.setStatus(status);
        userMapper.updateById(u);
    }
}
```

`UserImportRow.java`：

```java
package com.gdcp.lab.user.excel;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

@Data
public class UserImportRow {
    @ExcelProperty("用户名") private String username;
    @ExcelProperty("姓名") private String realName;
    @ExcelProperty("角色") private String role;
}
```

`UserImportListener.java`：

```java
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
```

`UserController.java`：

```java
package com.gdcp.lab.user.controller;

import com.alibaba.excel.EasyExcel;
import com.gdcp.lab.common.result.Result;
import com.gdcp.lab.user.entity.SysUser;
import com.gdcp.lab.user.excel.UserImportListener;
import com.gdcp.lab.user.excel.UserImportRow;
import com.gdcp.lab.user.mapper.SysUserMapper;
import com.gdcp.lab.user.service.UserService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {
    private final UserService userService;
    private final SysUserMapper userMapper;

    public UserController(UserService userService, SysUserMapper userMapper) {
        this.userService = userService;
        this.userMapper = userMapper;
    }

    @GetMapping
    public Result<List<SysUser>> list(@RequestParam(required = false) String role) {
        return Result.ok(userService.list(role));
    }

    @PostMapping
    public Result<SysUser> create(@RequestBody Map<String, String> body) {
        return Result.ok(userService.create(body.get("username"), body.get("realName"), body.get("role")));
    }

    @PostMapping("/import")
    public Result<String> importExcel(@RequestParam("file") MultipartFile file) throws IOException {
        UserImportListener listener = new UserImportListener(userMapper, new BCryptPasswordEncoder());
        EasyExcel.read(file.getInputStream(), UserImportRow.class, listener).sheet().doRead();
        return Result.ok("导入完成，共 " + listener.getRows().size() + " 条");
    }
}
```

`OperationLog.java` / `OperationLogMapper.java`（字段同 init-db.sql，Mapper 继承 BaseMapper）：

```java
package com.gdcp.lab.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("sys_operation_log")
public class OperationLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String username;
    private String action;
    private String detail;
    private LocalDateTime createdAt;
}
```

`OperationLogService.java`：

```java
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
```

- [ ] **步骤 4：运行测试验证通过**

运行：`.\mvnw.cmd -f lab-management-system/pom.xml -pl lab-user-service test`
预期：2 个新测试 PASS。

- [ ] **步骤 5：接口冒烟**

```powershell
Invoke-RestMethod -Method Post -ContentType 'application/json' -Body '{"username":"stu1001","realName":"李四","role":"STUDENT"}' http://localhost:9100/api/v1/users
Invoke-RestMethod -Method Get http://localhost:9100/api/v1/users
```
预期：返回新建用户与列表。

- [ ] **步骤 6：Commit**

```bash
git add lab-management-system/lab-user-service
git commit -m "feat: 用户管理CRUD与Excel批量导入"
```

---

## 任务 5：resource-service——实训室/设备/耗材 CRUD 与出入库

**文件：**
- 创建：`lab-resource-service/pom.xml`
- 创建：`lab-resource-service/src/main/resources/application.yml`
- 创建：`.../ResourceApplication.java`
- 创建：`.../entity/LabRoom.java`、`.../entity/LabDevice.java`、`.../entity/Material.java`、`.../entity/MaterialRecord.java`
- 创建：`.../mapper/LabRoomMapper.java`、`LabDeviceMapper.java`、`MaterialMapper.java`、`MaterialRecordMapper.java`
- 创建：`.../service/ResourceService.java`
- 创建：`.../controller/ResourceController.java`
- 测试：`.../service/ResourceServiceTest.java`

- [ ] **步骤 1：编写失败的出入库测试**

```java
package com.gdcp.lab.resource.service;

import com.gdcp.lab.common.exception.BizException;
import com.gdcp.lab.resource.entity.Material;
import com.gdcp.lab.resource.mapper.MaterialMapper;
import com.gdcp.lab.resource.mapper.MaterialRecordMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;

class ResourceServiceTest {
    private MaterialMapper materialMapper;
    private ResourceService service;

    @BeforeEach
    void setUp() {
        materialMapper = Mockito.mock(MaterialMapper.class);
        service = new ResourceService(null, null, materialMapper, Mockito.mock(MaterialRecordMapper.class));
    }

    @Test
    void stockOut_insufficient_throws() {
        Material m = new Material();
        m.setId(1L); m.setStock(2);
        Mockito.when(materialMapper.selectById(1L)).thenReturn(m);
        assertThrows(BizException.class, () -> service.stock(1L, "OUT", 5, 9L));
    }

    @Test
    void stockIn_increasesStock() {
        Material m = new Material();
        m.setId(1L); m.setStock(2);
        Mockito.when(materialMapper.selectById(1L)).thenReturn(m);
        service.stock(1L, "IN", 3, 9L);
        assertEquals(5, m.getStock());
        Mockito.verify(materialMapper).updateById(m);
    }
}
```

- [ ] **步骤 2：运行测试验证失败**

运行：`.\mvnw.cmd -f lab-management-system/pom.xml -pl lab-resource-service test`
预期：编译失败。

- [ ] **步骤 3：编写实现代码**

`lab-resource-service/pom.xml`：

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <parent><groupId>com.gdcp.lab</groupId><artifactId>lab-parent</artifactId><version>1.0.0</version></parent>
  <artifactId>lab-resource-service</artifactId>
  <dependencies>
    <dependency><groupId>com.gdcp.lab</groupId><artifactId>lab-common</artifactId><version>1.0.0</version></dependency>
    <dependency><groupId>com.alibaba.cloud</groupId><artifactId>spring-cloud-starter-alibaba-nacos-discovery</artifactId></dependency>
    <dependency><groupId>com.baomidou</groupId><artifactId>mybatis-plus-boot-starter</artifactId><version>${mybatis-plus.version}</version></dependency>
    <dependency><groupId>com.mysql</groupId><artifactId>mysql-connector-j</artifactId><scope>runtime</scope></dependency>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-test</artifactId><scope>test</scope></dependency>
  </dependencies>
</project>
```

`application.yml`（端口 9200、schema `lab_resource`，结构同任务 3，含 `lab.jwt.secret`）。

实体类 `LabRoom`/`LabDevice`/`Material`/`MaterialRecord` 字段与 init-db.sql 建表一致，使用 `@TableName` + `@TableId(type = IdType.AUTO)`。

`ResourceService.java`：

```java
package com.gdcp.lab.resource.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gdcp.lab.common.exception.BizException;
import com.gdcp.lab.resource.entity.LabDevice;
import com.gdcp.lab.resource.entity.LabRoom;
import com.gdcp.lab.resource.entity.Material;
import com.gdcp.lab.resource.entity.MaterialRecord;
import com.gdcp.lab.resource.mapper.LabDeviceMapper;
import com.gdcp.lab.resource.mapper.LabRoomMapper;
import com.gdcp.lab.resource.mapper.MaterialMapper;
import com.gdcp.lab.resource.mapper.MaterialRecordMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ResourceService {
    private final LabRoomMapper roomMapper;
    private final LabDeviceMapper deviceMapper;
    private final MaterialMapper materialMapper;
    private final MaterialRecordMapper recordMapper;

    public ResourceService(LabRoomMapper roomMapper, LabDeviceMapper deviceMapper,
                           MaterialMapper materialMapper, MaterialRecordMapper recordMapper) {
        this.roomMapper = roomMapper;
        this.deviceMapper = deviceMapper;
        this.materialMapper = materialMapper;
        this.recordMapper = recordMapper;
    }

    public List<LabRoom> listRooms(String keyword) {
        LambdaQueryWrapper<LabRoom> qw = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            qw.and(w -> w.like(LabRoom::getName, keyword).or().like(LabRoom::getCode, keyword));
        }
        return roomMapper.selectList(qw);
    }

    public List<LabDevice> listDevices(Long roomId) {
        LambdaQueryWrapper<LabDevice> qw = new LambdaQueryWrapper<>();
        if (roomId != null) qw.eq(LabDevice::getRoomId, roomId);
        return deviceMapper.selectList(qw);
    }

    public void saveRoom(LabRoom room) { roomMapper.insertOrUpdate(room); }
    public void saveDevice(LabDevice device) { deviceMapper.insertOrUpdate(device); }

    public List<Material> listMaterials(String keyword) {
        LambdaQueryWrapper<Material> qw = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            qw.and(w -> w.like(Material::getName, keyword).or().like(Material::getCode, keyword));
        }
        return materialMapper.selectList(qw);
    }

    @Transactional
    public void stock(Long materialId, String type, int quantity, Long operatorId) {
        if (quantity <= 0) throw new BizException("数量必须大于0");
        Material m = materialMapper.selectById(materialId);
        if (m == null) throw new BizException("耗材不存在");
        if ("IN".equalsIgnoreCase(type)) {
            m.setStock(m.getStock() + quantity);
        } else if ("OUT".equalsIgnoreCase(type)) {
            if (m.getStock() < quantity) throw new BizException("库存不足");
            m.setStock(m.getStock() - quantity);
        } else {
            throw new BizException("无效的出入库类型");
        }
        materialMapper.updateById(m);
        MaterialRecord record = new MaterialRecord();
        record.setMaterialId(materialId);
        record.setType(type.toUpperCase());
        record.setQuantity(quantity);
        record.setOperatorId(operatorId);
        recordMapper.insert(record);
    }
}
```

`ResourceController.java`（包 `Result`）：
- `GET /api/v1/rooms?keyword=` → `listRooms`
- `POST /api/v1/rooms` → `saveRoom`
- `GET /api/v1/devices?roomId=` → `listDevices`
- `POST /api/v1/devices` → `saveDevice`
- `GET /api/v1/materials?keyword=` → `listMaterials`
- `POST /api/v1/materials/{id}/stock?type=IN|OUT&quantity=5&operatorId=1` → `stock`

- [ ] **步骤 4：运行测试验证通过**

运行：`.\mvnw.cmd -f lab-management-system/pom.xml -pl lab-resource-service test`
预期：2 个测试 PASS。

- [ ] **步骤 5：接口冒烟**

```powershell
Invoke-RestMethod -Method Post -ContentType 'application/json' -Body '{"code":"SX-101","name":"网络安全实训室","building":"实训楼A","floor":3,"capacity":60,"type":"NETWORK"}' http://localhost:9200/api/v1/rooms
Invoke-RestMethod -Method Get 'http://localhost:9200/api/v1/rooms?keyword=网络安全'
```
预期：创建并列出实训室。

- [ ] **步骤 6：Commit**

```bash
git add lab-management-system/lab-resource-service
git commit -m "feat: 实训室/设备/耗材台账与出入库"
```

---

## 任务 6：business-service——预约状态机（冲突检测）+ MQ 事件发布

**文件：**
- 创建：`lab-business-service/pom.xml`
- 创建：`lab-business-service/src/main/resources/application.yml`
- 创建：`.../BusinessApplication.java`
- 创建：`.../entity/Reservation.java`、`.../entity/RepairOrder.java`
- 创建：`.../mapper/ReservationMapper.java`、`.../mapper/RepairOrderMapper.java`
- 创建：`.../service/ReservationService.java`
- 创建：`.../controller/ReservationController.java`
- 创建：`.../config/RabbitConfig.java`
- 测试：`.../service/ReservationServiceTest.java`

- [ ] **步骤 1：编写失败的冲突检测与状态流转测试**

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

class ReservationServiceTest {
    private ReservationMapper mapper;
    private ReservationService service;

    @BeforeEach
    void setUp() {
        mapper = Mockito.mock(ReservationMapper.class);
        service = new ReservationService(mapper, Mockito.mock(RabbitTemplate.class));
    }

    private Reservation res(long id, String st, String et, String status) {
        Reservation r = new Reservation();
        r.setId(id); r.setRoomId(1L); r.setStatus(status);
        r.setStartTime(LocalDateTime.parse(st));
        r.setEndTime(LocalDateTime.parse(et));
        return r;
    }

    @Test
    void create_overlappingTime_throws() {
        Mockito.when(mapper.findActiveByRoom(1L)).thenReturn(List.of(
                res(1L, "2026-09-08T09:00:00", "2026-09-08T11:00:00", "APPROVED")));
        Reservation newRes = res(0L, "2026-09-08T10:00:00", "2026-09-08T12:00:00", "PENDING");
        assertThrows(BizException.class, () -> service.create(newRes, 5L));
    }

    @Test
    void create_noConflict_saves() {
        Mockito.when(mapper.findActiveByRoom(1L)).thenReturn(List.of());
        Reservation newRes = res(0L, "2026-09-08T13:00:00", "2026-09-08T14:00:00", "PENDING");
        service.create(newRes, 5L);
        Mockito.verify(mapper).insert(newRes);
    }

    @Test
    void approve_validTransition_changesStatus() {
        Reservation r = res(1L, "2026-09-08T09:00:00", "2026-09-08T10:00:00", "PENDING");
        Mockito.when(mapper.selectById(1L)).thenReturn(r);
        service.approve(1L, 9L);
        assertEquals("APPROVED", r.getStatus());
        assertEquals(9L, r.getApproverId());
        Mockito.verify(mapper).updateById(r);
    }

    @Test
    void approve_alreadyApproved_throws() {
        Reservation r = res(1L, "2026-09-08T09:00:00", "2026-09-08T10:00:00", "APPROVED");
        Mockito.when(mapper.selectById(1L)).thenReturn(r);
        assertThrows(BizException.class, () -> service.approve(1L, 9L));
    }
}
```

- [ ] **步骤 2：运行测试验证失败**

运行：`.\mvnw.cmd -f lab-management-system/pom.xml -pl lab-business-service test`
预期：编译失败。

- [ ] **步骤 3：编写实现代码**

`lab-business-service/pom.xml`（在任务 5 依赖基础上加 `spring-boot-starter-amqp`、`spring-boot-starter-web`，父模块引用 lab-common）。

`application.yml`（端口 9300、schema `lab_business`、Nacos 注册，并追加）：

```yaml
spring:
  rabbitmq:
    host: 127.0.0.1
    port: 5672
    username: guest
    password: guest
lab:
  jwt:
    secret: lab-secret-key-0123456789abcdef0123456789abcdef
  mq:
    exchange: lab.event.exchange
```

`Reservation.java`（对应建表字段，status 为 String 状态码）：

```java
package com.gdcp.lab.business.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("reservation")
public class Reservation {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long roomId;
    private Long applicantId;
    private String purpose;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer peopleNum;
    private String status;
    private Long approverId;
    private String rejectReason;
    private LocalDateTime createdAt;
}
```

`ReservationMapper.java`：

```java
package com.gdcp.lab.business.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gdcp.lab.business.entity.Reservation;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface ReservationMapper extends BaseMapper<Reservation> {
    @Select("SELECT * FROM reservation WHERE room_id = #{roomId} AND status IN ('PENDING','APPROVED')")
    List<Reservation> findActiveByRoom(Long roomId);
}
```

`RabbitConfig.java`：

```java
package com.gdcp.lab.business.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

@Configuration
public class RabbitConfig {
    public static final String EXCHANGE = "lab.event.exchange";
    public static final String QUEUE_STATS = "lab.stats.queue";
    public static final String QUEUE_NOTIFY = "lab.notify.queue";

    @Bean
    public TopicExchange labExchange() { return new TopicExchange(EXCHANGE); }

    @Bean
    public Queue statsQueue() { return new Queue(QUEUE_STATS); }

    @Bean
    public Queue notifyQueue() { return new Queue(QUEUE_NOTIFY); }

    @Bean
    public Binding statsBinding(Queue statsQueue, TopicExchange labExchange) {
        return BindingBuilder.bind(statsQueue).to(labExchange).with("lab.stats.*");
    }

    @Bean
    public Binding notifyBinding(Queue notifyQueue, TopicExchange labExchange) {
        return BindingBuilder.bind(notifyQueue).to(labExchange).with("lab.notify.*");
    }

    @Bean
    public Jackson2JsonMessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory factory) {
        RabbitTemplate template = new RabbitTemplate(factory);
        template.setMessageConverter(messageConverter());
        return template;
    }
}
```

`ReservationService.java`（状态机 + 冲突检测 + 发事件）：

```java
package com.gdcp.lab.business.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gdcp.lab.business.config.RabbitConfig;
import com.gdcp.lab.business.entity.Reservation;
import com.gdcp.lab.business.mapper.ReservationMapper;
import com.gdcp.lab.common.exception.BizException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class ReservationService {
    private final ReservationMapper reservationMapper;
    private final RabbitTemplate rabbitTemplate;

    public ReservationService(ReservationMapper reservationMapper, RabbitTemplate rabbitTemplate) {
        this.reservationMapper = reservationMapper;
        this.rabbitTemplate = rabbitTemplate;
    }

    public Reservation create(Reservation r, Long applicantId) {
        if (r.getStartTime() == null || r.getEndTime() == null
                || !r.getStartTime().isBefore(r.getEndTime())) {
            throw new BizException("预约时间段不合法");
        }
        if (hasConflict(r)) throw new BizException("该实训室在此时间段已被预约");
        r.setApplicantId(applicantId);
        r.setStatus("PENDING");
        reservationMapper.insert(r);
        publish("lab.notify.reservation.created", r, "APPROVED");
        return r;
    }

    private boolean hasConflict(Reservation r) {
        List<Reservation> actives = reservationMapper.findActiveByRoom(r.getRoomId());
        return actives.stream().anyMatch(a ->
                r.getStartTime().isBefore(a.getEndTime())
                && r.getEndTime().isAfter(a.getStartTime()));
    }

    public void approve(Long id, Long approverId) {
        Reservation r = mustPending(id);
        r.setStatus("APPROVED");
        r.setApproverId(approverId);
        reservationMapper.updateById(r);
        publish("lab.stats.reservation.approved", r, "APPROVED");
    }

    public void reject(Long id, Long approverId, String reason) {
        Reservation r = mustPending(id);
        r.setStatus("REJECTED");
        r.setApproverId(approverId);
        r.setRejectReason(reason);
        reservationMapper.updateById(r);
    }

    public void cancel(Long id, Long operatorId) {
        Reservation r = reservationMapper.selectById(id);
        if (r == null) throw new BizException("预约不存在");
        if (!"PENDING".equals(r.getStatus())) throw new BizException("当前状态不可取消");
        r.setStatus("CANCELLED");
        reservationMapper.updateById(r);
    }

    public void complete(Long id) {
        Reservation r = reservationMapper.selectById(id);
        if (r == null || !"APPROVED".equals(r.getStatus())) throw new BizException("状态非法");
        r.setStatus("COMPLETED");
        reservationMapper.updateById(r);
        publish("lab.stats.reservation.completed", r, "COMPLETED");
    }

    public List<Reservation> list(Long roomId, Long applicantId, String status) {
        LambdaQueryWrapper<Reservation> qw = new LambdaQueryWrapper<>();
        if (roomId != null) qw.eq(Reservation::getRoomId, roomId);
        if (applicantId != null) qw.eq(Reservation::getApplicantId, applicantId);
        if (status != null && !status.isBlank()) qw.eq(Reservation::getStatus, status);
        qw.orderByDesc(Reservation::getCreatedAt);
        return reservationMapper.selectList(qw);
    }

    private Reservation mustPending(Long id) {
        Reservation r = reservationMapper.selectById(id);
        if (r == null) throw new BizException("预约不存在");
        if (!"PENDING".equals(r.getStatus())) throw new BizException("当前状态不可审批");
        return r;
    }

    private void publish(String routingKey, Reservation r, String status) {
        rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE, routingKey,
                Map.of("reservationId", r.getId(), "roomId", r.getRoomId(),
                        "status", status, "applicantId", r.getApplicantId()));
    }
}
```

`ReservationController.java`（`@RequestMapping("/api/v1/reservations")`）：
- `POST /` → create（body 携带 roomId/purpose/startTime/endTime/peopleNum，applicantId 取自请求头 `X-User-Id`，由网关注入）
- `POST /{id}/approve`、`POST /{id}/reject?reason=`、`DELETE /{id}`（取消）、`POST /{id}/complete`
- `GET /?roomId=&applicantId=&status=`

- [ ] **步骤 4：运行测试验证通过**

运行：`.\mvnw.cmd -f lab-management-system/pom.xml -pl lab-business-service test`
预期：4 个测试 PASS。

- [ ] **步骤 5：Commit**

```bash
git add lab-management-system/lab-business-service
git commit -m "feat: 预约状态机与RabbitMQ事件发布"
```

---

## 任务 7：business-service——报修工单状态机 + 发布完成事件

**文件：**
- 创建：`.../entity/RepairOrder.java`（已建）
- 创建：`.../mapper/RepairOrderMapper.java`
- 创建：`.../service/RepairService.java`
- 创建：`.../controller/RepairController.java`
- 测试：`.../service/RepairServiceTest.java`

- [ ] **步骤 1：编写失败的状态流转测试**

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
    void assign_pendingToAssigned() {
        RepairOrder o = order("PENDING");
        Mockito.when(mapper.selectById(1L)).thenReturn(o);
        service.assign(1L, 7L);
        assertEquals("ASSIGNED", o.getStatus());
        assertEquals(7L, o.getAssigneeId());
        Mockito.verify(mapper).updateById(o);
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

运行：`.\mvnw.cmd -f lab-management-system/pom.xml -pl lab-business-service test`
预期：编译失败。

- [ ] **步骤 3：编写实现代码**

`RepairOrderMapper.java`（继承 BaseMapper，`@Select("SELECT * FROM repair_order WHERE status <> 'VERIFIED' ORDER BY id DESC")` 用于工作台列表）。其余查询用 LambdaQueryWrapper。

`RepairService.java`：

```java
package com.gdcp.lab.business.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gdcp.lab.business.config.RabbitConfig;
import com.gdcp.lab.business.entity.RepairOrder;
import com.gdcp.lab.business.mapper.RepairOrderMapper;
import com.gdcp.lab.common.exception.BizException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class RepairService {
    private final RepairOrderMapper orderMapper;
    private final RabbitTemplate rabbitTemplate;

    public RepairService(RepairOrderMapper orderMapper, RabbitTemplate rabbitTemplate) {
        this.orderMapper = orderMapper;
        this.rabbitTemplate = rabbitTemplate;
    }

    public RepairOrder create(RepairOrder o) {
        o.setStatus("PENDING");
        orderMapper.insert(o);
        return o;
    }

    public void assign(Long id, Long assigneeId) {
        RepairOrder o = must(id, "PENDING");
        o.setStatus("ASSIGNED");
        o.setAssigneeId(assigneeId);
        orderMapper.updateById(o);
    }

    public void reject(Long id, String reason) {
        RepairOrder o = must(id, "PENDING");
        o.setStatus("REJECTED");
        o.setResult(reason);
        orderMapper.updateById(o);
    }

    public void finish(Long id, String result) {
        RepairOrder o = must(id, "ASSIGNED");
        o.setStatus("COMPLETED");
        o.setResult(result);
        o.setCompletedAt(LocalDateTime.now());
        orderMapper.updateById(o);
        rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE, "lab.stats.repair.completed",
                Map.of("orderId", o.getId(), "deviceId", o.getDeviceId()));
    }

    public void verify(Long id) {
        RepairOrder o = must(id, "COMPLETED");
        o.setStatus("VERIFIED");
        orderMapper.updateById(o);
    }

    public List<RepairOrder> list(Long deviceId, String status) {
        LambdaQueryWrapper<RepairOrder> qw = new LambdaQueryWrapper<>();
        if (deviceId != null) qw.eq(RepairOrder::getDeviceId, deviceId);
        if (status != null && !status.isBlank()) qw.eq(RepairOrder::getStatus, status);
        qw.orderByDesc(RepairOrder::getId);
        return orderMapper.selectList(qw);
    }

    private RepairOrder must(Long id, String expectedStatus) {
        RepairOrder o = orderMapper.selectById(id);
        if (o == null) throw new BizException("工单不存在");
        if (!expectedStatus.equals(o.getStatus())) throw new BizException("当前状态不可操作");
        return o;
    }
}
```

`RepairController.java`（`@RequestMapping("/api/v1/repairs")`）：
- `POST /` → create
- `POST /{id}/assign?assigneeId=`、`POST /{id}/reject?reason=`、`POST /{id}/finish`（body 带 result）、`POST /{id}/verify`
- `GET /?deviceId=&status=`

- [ ] **步骤 4：运行测试验证通过**

运行：`.\mvnw.cmd -f lab-management-system/pom.xml -pl lab-business-service test`
预期：3 个测试 PASS。

- [ ] **步骤 5：Commit**

```bash
git add lab-management-system/lab-business-service
git commit -m "feat: 报修工单状态机与事件发布"
```

---

## 任务 8：report-service——MQ 消费、聚合统计、统计接口

**文件：**
- 创建：`lab-report-service/pom.xml`
- 创建：`lab-report-service/src/main/resources/application.yml`
- 创建：`.../ReportApplication.java`
- 创建：`.../entity/StatDailyRoomUsage.java`、`.../entity/StatDailyRepair.java`
- 创建：`.../mapper/StatDailyRoomUsageMapper.java`、`.../mapper/StatDailyRepairMapper.java`
- 创建：`.../listener/StatListener.java`
- 创建：`.../service/StatService.java`
- 创建：`.../controller/StatController.java`

- [ ] **步骤 1：编写失败的统计聚合逻辑测试（先建 mapper 桩）**

```java
package com.gdcp.lab.report.service;

import com.gdcp.lab.report.entity.StatDailyRepair;
import com.gdcp.lab.report.mapper.StatDailyRepairMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class StatServiceTest {
    private StatDailyRepairMapper repairMapper;
    private StatService service;

    @BeforeEach
    void setUp() {
        repairMapper = Mockito.mock(StatDailyRepairMapper.class);
        service = new StatService(null, repairMapper);
    }

    @Test
    void repairCompleted_incrementsTodayCount() {
        LocalDate today = LocalDate.of(2026, 9, 7);
        Mockito.when(repairMapper.findByDate(today)).thenReturn(null);
        service.onRepairCompleted(today);
        Mockito.verify(repairMapper).insert(Mockito.any(StatDailyRepair.class));
    }
}
```

- [ ] **步骤 2：运行测试验证失败**

运行：`.\mvnw.cmd -f lab-management-system/pom.xml -pl lab-report-service test`
预期：编译失败。

- [ ] **步骤 3：编写实现代码**

`pom.xml`：依赖 lab-common、nacos-discovery、mybatis-plus、mysql、`spring-boot-starter-amqp`、`spring-boot-starter-test`。

`application.yml`（端口 9400、schema `lab_report`、rabbitmq 配置同任务 6）。

实体 `/mapper`：`StatDailyRoomUsage`（字段 stat_date/room_id/usage_count/approved_count）、`StatDailyRepair`（stat_date/new_count/completed_count），Mapper 各含：

```java
@Select("SELECT * FROM stat_daily_repair WHERE stat_date = #{date}")
StatDailyRepair findByDate(LocalDate date);
```

`StatService.java`：

```java
package com.gdcp.lab.report.service;

import com.gdcp.lab.report.entity.StatDailyRepair;
import com.gdcp.lab.report.entity.StatDailyRoomUsage;
import com.gdcp.lab.report.mapper.StatDailyRepairMapper;
import com.gdcp.lab.report.mapper.StatDailyRoomUsageMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class StatService {
    private final StatDailyRoomUsageMapper usageMapper;
    private final StatDailyRepairMapper repairMapper;

    public StatService(StatDailyRoomUsageMapper usageMapper, StatDailyRepairMapper repairMapper) {
        this.usageMapper = usageMapper;
        this.repairMapper = repairMapper;
    }

    public void onReservationApproved(LocalDate date, Long roomId) {
        StatDailyRoomUsage row = usageMapper.findByDateAndRoom(date, roomId);
        if (row == null) {
            row = new StatDailyRoomUsage();
            row.setStatDate(date); row.setRoomId(roomId);
            row.setApprovedCount(1); row.setUsageCount(0);
            usageMapper.insert(row);
        } else {
            row.setApprovedCount(row.getApprovedCount() + 1);
            usageMapper.updateById(row);
        }
    }

    public void onReservationCompleted(LocalDate date, Long roomId) {
        StatDailyRoomUsage row = usageMapper.findByDateAndRoom(date, roomId);
        if (row == null) {
            row = new StatDailyRoomUsage();
            row.setStatDate(date); row.setRoomId(roomId);
            row.setApprovedCount(0); row.setUsageCount(1);
            usageMapper.insert(row);
        } else {
            row.setUsageCount(row.getUsageCount() + 1);
            usageMapper.updateById(row);
        }
    }

    public void onRepairCompleted(LocalDate date) {
        StatDailyRepair row = repairMapper.findByDate(date);
        if (row == null) {
            row = new StatDailyRepair();
            row.setStatDate(date); row.setCompletedCount(1); row.setNewCount(0);
            repairMapper.insert(row);
        } else {
            row.setCompletedCount(row.getCompletedCount() + 1);
            repairMapper.updateById(row);
        }
    }

    public Map<String, Object> overview() {
        List<StatDailyRoomUsage> usage = usageMapper.selectList(null);
        List<StatDailyRepair> repairs = repairMapper.selectList(null);
        int approved = usage.stream().mapToInt(StatDailyRoomUsage::getApprovedCount).sum();
        int used = usage.stream().mapToInt(StatDailyRoomUsage::getUsageCount).sum();
        int completed = repairs.stream().mapToInt(StatDailyRepair::getCompletedCount).sum();
        Map<String, Object> m = new HashMap<>();
        m.put("totalApproved", approved);
        m.put("totalUsed", used);
        m.put("repairCompleted", completed);
        return m;
    }

    public List<StatDailyRoomUsage> roomUsage(LocalDate from, LocalDate to) {
        return usageMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<StatDailyRoomUsage>()
                .between(StatDailyRoomUsage::getStatDate, from, to)
                .orderByAsc(StatDailyRoomUsage::getStatDate));
    }
}
```

`StatListener.java`：

```java
package com.gdcp.lab.report.listener;

import com.gdcp.lab.report.service.StatService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Map;

@Component
public class StatListener {
    private final StatService statService;

    public StatListener(StatService statService) { this.statService = statService; }

    @RabbitListener(queues = "lab.stats.queue")
    public void onStatsEvent(Map<String, Object> payload) {
        String routing = (String) payload.getOrDefault("_routingKey", "");
        Long roomId = payload.get("roomId") == null ? null : Long.valueOf(payload.get("roomId").toString());
        if (routing.endsWith(".reservation.approved")) {
            statService.onReservationApproved(LocalDate.now(), roomId);
        } else if (routing.endsWith(".reservation.completed")) {
            statService.onReservationCompleted(LocalDate.now(), roomId);
        } else if (routing.endsWith(".repair.completed")) {
            statService.onRepairCompleted(LocalDate.now());
        }
    }
}
```

`StatController.java`：
- `GET /api/v1/stats/overview` → `overview`
- `GET /api/v1/stats/room-usage?from=&to=` → `roomUsage`

- [ ] **步骤 4：运行测试验证通过**

运行：`.\mvnw.cmd -f lab-management-system/pom.xml -pl lab-report-service test`
预期：1 个测试 PASS。

- [ ] **步骤 5：Commit**

```bash
git add lab-management-system/lab-report-service
git commit -m "feat: 统计服务与MQ事件聚合"
```

---

## 任务 9：lab-gateway——路由转发 + JWT 鉴权

**文件：**
- 创建：`lab-gateway/pom.xml`
- 创建：`lab-gateway/src/main/resources/application.yml`
- 创建：`.../GatewayApplication.java`
- 创建：`.../filter/AuthGlobalFilter.java`
- 创建：`.../config/CorsConfig.java`

- [ ] **步骤 1（验证方式为冒烟）：启动网关并验证鉴权过滤**

无法用纯单元测试快速覆盖,故以"登录成功拿 token→通过网关 200；无 token→401"为验收。

- [ ] **步骤 2：编写实现代码**

`lab-gateway/pom.xml`：

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <parent><groupId>com.gdcp.lab</groupId><artifactId>lab-parent</artifactId><version>1.0.0</version></parent>
  <artifactId>lab-gateway</artifactId>
  <dependencies>
    <dependency>
      <groupId>org.springframework.cloud</groupId>
      <artifactId>spring-cloud-starter-gateway</artifactId>
    </dependency>
    <dependency>
      <groupId>com.alibaba.cloud</groupId>
      <artifactId>spring-cloud-starter-alibaba-nacos-discovery</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.cloud</groupId>
      <artifactId>spring-cloud-starter-loadbalancer</artifactId>
    </dependency>
    <dependency><groupId>com.gdcp.lab</groupId><artifactId>lab-common</artifactId><version>1.0.0</version></dependency>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-test</artifactId><scope>test</scope></dependency>
  </dependencies>
</project>
```

`application.yml`：

```yaml
server:
  port: 9000
spring:
  application:
    name: lab-gateway
  cloud:
    nacos:
      discovery:
        server-addr: 127.0.0.1:8848
    gateway:
      routes:
        - id: user-service
          uri: lb://lab-user-service
          predicates:
            - Path=/api/v1/auth/**,/api/v1/users/**
        - id: resource-service
          uri: lb://lab-resource-service
          predicates:
            - Path=/api/v1/rooms/**,/api/v1/devices/**,/api/v1/materials/**
        - id: business-service
          uri: lb://lab-business-service
          predicates:
            - Path=/api/v1/reservations/**,/api/v1/repairs/**
        - id: report-service
          uri: lb://lab-report-service
          predicates:
            - Path=/api/v1/stats/**
lab:
  jwt:
    secret: lab-secret-key-0123456789abcdef0123456789abcdef
```

`AuthGlobalFilter.java`（校验除 login 外的所有请求）：

```java
package com.gdcp.lab.gateway.filter;

import com.gdcp.lab.common.constant.AuthConst;
import com.gdcp.lab.common.jwt.JwtUtil;
import io.jsonwebtoken.Claims;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class AuthGlobalFilter implements GlobalFilter, Ordered {
    private final JwtUtil jwtUtil;

    public AuthGlobalFilter(@Value("${lab.jwt.secret}") String secret) {
        this.jwtUtil = new JwtUtil(secret);
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        if (path.startsWith("/api/v1/auth/login")) {
            return chain.filter(exchange);
        }
        String header = exchange.getRequest().getHeaders().getFirst(AuthConst.AUTH_HEADER);
        if (header == null || !header.startsWith(AuthConst.TOKEN_PREFIX)) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }
        try {
            Claims claims = jwtUtil.parse(header.substring(AuthConst.TOKEN_PREFIX.length()));
            ServerWebExchange mutated = exchange.mutate()
                    .request(r -> r.header("X-User-Id", claims.get("userId").toString())
                            .header("X-User-Role", claims.get("role").toString()))
                    .build();
            return chain.filter(mutated);
        } catch (Exception e) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }
    }

    @Override
    public int getOrder() { return -100; }
}
```

`CorsConfig.java`（允许前端 5173 跨域）：

```java
package com.gdcp.lab.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
public class CorsConfig {
    @Bean
    public CorsWebFilter corsWebFilter() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of("http://localhost:5173", "http://127.0.0.1:5173"));
        config.addAllowedHeader("*");
        config.addAllowedMethod("*");
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsWebFilter(source);
    }
}
```

- [ ] **步骤 3：启动全部后端服务并冒烟验证**

启动顺序：Nacos → RabbitMQ → 各服务（`spring-boot:run`）→ 网关。
```powershell
# 1) 无 token 访问应 401
try { Invoke-WebRequest -UseBasicParsing http://localhost:9000/api/v1/users } catch { $_.Exception.Response.StatusCode.value__ }  # 401

# 2) 登录拿 token（user-service 9100）
$login = Invoke-RestMethod -Method Post -ContentType 'application/json' -Body '{"username":"admin","password":"123456"}' http://localhost:9100/api/v1/auth/login
$t = $login.data.token

# 3) 带 token 经网关访问 users
$h = @{ Authorization = "Bearer $t" }
Invoke-RestMethod -Headers $h http://localhost:9000/api/v1/users   # code 200

# 4) 同时验证业务链路：登录后的 X-User-Id 透传有效（见任务 10 端到端）
```
预期：无 token 401，带 token 200。

- [ ] **步骤 4：Commit**

```bash
git add lab-management-system/lab-gateway
git commit -m "feat: 网关路由与JWT鉴权"
```

---

## 任务 10：前端工程初始化 + 登录与路由守卫

**文件：**
- 创建：`frontend/`（Vite 脚手架：`package.json`、`vite.config.ts`、`tsconfig.json`、`index.html`、`src/main.ts`、`src/App.vue`）
- 创建：`frontend/src/api/request.ts`、`frontend/src/api/auth.ts`
- 创建：`frontend/src/store/user.ts`（Pinia）
- 创建：`frontend/src/router/index.ts`（含守卫）
- 创建：`frontend/src/views/Login.vue`
- 创建：`frontend/src/layouts/MainLayout.vue`

- [ ] **步骤 1：生成工程并安装依赖**

运行：
```powershell
New-Item -ItemType Directory -Path lab-management-system\frontend -Force | Out-Null
cd lab-management-system\frontend
npm create vite@5 . -- --template vue-ts
npm i element-plus @element-plus/icons-vue pinia vue-router axios
```

- [ ] **步骤 2：配置 vite 代理**

`vite.config.ts`：

```ts
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      '/api': { target: 'http://localhost:9000', changeOrigin: true }
    }
  }
})
```

- [ ] **步骤 3：编写 axios 封装**

`src/api/request.ts`：

```ts
import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '../router'

const request = axios.create({ baseURL: '/api/v1', timeout: 15000 })

request.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

request.interceptors.response.use(
  (resp) => {
    const body = resp.data
    if (body.code !== 200) {
      ElMessage.error(body.message || '请求失败')
      return Promise.reject(new Error(body.message))
    }
    return body
  },
  (err) => {
    if (err.response?.status === 401) {
      localStorage.removeItem('token')
      router.push('/login')
    } else if (err.response?.status === 403) {
      ElMessage.error('无权限操作')
    } else {
      ElMessage.error(err.message || '网络错误')
    }
    return Promise.reject(err)
  }
)

export default request
```

`src/api/auth.ts`：

```ts
import request from './request'

export function login(username: string, password: string) {
  return request.post('/auth/login', { username, password })
}
```

- [ ] **步骤 4：Pinia 用户 store 与路由守卫**

`src/store/user.ts`：

```ts
import { defineStore } from 'pinia'

export const useUserStore = defineStore('user', {
  state: () => ({
    token: localStorage.getItem('token') || '',
    role: localStorage.getItem('role') || '',
    username: localStorage.getItem('username') || ''
  }),
  actions: {
    setAuth(token: string, role: string, username: string) {
      this.token = token
      this.role = role
      this.username = username
      localStorage.setItem('token', token)
      localStorage.setItem('role', role)
      localStorage.setItem('username', username)
    },
    logout() {
      this.token = ''; this.role = ''; this.username = ''
      localStorage.removeItem('token')
      localStorage.removeItem('role')
      localStorage.removeItem('username')
    }
  }
})
```

`src/router/index.ts`：

```ts
import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '../store/user'

const routes = [
  { path: '/login', component: () => import('../views/Login.vue') },
  {
    path: '/',
    component: () => import('../layouts/MainLayout.vue'),
    children: [
      { path: '', redirect: '/dashboard' },
      { path: 'dashboard', component: () => import('../views/Dashboard.vue'), meta: { title: '工作台' } },
      { path: 'rooms', component: () => import('../views/Rooms.vue'), meta: { title: '实训室台账' } },
      { path: 'devices', component: () => import('../views/Devices.vue'), meta: { title: '设备台账' } },
      { path: 'materials', component: () => import('../views/Materials.vue'), meta: { title: '耗材管理', roles: ['LAB_ADMIN', 'SYSTEM_ADMIN'] } },
      { path: 'reservations', component: () => import('../views/Reservations.vue'), meta: { title: '实训室预约' } },
      { path: 'repairs', component: () => import('../views/Repairs.vue'), meta: { title: '报修管理' } },
      { path: 'reports', component: () => import('../views/Reports.vue'), meta: { title: '统计报表', roles: ['LAB_ADMIN', 'SYSTEM_ADMIN', 'TEACHER'] } },
      { path: 'system/users', component: () => import('../views/SystemUsers.vue'), meta: { title: '用户管理', roles: ['SYSTEM_ADMIN'] } },
      { path: 'system/logs', component: () => import('../views/SystemLogs.vue'), meta: { title: '操作日志', roles: ['SYSTEM_ADMIN'] } }
    ]
  }
]

const router = createRouter({ history: createWebHistory(), routes })

router.beforeEach((to) => {
  const store = useUserStore()
  if (to.path !== '/login' && !store.token) return '/login'
  if (to.meta.roles && !(to.meta.roles as string[]).includes(store.role)) return '/dashboard'
  return true
})

export default router
```

- [ ] **步骤 5：登录页与主布局**

`src/views/Login.vue`：Element Plus 表单（用户名/密码），提交调 `login()`：登录接口只返回 token，不返回角色与用户名 → 登录成功后再 `GET /auth/me`（user-service 新增：从网关透传的 `X-User-Id` 查用户信息，返回 role/username/realName），写入 store 并 `router.push('/dashboard')`。

补充 user-service 接口（`AuthController` 增加）：

```java
@GetMapping("/me")
public Result<Map<String, String>> me(@RequestHeader("X-User-Id") Long userId) {
    SysUser u = authService.getById(userId);
    return Result.ok(Map.of("id", String.valueOf(u.getId()), "username", u.getUsername(),
            "realName", u.getRealName(), "role", u.getRole()));
}
```
（`authService.getById` 委托 `userMapper.selectById`。）

`src/layouts/MainLayout.vue`：左侧菜单（`el-menu`，依据 `store.role` 过滤 routers meta.roles）、顶栏显示用户名 + 退出登录。

- [ ] **步骤 6：构建验证**

运行：`npm run build`
预期：构建成功，无 TS 报错。

- [ ] **步骤 7：Commit**

```bash
git add lab-management-system/frontend
git commit -m "feat: 前端工程/登录/路由守卫与布局"
```

---

## 任务 11：前端业务页面（台账/预约/报修/耗材/系统管理/统计）

**文件：**
- 创建：`src/views/Dashboard.vue`、`Rooms.vue`、`Devices.vue`、`Materials.vue`
- 创建：`src/views/Reservations.vue`、`Repairs.vue`、`Reports.vue`
- 创建：`src/views/SystemUsers.vue`、`SystemLogs.vue`
- 创建：`src/api/resource.ts`、`src/api/business.ts`、`src/api/stats.ts`、`src/api/user.ts`

- [ ] **步骤 1：编写 API 封装**

`src/api/resource.ts`：

```ts
import request from './request'

export const listRooms = (params?: any) => request.get('/rooms', { params })
export const saveRoom = (data: any) => request.post('/rooms', data)
export const listDevices = (params?: any) => request.get('/devices', { params })
export const saveDevice = (data: any) => request.post('/devices', data)
export const listMaterials = (params?: any) => request.get('/materials', { params })
export const stockMaterial = (id: number, type: string, quantity: number) =>
  request.post(`/materials/${id}/stock`, null, { params: { type, quantity } })
```

`src/api/business.ts`：

```ts
import request from './request'

export const listReservations = (params?: any) => request.get('/reservations', { params })
export const createReservation = (data: any) => request.post('/reservations', data)
export const approveReservation = (id: number) => request.post(`/reservations/${id}/approve`)
export const rejectReservation = (id: number, reason: string) => request.post(`/reservations/${id}/reject`, null, { params: { reason } })
export const cancelReservation = (id: number) => request.delete(`/reservations/${id}`)
export const listRepairs = (params?: any) => request.get('/repairs', { params })
export const createRepair = (data: any) => request.post('/repairs', data)
export const assignRepair = (id: number, assigneeId: number) => request.post(`/repairs/${id}/assign`, null, { params: { assigneeId } })
export const finishRepair = (id: number, result: string) => request.post(`/repairs/${id}/finish`, null, { params: { result } })
```

`src/api/stats.ts`：

```ts
import request from './request'

export const getOverview = () => request.get('/stats/overview')
export const getRoomUsage = (params: any) => request.get('/stats/room-usage', { params })
```

`src/api/user.ts`：

```ts
import request from './request'

export const listUsers = (params?: any) => request.get('/users', { params })
export const createUser = (data: any) => request.post('/users', data)
export const listLogs = () => request.get('/logs')  // 需在 UserController 增加 GET /api/v1/logs → OperationLogService.list()
export const importUsers = (file: File) => {
  const form = new FormData()
  form.append('file', file)
  return request.post('/users/import', form, { headers: { 'Content-Type': 'multipart/form-data' } })
}
```

- [ ] **步骤 2：编写核心业务页面（预约为主线）**

`src/views/Reservations.vue`：
- 学生/教师视图：`el-form`（选择实训室 `listRooms`、日期时间范围 `el-date-picker type="datetimerange"`、purpose、peopleNum）提交 `createReservation`；下方 `el-table` 展示 `listReservations({ applicantId: store.userId })`，待审批可取消。
- 管理员视图：顶部 Tab 切换"我的预约/审批中心"；审批中心 `listReservations({ status: 'PENDING' })`，操作列有"通过 approveReservation / 驳回 rejectReservation(需 reason 弹窗)"。

`src/views/Repairs.vue`：
- 提交报修：`el-form`（device 选择 `listDevices`、description）→ `createRepair`。
- 工单列表按角色展示；管理员：`assignRepair`（选择维修员，`listUsers({role:'LAB_ADMIN'})`）、`finishRepair`（填写结果）；完成后 `verifyRepair`（`POST /repairs/{id}/verify`，在 api/business.ts 补该函数）。

`src/views/Rooms.vue` / `Devices.vue` / `Materials.vue`：`el-table` + 搜索框 + 新增/编辑 `el-dialog` 表单；页面按 `store.role` 控制按钮显隐（学生/教师只读）。Materials 页面增加"入库/出库"对话框（`stockMaterial` 数量 + 类型）。

`src/views/SystemUsers.vue`：用户表格（`listUsers`）+ 新建（`createUser`）+ Excel 导入（`el-upload` → `importUsers`）。
`src/views/SystemLogs.vue`：`listLogs()` 表格展示。
`src/views/Reports.vue`：`getOverview()` 统计卡片 + `ECharts`（`npm i echarts`）柱状图展示 `getRoomUsage({from,to})`。
`src/views/Dashboard.vue`：欢迎语 + 角色相关快捷入口。

- [ ] **步骤 3：构建验证**

运行：`npm run build`
预期：构建成功。

- [ ] **步骤 4：Commit**

```bash
git add lab-management-system/frontend
git commit -m "feat: 前端业务页面与统计图表"
```

---

## 任务 12：一键启动脚本 + 端到端验收

**文件：**
- 创建：`scripts/start-all.cmd`、`scripts/stop-all.cmd`

- [ ] **步骤 1：编写启动/停止脚本**

`scripts/start-all.cmd`：

```bat
@echo off
rem 1) Nacos
start "nacos" cmd /k "cd /d C:\nacos\nacos\bin && startup.cmd -m standalone"
rem 2) RabbitMQ
net start RabbitMQ
rem 3) 打包并后台启动各服务（等待 Nacos 就绪后再执行）
cd /d %~dp0..
call .\mvnw.cmd -f lab-management-system\pom.xml -pl lab-user-service,lab-resource-service,lab-business-service,lab-report-service,lab-gateway -am package -DskipTests
start "user"  cmd /k "java -jar lab-user-service\target\lab-user-service-1.0.0.jar"
start "resource" cmd /k "java -jar lab-resource-service\target\lab-resource-service-1.0.0.jar"
start "business" cmd /k "java -jar lab-business-service\target\lab-business-service-1.0.0.jar"
start "report"  cmd /k "java -jar lab-report-service\target\lab-report-service-1.0.0.jar"
start "gateway" cmd /k "java -jar lab-gateway\target\lab-gateway-1.0.0.jar"
rem 4) 前端
start "frontend" cmd /k "cd /d frontend && npm run dev"
echo Started. Frontend: http://localhost:5173 Gateway: http://localhost:9000
```

`scripts/stop-all.cmd`：

```bat
@echo off
taskkill /FI "WINDOWTITLE eq user*"  2>nul
taskkill /FI "WINDOWTITLE eq resource*" 2>nul
taskkill /FI "WINDOWTITLE eq business*" 2>nul
taskkill /FI "WINDOWTITLE eq report*" 2>nul
taskkill /FI "WINDOWTITLE eq gateway*" 2>nul
taskkill /FI "WINDOWTITLE eq frontend*" 2>nul
echo Stopped.
```

- [ ] **步骤 2：端到端验收——预约主链路**

```powershell
# 1) 登录 admin
$login = Invoke-RestMethod -Method Post -ContentType 'application/json' -Body '{"username":"admin","password":"123456"}' http://localhost:9000/api/v1/auth/login
$h = @{ Authorization = "Bearer $($login.data.token)"; "X-User-Id" = "1"; "X-User-Role" = "SYSTEM_ADMIN" }

# 2) 创建实训室
Invoke-RestMethod -Method Post -ContentType 'application/json' -Headers $h -Body '{"code":"SX-102","name":"软件技术实训室","capacity":50}' http://localhost:9000/api/v1/rooms | Out-Null

# 3) 提交预约（applicant=5 学生）
$b = '{"roomId":1,"purpose":"实训课","startTime":"2026-09-10T09:00:00","endTime":"2026-09-10T11:00:00","peopleNum":40}'
Invoke-RestMethod -Method Post -ContentType 'application/json' -Headers $h -Body $b http://localhost:9000/api/v1/reservations | Out-Null

# 4) 管理员审批
Invoke-RestMethod -Method Post -Headers $h http://localhost:9000/api/v1/reservations/1/approve | Out-Null

# 5) 统计可见
(Invoke-RestMethod -Headers $h http://localhost:9000/api/v1/stats/overview).data.totalApproved   # 期望 >= 1
```

- [ ] **步骤 3：端到端验收——报修链路**

```powershell
# 1) 创建设备依附某实训室
Invoke-RestMethod -Method Post -ContentType 'application/json' -Headers $h -Body '{"code":"PC-001","name":"教学电脑","roomId":1,"category":"COMPUTER"}' http://localhost:9000/api/v1/devices | Out-Null

# 2) 提交报修
Invoke-RestMethod -Method Post -ContentType 'application/json' -Headers $h -Body '{"deviceId":1,"roomId":1,"reporterId":5,"description":"开机黑屏"}' http://localhost:9000/api/v1/repairs | Out-Null

# 3) 派单 → 完成 → 验收
$assign = Invoke-RestMethod -Method Post -Headers $h 'http://localhost:9000/api/v1/repairs/1/assign?assigneeId=7' | Out-Null
Invoke-RestMethod -Method Post -Headers $h 'http://localhost:9000/api/v1/repairs/1/finish?result=已更换主板' | Out-Null
Invoke-RestMethod -Method Post -Headers $h 'http://localhost:9000/api/v1/repairs/1/verify' | Out-Null

# 4) 统计可见
(Invoke-RestMethod -Headers $h http://localhost:9000/api/v1/stats/overview).data.repairCompleted  # 期望 >= 1
```

- [ ] **步骤 4：浏览器验证前端链路**

打开 `http://localhost:5173`，用 admin/123456 登录，依次验证：
1. 系统管理 → 用户管理：Excel 导入学生账号
2. 实训室台账：新增"软件技术实训室"
3. 预约模块：提交预约 → 审批中心通过 → 工作台/统计看到数据
4. 报修模块：提交报修 → 派单 → 完成 → 验收
5. 统计报表：图表展示
6. 退出后用学生账号登录验证"仅只读、菜单收窄"

- [ ] **步骤 5：最终 Commit**

```bash
git add scripts
git commit -m "feat: 一键启动脚本与端到端验收"
```

---

## 自检记录

- **规格覆盖度**：规格 6 大模块均有对应任务（任务 4 系统管理、任务 5 台账/耗材、任务 6-7 预约/报修、任务 8 统计、任务 3-4 用户认证）；RBAC 由网关鉴权 + 前端路由 roles 实现（任务 9/10/11）；Excel 导入在任务 4；验收链路在任务 12。
- **占位符扫描**：无 TODO/待定描述；每个代码步骤均含实际代码。
- **类型一致性**：`Result`/`BizException`/`JwtUtil` 在 lab-common 定义，各服务统一引用；状态码常量 `PENDING/APPROVED/REJECTED/CANCELLED/COMPLETED` 与 `ASSIGNED/VERIFIED` 在任务 6/7 测试中一致；`rabbitTemplate.convertAndSend` 的 Map 键在任务 8 消费端一致（`roomId/status`），`_routingKey` 由商品端发布时置入（实现时在 `publish` 中 `payload.put("_routingKey", routingKey)`）。
