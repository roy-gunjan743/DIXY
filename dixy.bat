@echo off
setlocal enabledelayedexpansion
rem ─────────────────────────────────────────────────────────────────
rem  DIXY launcher (Windows) — attaches the DIXY javaagent to a
rem  target JVM app, then opens the DIXY CLI against it.
rem
rem  Usage:
rem    dixy.bat                     (auto-detect target jar: cwd, then build\libs, then target)
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

rem ---- Handle known flags FIRST, before anything treats %1 as a jar ----
if /i "%~1"=="agent-path" (
    echo %AGENT_JAR%
    exit /b 0
)

if /i "%~1"=="version" goto show_version
if /i "%~1"=="--version" goto show_version
if /i "%~1"=="-v" goto show_version
goto after_version

:show_version
echo DIXY 0.0.1-SNAPSHOT
exit /b 0

:after_version
if /i "%~1"=="cli" (
    echo [dixy] Launching DIXY CLI...
    java -jar "%CLI_JAR%"
    exit /b %ERRORLEVEL%
)

rem ---- Reject anything else that looks like an unrecognized flag ----
rem (prevents "--foo" from being silently treated as a jar filename)
set "FIRST_ARG=%~1"
if not "%FIRST_ARG%"=="" (
    set "FIRST_CHAR=%FIRST_ARG:~0,1%"
    if "!FIRST_CHAR!"=="-" (
        echo [ERROR] Unknown option: %FIRST_ARG%
        echo [dixy] Recognized commands: version, cli, agent-path
        echo [dixy] Or run with no arguments to auto-attach to a jar in this folder.
        exit /b 1
    )
)

rem ---- Step 1: locate the target jar ---------------------------------
set "TARGET_JAR=%~1"

if not "%TARGET_JAR%"=="" goto have_target

echo [dixy] No target jar given - searching for a runnable jar...

set "COUNT=0"

rem Search order: current dir, then common build output folders.
rem scan_dir stops contributing once a prior dir has already yielded matches.
for %%D in (. build\libs target) do call :scan_dir "%%D"

if !COUNT! GTR 0 goto have_candidates

echo [dixy] No target jar found in this folder, build\libs, or target - launching DIXY CLI standalone...
java -jar "%CLI_JAR%"
exit /b %ERRORLEVEL%

:have_candidates
if !COUNT! EQU 1 (
    set "TARGET_JAR=!CANDIDATE1!"
    echo [dixy] Auto-detected target: !TARGET_JAR!
    goto have_target
)

echo [dixy] Multiple candidate jars found - pick one:
for /l %%i in (1,1,!COUNT!) do call :print_candidate %%i
set /p CHOICE=Enter number:
set "TARGET_JAR=!CANDIDATE%CHOICE%!"
goto have_target

rem ---- subroutines used above (never fallen into; only reached via CALL) ----
:scan_dir
if !COUNT! GTR 0 goto :eof
if not exist "%~1" goto :eof
for %%f in ("%~1\*.jar") do call :consider_jar "%%f"
goto :eof

:consider_jar
set "FNAME=%~nx1"
rem Skip DIXY's own jars
if /i "!FNAME!"=="dixy-agent.jar" goto :eof
if /i "!FNAME!"=="dixy-cli.jar" goto :eof
rem Skip Spring Boot's non-executable "-plain.jar" companion
echo !FNAME! | findstr /i /c:"-plain.jar" >nul
if not errorlevel 1 goto :eof
set /a COUNT+=1
set "CANDIDATE!COUNT!=%~1"
goto :eof

:print_candidate
echo   %1) !CANDIDATE%1!
goto :eof

:have_target

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