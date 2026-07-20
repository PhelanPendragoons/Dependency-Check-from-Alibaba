@echo off
chcp 65001 >nul
title 依赖检查平台 - 演示启动
echo ================================================
echo   依赖安全与合规分析平台 - 演示环境启动
echo ================================================
echo.

set JAVA_HOME=C:\Program Files\Java\zulu17.56.15-ca-jdk17.0.14-win_x64
set JAVA=%JAVA_HOME%\bin\java
set JAR=dependency-check-web\target\dependency-check-web-1.0.0.jar
if not defined NVD_API_KEY (
    if exist dependency-check-web\nvd-api.properties (
        for /f "tokens=2 delims==" %%K in ('findstr /b "dependency-check.nvd-api-key" dependency-check-web\nvd-api.properties') do set NVD_API_KEY=%%K
    )
)
if not defined NVD_API_KEY (
    echo [提示] 未设置 NVD API Key（演示模式使用本地缓存，无需联网）
)

:: 演示模式：NVD 缓存已预热（data\dc-cache\odc.mv.db ~250MB），
:: 关闭自动更新后扫描完全离线，不依赖 VPN / NVD API / 镜像服务
set NVD_AUTO_UPDATE=false

:: 7/20 修复：启动本地 NVD 镜像 HTTP 服务器（引擎通过 datafeed channel 读取缓存）
:: 缺少此项时扫描会在初始化阶段同秒失败（NVD 数据无法访问）
echo [0/3] 启动 NVD 本地镜像服务 (:8888) ...
if exist dependency-check-web\data\nvd-mirror\cache.properties (
    start "NVD-Mirror" cmd /c "cd /d %~dp0dependency-check-web\data\nvd-mirror && python -m http.server 8888 --bind 127.0.0.1"
    set NVD_DATAFEED_URL=http://127.0.0.1:8888/nvdcve-2.0-{0}.json.gz
    echo NVD 镜像服务已启动
) else (
    echo [警告] NVD 镜像目录不存在，请先运行 nvd-warmup.bat 预热缓存
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

echo [1/3] 启动后端服务 (:8080) ...
start "Backend" "%JAVA%" -jar "%JAR%"
echo 后端启动中，等待 20 秒...

timeout /t 20 /nobreak >nul

echo [2/3] 启动前端服务 (:3000) ...
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
