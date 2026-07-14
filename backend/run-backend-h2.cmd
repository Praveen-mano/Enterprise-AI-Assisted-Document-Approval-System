@echo off
setlocal
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -Command "$env:SPRING_PROFILES_ACTIVE='local-h2'; & '%~dp0run-backend.ps1'"
