@echo off
cd /d "%~dp0UniversalServer"
start "UniversalServerLoader" javaw -jar universal-loader.jar gui
