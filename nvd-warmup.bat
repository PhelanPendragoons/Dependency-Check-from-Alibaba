@echo off
chcp 65001 >nul
title NVD 数据库预热脚本
echo ================================================
echo   NVD 数据库预热脚本
echo ================================================
echo.
echo 步骤 1: 检查 Java 环境
echo.

set JAVA_HOME=C:\Program Files\Java\zulu17.56.15-ca-jdk17.0.14-win_x64
set JAVA=%JAVA_HOME%\bin\java

if not exist "%JAVA%" (
    echo [错误] 找不到 Java: %JAVA%
    echo 请修改脚本中的 JAVA_HOME 路径
    pause
    exit /b 1
)

echo [OK] Java 路径: %JAVA%

echo.
echo 步骤 2: 设置 NVD API Key
echo.

if not defined NVD_API_KEY (
    if exist dependency-check-web\nvd-api.properties (
        for /f "tokens=2 delims==" %%K in ('findstr /b "dependency-check.nvd-api-key" dependency-check-web\nvd-api.properties') do set NVD_API_KEY=%%K
    )
)
if not defined NVD_API_KEY (
    echo [错误] 未找到 NVD API Key
    echo 请先 set NVD_API_KEY=your-key 或在 dependency-check-web\nvd-api.properties 中配置
    pause
    exit /b 1
)
echo [OK] API Key 已设置

echo.
echo 步骤 3: 编译 NVD 初始化程序
echo.

set JAR=dependency-check-web\target\dependency-check-web-1.0.0-SNAPSHOT.jar

"%JAVA%" -cp "%JAR%" --add-opens java.base/java.lang=ALL-UNNAMED NvdInit.java data\dc-cache 2>&1

if %errorlevel% neq 0 (
    echo.
    echo [失败] NVD 数据库预热失败，请检查上方错误信息
    echo.
    echo 常见原因:
    echo   1. VPN/漫游连接问题 — 尝试切换节点
    echo   2. API Key 无效 — 检查 nvd-api.properties
    echo   3. 网络超时 — 重试
    pause
    exit /b 1
)

echo.
echo ================================================
echo   [成功] NVD 数据库预热完成！
echo ================================================
echo.
echo 现在可以启动 Web 服务进行演示:
echo   cd dependency-check-web
echo   mvn spring-boot:run
echo.
pause
