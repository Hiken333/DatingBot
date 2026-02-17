@echo off
echo Starting Buhlo Bot...
echo.

REM Set environment variables
set DB_PASSWORD=buhlo_password
set REDIS_PASSWORD=buhlo_password

echo Environment variables set:
echo DB_PASSWORD=%DB_PASSWORD%
echo REDIS_PASSWORD=%REDIS_PASSWORD%
echo.

REM Clean and compile first
echo Cleaning and compiling...
mvn clean compile -DskipTests

if %ERRORLEVEL% neq 0 (
    echo Compilation failed!
    pause
    exit /b 1
)

echo.
echo Starting application...
mvn spring-boot:run

pause
