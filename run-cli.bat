@echo off
setlocal enabledelayedexpansion

rem Run relative to this script's own location instead of a hardcoded
rem drive path, so it works regardless of where the repo is cloned.
cd /d "%~dp0"

call gradlew.bat bootJar
if errorlevel 1 (
    echo.
    echo [run-cli] Build failed - aborting.
    pause
    exit /b 1
)

docker compose up -d postgres redis
if errorlevel 1 (
    echo.
    echo [run-cli] Failed to start postgres/redis containers - aborting.
    pause
    exit /b 1
)

rem Wait for Postgres to report healthy (per docker-compose.yml's
rem pg_isready healthcheck) instead of racing ahead into a
rem connection-refused error on first launch.
echo [run-cli] Waiting for postgres to become healthy...
set /a attempts=0
:wait_for_postgres
set /a attempts+=1
for /f "delims=" %%H in ('docker inspect --format "{{.State.Health.Status}}" vdd-postgres 2^>nul') do set pg_status=%%H
if "!pg_status!"=="healthy" goto postgres_ready
if !attempts! GEQ 30 (
    echo.
    echo [run-cli] Timed out waiting for postgres to become healthy.
    pause
    exit /b 1
)
timeout /t 2 >nul
goto wait_for_postgres

:postgres_ready
echo [run-cli] postgres is healthy.

rem Credentials default to postgres/gupi@1234 in application.yml already
rem (matching docker-compose.yml) - no need to duplicate them here.
java "-Duser.timezone=UTC" "-Dspring.profiles.active=cli" -jar build\libs\virtual_deadlock_detection-0.0.1-SNAPSHOT.jar
pause