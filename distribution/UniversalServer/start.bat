@echo off
cd /d "%~dp0"
where java >nul 2>nul || (echo Java 21 est requis. & pause & exit /b 1)
start "UniversalServerLoader" javaw -jar universal-loader.jar gui
