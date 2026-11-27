@echo off
setlocal
where gradle >nul 2>nul
if %ERRORLEVEL% EQU 0 (
  gradle %*
  exit /b %ERRORLEVEL%
)
set GRADLE_VERSION=8.9
set BASE_DIR=%USERPROFILE%\.gradle\caches\galaxy-gradle
set GRADLE_HOME_DIR=%BASE_DIR%\gradle-%GRADLE_VERSION%
if not exist "%GRADLE_HOME_DIR%\bin\gradle.bat" (
  if not exist "%BASE_DIR%" mkdir "%BASE_DIR%"
  powershell -NoProfile -ExecutionPolicy Bypass -Command "Invoke-WebRequest 'https://services.gradle.org/distributions/gradle-%GRADLE_VERSION%-bin.zip' -OutFile '%BASE_DIR%\gradle-%GRADLE_VERSION%-bin.zip'; Expand-Archive -Force '%BASE_DIR%\gradle-%GRADLE_VERSION%-bin.zip' '%BASE_DIR%'"
  if errorlevel 1 exit /b 1
)
call "%GRADLE_HOME_DIR%\bin\gradle.bat" --no-daemon %*
exit /b %ERRORLEVEL%
