@echo off
REM ─────────────────────────────────────────────────────────────────────
REM  Virtual Deadlock Detection — Windows startup script
REM  Usage:
REM    run.bat                         (uses DB_PASSWORD already in env)
REM    run.bat mySecretPassword        (pass password as first argument)
REM ─────────────────────────────────────────────────────────────────────

IF NOT "%~1"=="" (
    set DB_PASSWORD=%~1
)

IF "%DB_PASSWORD%"=="" (
    echo [ERROR] DB_PASSWORD is not set.
    echo   Usage: run.bat ^<password^>
    echo          or set DB_PASSWORD=^<password^> before running.
    exit /b 1
)

echo [INFO] Starting Virtual Deadlock Detection...
echo [INFO] DB_PASSWORD is set.

java -jar build\libs\virtual_deadlock_detection-0.0.1-SNAPSHOT.jar
