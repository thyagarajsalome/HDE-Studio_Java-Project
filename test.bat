@echo off
echo =======================================================
echo   HDE Studio - Running Automated Unit Test Suite
echo =======================================================
javac -encoding UTF-8 -d . HousePlanCADApp.java test/CADTestSuite.java
if %errorlevel% neq 0 (
    echo [ERROR] Compilation failed!
    exit /b 1
)
java -Djava.awt.headless=true -cp . cadstudio.CADTestSuite
if %errorlevel% neq 0 (
    echo.
    echo [ERROR] Unit tests failed! Please fix issues before deploying.
    exit /b 1
)
echo.
echo [OK] All tests passed cleanly!
