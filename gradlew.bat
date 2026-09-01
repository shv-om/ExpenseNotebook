@echo off
setlocal
set "GRADLE_VERSION=8.9"
set "DIST_DIR=%USERPROFILE%\.gradle\wrapper\dists\gradle-8.9-bin\expense-notebook"
set "GRADLE_EXE=%DIST_DIR%\gradle-8.9\bin\gradle.bat"
set "ARCHIVE=%DIST_DIR%\gradle-8.9-bin.zip"

if not exist "%GRADLE_EXE%" (
    if not exist "%DIST_DIR%" mkdir "%DIST_DIR%"
    if not exist "%ARCHIVE%" (
        powershell -NoProfile -ExecutionPolicy Bypass -Command "$ProgressPreference='SilentlyContinue'; Invoke-WebRequest -UseBasicParsing 'https://services.gradle.org/distributions/gradle-8.9-bin.zip' -OutFile '%ARCHIVE%'"
        if errorlevel 1 exit /b 1
    )
    powershell -NoProfile -ExecutionPolicy Bypass -Command "Expand-Archive -Force '%ARCHIVE%' '%DIST_DIR%'"
    if errorlevel 1 exit /b 1
)

call "%GRADLE_EXE%" -p "%~dp0" %*
exit /b %ERRORLEVEL%
