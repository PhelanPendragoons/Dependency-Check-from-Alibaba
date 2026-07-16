@echo off
chcp 65001 >nul
echo ================================================
echo   NVD 数据库状态检查
echo ================================================
echo.

set CACHE_DIR=dependency-check-web\data\dc-cache
set DB_FILE=%CACHE_DIR%\odc.mv.db
:: 完整 NVD 库为数百 MB，小于 100MB 视为残缺（下载中断/仅建了空表）
set /a MIN_SIZE_MB=100

if not exist "%CACHE_DIR%" (
    echo [!!] NVD 缓存目录不存在: %CACHE_DIR%
    echo.
    goto :instructions
)

if not exist "%DB_FILE%" (
    echo [!!] NVD 缓存目录存在但无 odc.mv.db 文件
    echo.
    goto :instructions
)

:: 获取 odc.mv.db 大小（字节）并换算为 MB
for %%A in ("%DB_FILE%") do set DB_SIZE=%%~zA
set /a DB_SIZE_MB=0
if %DB_SIZE% GEQ 1000000 set /a DB_SIZE_MB=%DB_SIZE:~0,-6%

if %DB_SIZE_MB% LSS %MIN_SIZE_MB% (
    echo [!!] odc.mv.db 仅 %DB_SIZE% 字节（约 %DB_SIZE_MB% MB^），小于 %MIN_SIZE_MB% MB
    echo      判定为残缺缓存（下载中断或仅初始化了空库）
    echo.
    goto :instructions
)

echo [OK] NVD 缓存已就绪！
echo.
echo 缓存文件：odc.mv.db  大小约 %DB_SIZE_MB% MB（%DB_SIZE% 字节）
echo.
dir "%CACHE_DIR%" 2>nul
echo.
echo [OK] 可以开始扫描，漏洞检测将使用缓存数据（秒级响应）
goto :end

:instructions
echo 需要执行 NVD 预热扫描。
echo.
echo 操作步骤：
echo   1. set NVD_API_KEY=your-key
echo      （Key 保存在 dependency-check-web\nvd-api.properties，勿提交 Git）
echo.
echo   2. 连接 VPN（日/新/美节点）后启动后端：
echo      cd dependency-check-web
echo      mvn spring-boot:run
echo.
echo   3. 在另一个终端，用 curl 触发预热扫描：
echo      curl -X POST "http://localhost:8080/api/tasks?projectId=1"
echo      （需先上传一个项目，或在 Web UI 操作）
echo.
echo   4. 等待扫描完成（首次约 10-30 分钟，后续秒级）
echo.
echo   5. 再次运行本脚本确认缓存已生成
goto :end

:end
echo.
pause
