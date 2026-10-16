@echo off
if "%1"=="" goto usage

javac -encoding UTF-8 -d . tools/GenerateLicense.java
java -cp . tools.GenerateLicense %*
goto end

:usage
echo =======================================================
echo   HDE Studio Official Commercial License Generator
echo =======================================================
echo Usage: generate-license.bat customer_email plan [valid_days]
echo.
echo Available Plans:
echo   GLOBAL_PRO  - All-Access Global Pro Edition (India + USA)
echo   USA_PRO     - USA and International Edition
echo   IND_PRO     - India Standard Edition
echo.
echo Valid Days: 0 for Lifetime, or e.g. 365 for 1 Year
echo Example: generate-license.bat customer@gmail.com GLOBAL_PRO 0
echo =======================================================
exit /b 1

:end
