@echo off
setlocal
set VERSION=8.13
set BASE=%USERPROFILE%\.gradle\life-track-bootstrap
set DIST=%BASE%\gradle-%VERSION%
set GRADLE=%DIST%\bin\gradle.bat
if not exist "%GRADLE%" (
  if not exist "%BASE%" mkdir "%BASE%"
  echo Downloading Gradle %VERSION% (first run only)...
  powershell -NoProfile -ExecutionPolicy Bypass -Command "$ProgressPreference='SilentlyContinue'; Invoke-WebRequest 'https://services.gradle.org/distributions/gradle-%VERSION%-bin.zip' -OutFile '%BASE%\gradle-%VERSION%-bin.zip'; Expand-Archive -Force '%BASE%\gradle-%VERSION%-bin.zip' '%BASE%'"
  if errorlevel 1 exit /b 1
)
call "%GRADLE%" %*
exit /b %ERRORLEVEL%
