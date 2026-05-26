@echo off
chcp 65001 >nul
title 开发环境验证脚本

echo ========================================
echo     开发环境验证脚本
echo     应用依赖安全与合规分析平台
echo ========================================
echo.
echo 正在验证，请稍候...
echo.

set ALL_PASSED=true

echo [1/5] 验证 JDK ...
java -version 2>&1 | findstr "version" >nul
if %errorlevel%==0 (
    for /f "tokens=3" %%i in ('java -version 2^>^&1 ^| findstr "version"') do (
        set JAVA_VER=%%i
    )
    echo ✅ JDK 安装成功
    java -version 2>&1 | findstr "version"
) else (
    echo ❌ JDK 未安装或未配置环境变量
    echo    原因：'java' 命令无法识别
    echo    解决：请检查 JAVA_HOME 环境变量是否配置正确
    set ALL_PASSED=false
)

echo.
echo [2/5] 验证 Maven ...
mvn -version 2>&1 | findstr "Maven home" >nul
if %errorlevel%==0 (
    echo ✅ Maven 安装成功
    mvn -version 2>&1 | findstr "Maven home"
) else (
    echo ❌ Maven 未安装或未配置环境变量
    echo    原因：'mvn' 命令无法识别
    echo    解决：请检查 Maven 的 bin 目录是否已添加到 PATH
    set ALL_PASSED=false
)

echo.
echo [3/5] 验证 Git ...
git --version 2>&1 | findstr "git version" >nul
if %errorlevel%==0 (
    echo ✅ Git 安装成功
    git --version
) else (
    echo ❌ Git 未安装或未配置环境变量
    echo    原因：'git' 命令无法识别
    echo    解决：请重新安装 Git，并勾选 "Add Git to PATH"
    set ALL_PASSED=false
)

echo.
echo [4/5] 验证 Node.js ...
node -v 2>&1 | findstr "v" >nul
if %errorlevel%==0 (
    echo ✅ Node.js 安装成功
    for /f "tokens=1" %%i in ('node -v') do echo   Node.js 版本: %%i
) else (
    echo ❌ Node.js 未安装或未配置环境变量
    echo    原因：'node' 命令无法识别
    echo    解决：请重新安装 Node.js，并确保勾选 "Add to PATH"
    set ALL_PASSED=false
)

echo.
echo [5/5] 验证 Docker ...
docker --version 2>&1 | findstr "Docker version" >nul
if %errorlevel%==0 (
    echo ✅ Docker 安装成功
    docker --version
) else (
    echo ❌ Docker 未安装或未启动
    echo    原因：'docker' 命令无法识别
    echo    解决：请确保 Docker Desktop 已安装并启动
    set ALL_PASSED=false
)

echo.
echo ========================================
if "%ALL_PASSED%"=="true" (
    echo 🎉 恭喜！所有环境验证通过！
    echo    你可以开始进行项目开发了！
) else (
    echo ⚠️  部分环境验证未通过
    echo    请根据上面的 ❌ 提示信息解决问题后重新运行本脚本
)
echo ========================================
echo.
echo 按任意键退出...
pause >nul
