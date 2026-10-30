@echo off
where gradle >nul 2>nul
if %ERRORLEVEL% equ 0 (
    gradle %*
    exit /b %ERRORLEVEL%
)

if exist "%~dp0gradle\wrapper\gradle-wrapper.jar" (
    java -jar "%~dp0gradle\wrapper\gradle-wrapper.jar" %*
    exit /b %ERRORLEVEL%
)

echo Error: Neither 'gradle' command nor 'gradle-wrapper.jar' was found.
exit /b 1
