@echo off
cd /d "%~dp0"
echo Compiling HousePlanCADApp...
javac -d . HousePlanCADApp.java
if %errorlevel% neq 0 (
    echo Compilation failed.
    pause
    exit /b %errorlevel%
)
echo Launching CAD Studio...
start javaw cadstudio.HousePlanCADApp
