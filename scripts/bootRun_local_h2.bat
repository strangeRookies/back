@echo off
setlocal
cd /d "%~dp0.."
set SPRING_PROFILES_ACTIVE=local-h2
set JWT_SECRET=local-development-jwt-secret-change-before-shared-use-32bytes
set DB_PASSWORD=
set AES_SECRET_KEY=StrangeSafetyKey
set AES_IV=StrangeSafetyIV16
call gradlew.bat bootRun
endlocal