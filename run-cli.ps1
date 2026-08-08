# Virtual Deadlock Detection CLI Launcher
# Run this directly from PowerShell for a fully interactive terminal

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $ScriptDir

Write-Host "Building application..." -ForegroundColor Cyan
& ".\gradlew.bat" bootJar
if ($LASTEXITCODE -ne 0) {
    Write-Host "Build failed!" -ForegroundColor Red
    exit 1
}

Write-Host "Starting Docker services..." -ForegroundColor Cyan
docker compose up -d postgres redis
Start-Sleep -Seconds 2

Write-Host "Starting Virtual Deadlock Detection CLI..." -ForegroundColor Green
Write-Host ""

$jar = "build\libs\virtual_deadlock_detection-0.0.1-SNAPSHOT.jar"

# Pass Spring Boot config as program arguments (--key=value), not JVM -D flags.
# This ensures Spring's profile mechanism activates correctly.
java `
    "-Duser.timezone=UTC" `
    "-Dspring.devtools.restart.enabled=false" `
    "-Dorg.jline.terminal.provider=jansi" `
    -jar $jar `
    "--spring.profiles.active=cli" `
    "--spring.datasource.username=postgres" `
    "--spring.datasource.password=gupi@1234"
