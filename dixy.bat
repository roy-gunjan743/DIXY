@echo off
setlocal enabledelayedexpansion
rem ─────────────────────────────────────────────────────────────────
rem  DIXY launcher (Windows) — attaches the DIXY javaagent to a
rem  target JVM app, then opens the DIXY CLI against it.
rem
rem  Usage:
rem    dixy.bat                     (auto-detect target jar in cwd)
rem    dixy.bat path\to\app.jar     (explicit target)
rem ─────────────────────────────────────────────────────────────────

cd /d "%~dp0"
set "AGENT_JAR=%~dp0dixy-agent.jar"
set "CLI_JAR=%~dp0dixy-cli.jar"
set "LOG_FILE=%~dp0.dixy-target.log"

if not exist "%AGENT_JAR%" (
    echo [ERROR] dixy-agent.jar not found next to this script.
    exit /b 1
)
if not exist "%CLI_JAR%" (
    echo [ERROR] dixy-cli.jar not found next to this script.
    exit /b 1
)

if /i "%~1"=="agent-path" (
    echo %AGENT_JAR%
    exit /b 0
)

if /i "%~1"=="version" (
    echo DIXY 0.0.1-SNAPSHOT
    exit /b 0
)

if /i "%~1"=="cli" (
    echo [dixy] Launching DIXY CLI...
    java -jar "%CLI_JAR%"
    exit /b %ERRORLEVEL%
)

rem ---- Step 1: locate the target jar ---------------------------------
set "TARGET_JAR=%~1"

if "%TARGET_JAR%"=="" (
    echo [dixy] No target jar given - scanning current directory...
    set "FOUND="
    set "COUNT=0"
    for %%f in (*.jar) do (
        if /i not "%%~nxf"=="dixy-agent.jar" if /i not "%%~nxf"=="dixy-cli.jar" (
            set /a COUNT+=1
            set "CANDIDATE!COUNT!=%%f"
        )
    )
    if "!COUNT!"=="0" (
        echo [dixy] Launching DIXY CLI standalone...
        java -jar "%CLI_JAR%"
        exit /b %ERRORLEVEL%
    )
    if "!COUNT!"=="1" (
        set "TARGET_JAR=!CANDIDATE1!"
    ) else (
        echo [dixy] Multiple jars found - pick one:
        for /l %%i in (1,1,!COUNT!) do echo   %%i^) !CANDIDATE%%i!
        set /p CHOICE=Enter number:
        set "TARGET_JAR=!CANDIDATE%CHOICE%!"
    )
)

echo [dixy] Target application: %TARGET_JAR%

rem ---- Step 2: launch the target with the DIXY agent attached --------
echo [dixy] Starting target JVM with -javaagent:dixy-agent.jar ...
start "DIXY Target" /min cmd /c "java -javaagent:"%AGENT_JAR%" -jar "%TARGET_JAR%" > "%LOG_FILE%" 2>&1"

rem Give the agent a moment to initialize, then confirm via the log
echo [dixy] Waiting for agent handshake...
set /a ATTEMPTS=0
:wait_loop
set /a ATTEMPTS+=1
findstr /c:"Agent Components Initialized Successfully" "%LOG_FILE%" >nul 2>&1
if not errorlevel 1 (
    echo [dixy] Agent is live.
    goto agent_ready
)
if !ATTEMPTS! GEQ 30 (
    echo [WARN] Agent handshake not confirmed after 30s - opening CLI anyway.
    goto agent_ready
)
timeout /t 1 >nul
goto wait_loop

:agent_ready
rem ---- Step 3: open the DIXY CLI --------------------------------------
echo [dixy] Launching DIXY CLI...
java -Dspring.profiles.active=cli -jar "%CLI_JAR%"

endlocal