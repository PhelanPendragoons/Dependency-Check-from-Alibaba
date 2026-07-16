@echo off
chcp 65001 >nul
title 依赖检查平台 - 演示启动
echo ================================================
echo   依赖安全与合规分析平台 - 演示环境启动
echo ================================================
echo.

set JAVA_HOME=C:\Program Files\Java\zulu17.56.15-ca-jdk17.0.14-win_x64
set JAVA=%JAVA_HOME%\bin\java
set JAR=dependency-check-web\target\dependency-check-web-1.0.0-SNAPSHOT.jar
if not defined NVD_API_KEY (
    if exist dependency-check-web\nvd-api.properties (
        for /f "tokens=2 delims==" %%K in ('findstr /b "dependency-check.nvd-api-key" dependency-check-web\nvd-api.properties') do set NVD_API_KEY=%%K
    )
)
if not defined NVD_API_KEY (
    echo [警告] 未找到 NVD API Key，扫描将无法更新 NVD 数据（已有缓存则不影响演示）
)

if not exist "%JAR%" (
    echo [错误] JAR 包不存在，正在构建...
    cd dependency-check-web
    call mvn package -DskipTests -q
    cd ..
    if not exist "%JAR%" (
        echo [错误] 构建失败
        pause
        exit /b 1
    )
)

echo [1/2] 启动后端服务 (:8080) ...
start "Backend" "%JAVA%" -jar "%JAR%"
echo 后端启动中，等待 20 秒...

timeout /t 20 /nobreak >nul

echo [2/2] 启动前端服务 (:3000) ...
cd dependency-check-web\frontend
start "Frontend" cmd /c "npm run dev"
cd ..\..

echo.
echo ================================================
echo   演示环境启动完成！
echo   后端: http://localhost:8080
echo   前端: http://localhost:3000
echo   Swagger: http://localhost:8080/swagger-ui.html
echo   健康检查: http://localhost:8080/actuator/health
echo ================================================
echo.
echo 按任意键退出...
pause >nul
