@echo off
setlocal

:: Automatically find the first JAR file in the current directory
for %%f in (*.jar) do (
    set "JAR_FILE=%%f"
    goto :found
)

:: Check subfolders if not found in root
for /r %%f in (*.jar) do (
    set "JAR_FILE=%%f"
    goto :found
)

echo ❌ Error: No .jar file detected automatically.
pause
exit /b

:found
echo 🔍 Detected JAR file: %JAR_FILE%
echo 🚀 Starting DIXY deadlock, thread, and pinning detection...

:: Run DIXY with the discovered JAR
call run.bat "%JAR_FILE%"
endlocal