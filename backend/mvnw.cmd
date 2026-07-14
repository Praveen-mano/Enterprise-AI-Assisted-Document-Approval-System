@echo off
setlocal

set "MVN_VERSION=3.9.9"
set "BASE_DIR=%~dp0"
set "MVN_HOME=%BASE_DIR%.mvn\apache-maven-%MVN_VERSION%"
set "MVN_CMD=%MVN_HOME%\bin\mvn.cmd"
set "MVN_ZIP=%BASE_DIR%.mvn\apache-maven-%MVN_VERSION%-bin.zip"
set "MVN_URL=https://archive.apache.org/dist/maven/maven-3/%MVN_VERSION%/binaries/apache-maven-%MVN_VERSION%-bin.zip"

where java >nul 2>nul
if errorlevel 1 (
  echo Java was not found. Install Java 21 first, then run this command again.
  echo Recommended with Scoop: scoop install temurin21-jdk
  exit /b 1
)

if exist "%MVN_CMD%" goto run_maven

echo Local Maven was not found. Downloading Apache Maven %MVN_VERSION%...
if not exist "%BASE_DIR%.mvn" mkdir "%BASE_DIR%.mvn"

powershell -NoProfile -ExecutionPolicy Bypass -Command "$ProgressPreference='SilentlyContinue'; Invoke-WebRequest -Uri '%MVN_URL%' -OutFile '%MVN_ZIP%'"
if errorlevel 1 (
  echo Could not download Maven. Check your internet connection or install Maven globally.
  exit /b 1
)

powershell -NoProfile -ExecutionPolicy Bypass -Command "Expand-Archive -LiteralPath '%MVN_ZIP%' -DestinationPath '%BASE_DIR%.mvn' -Force"
if errorlevel 1 (
  echo Could not extract Maven zip.
  exit /b 1
)

:run_maven
call "%MVN_CMD%" %*
