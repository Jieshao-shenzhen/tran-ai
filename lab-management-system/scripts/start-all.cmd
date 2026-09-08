@echo off
rem ============================================
rem  实训室管理系统 - 一键启动脚本
rem  顺序：Nacos -> RabbitMQ -> 打包 -> 5 个服务 -> 前端
rem  停止请运行 scripts\stop-all.cmd
rem ============================================

rem 1) Nacos 注册中心
start "nacos" cmd /k "cd /d C:\nacos\nacos\bin && startup.cmd -m standalone"

rem 2) RabbitMQ 消息队列
net start RabbitMQ 2>nul

rem 3) 打包全部服务（cwd 为 lab-management-system；产物已存在则跳过）
cd /d %~dp0..
if not exist lab-gateway\target\lab-gateway-1.0.0.jar (
  echo [打包] 首次运行需编译全部服务，请耐心等待...
  call .\mvnw.cmd -f pom.xml -pl lab-user-service,lab-resource-service,lab-business-service,lab-report-service,lab-gateway -am package -DskipTests
  if errorlevel 1 (
    echo BUILD FAILED - 请检查上方 Maven 报错
    pause
    exit /b 1
  )
) else (
  echo [打包] 产物已存在，跳过编译。
)

rem 4) 后台启动 5 个服务（窗口标题供 stop-all 匹配）
start "user"     cmd /k "java -jar lab-user-service\target\lab-user-service-1.0.0.jar"
start "resource" cmd /k "java -jar lab-resource-service\target\lab-resource-service-1.0.0.jar"
start "business" cmd /k "java -jar lab-business-service\target\lab-business-service-1.0.0.jar"
start "report"   cmd /k "java -jar lab-report-service\target\lab-report-service-1.0.0.jar"
start "gateway"  cmd /k "java -jar lab-gateway\target\lab-gateway-1.0.0.jar"

rem 5) 前端开发服务器
start "frontend" cmd /k "cd /d %~dp0..\frontend && npm run dev"

echo.
echo Started. Frontend: http://localhost:5173  Gateway: http://localhost:9000
echo 注册中心: http://localhost:8848  RabbitMQ: http://localhost:15672
echo 如需停止全部服务，请运行 scripts\stop-all.cmd