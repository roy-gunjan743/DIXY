@echo off
setlocal enabledelayedexpansion
set "SCRIPT_DIR=%~dp0"
set "CLI_JAR=%SCRIPT_DIR%dixy-cli.jar"
set "AGENT_JAR=%SCRIPT_DIR%dixy-agent.jar"
if /i "%~1"=="--version" goto version
if /i "%~1"=="version" goto version
if /i "%~1"=="-v" goto version
if not exist "%CLI_JAR%" (echo DIXY installation is incomplete: expected jars in %SCRIPT_DIR% 1>&2 & exit /b 1)
if not exist "%AGENT_JAR%" (echo DIXY installation is incomplete: expected jars in %SCRIPT_DIR% 1>&2 & exit /b 1)
set "JAVA_BIN=%JAVA_HOME%\bin\java.exe"
if not exist "%JAVA_BIN%" set "JAVA_BIN=java.exe"
"%JAVA_BIN%" -version >nul 2>&1
if errorlevel 1 (echo DIXY requires Java 21 or newer. Install Temurin 21 from https://adoptium.net/ and retry. 1>&2 & exit /b 1)
set "NON_INTERACTIVE=false"
set "SHELL_INTERACTIVE=true"
if /i "%~1"=="detect" set "NON_INTERACTIVE=true"
if /i "%~1"=="detect" set "SHELL_INTERACTIVE=false"
if "%~1"=="" goto launch_cli
if /i "%~1"=="detect" goto launch_cli
if /i "%~1"=="--version" goto launch_cli
if /i "%~1"=="version" goto launch_cli
if /i "%~1"=="-v" goto launch_cli
call :start_target "%~1"
if errorlevel 1 goto launcher_error
shift

:launch_cli
set "ACTIVE_PROFILE=cli"
if /i "%~1"=="detect" set "ACTIVE_PROFILE=web"
"%JAVA_BIN%" -Dspring.profiles.active=%ACTIVE_PROFILE% -Dspring.shell.interactive.enabled=%SHELL_INTERACTIVE% -Ddixy.agent.path="%AGENT_JAR%" -Ddixy.noninteractive=%NON_INTERACTIVE% -jar "%CLI_JAR%" %*
exit /b %ERRORLEVEL%

:launcher_error
exit /b 1

:start_target
set "TARGET_ROOT=%~1"
if exist "%TARGET_ROOT%\*.jar" goto find_target
if /i "%TARGET_ROOT:~-4%"==".jar" if exist "%TARGET_ROOT%" (
	set "TARGET_JAR=%TARGET_ROOT%"
	goto launch_target
)
echo [dixy] Target root does not exist or contains no jar: %TARGET_ROOT% 1>&2
exit /b 1

:find_target
set "COUNT=0"
for %%F in ("%TARGET_ROOT%\*.jar") do call :consider_target "%%~fF"
if "%COUNT%"=="0" (
	echo [dixy] No runnable jar found in %TARGET_ROOT% 1>&2
	exit /b 1
)
if "%COUNT%"=="1" (
	set "TARGET_JAR=!CANDIDATE1!"
	goto launch_target
)
echo [dixy] Select the target jar:
for /l %%I in (1,1,%COUNT%) do echo   %%I^) !CANDIDATE%%I!
set /p TARGET_CHOICE=Enter number:
set "TARGET_JAR=!CANDIDATE%TARGET_CHOICE%!"
if not defined TARGET_JAR (
	echo [dixy] Invalid target selection 1>&2
	exit /b 1
)

:launch_target
echo [dixy] Starting target: %TARGET_JAR%
start "DIXY target" /b "%JAVA_BIN%" -javaagent:"%AGENT_JAR%"=serverUrl=http://localhost:8081,apiKey=dixy-local-dev-key-123,appName=dixy-target -jar "%TARGET_JAR%"
echo [dixy] Target started. Use 'detect' in the dixy shell for live scanning.
exit /b 0

:consider_target
set "TARGET_NAME=%~nx1"
if /i "%TARGET_NAME%"=="dixy-agent.jar" exit /b 0
if /i "%TARGET_NAME%"=="dixy-cli.jar" exit /b 0
echo "%TARGET_NAME%" | findstr /i /c:"-plain.jar" >nul && exit /b 0
set /a COUNT+=1
set "CANDIDATE%COUNT%=%~1"
exit /b 0
:version
if exist "%SCRIPT_DIR%VERSION" (
	type "%SCRIPT_DIR%VERSION"
) else (
	echo DIXY development build
)
exit /b 0
