@echo off
REM Postgres/RDS + optional Gemini/VLM env from your existing strange_back\.env
REM Does NOT use local-h2. Set variables in this shell before bootRun (Spring does not load .env).
setlocal
cd /d "%~dp0.."

if "%SPRING_PROFILES_ACTIVE%"=="" set SPRING_PROFILES_ACTIVE=local
if "%JWT_SECRET%"=="" (
  echo [bootRun_postgres_gemini] Set JWT_SECRET, DB_URL, DB_PASSWORD from your .env first.
  exit /b 1
)

echo Profile=%SPRING_PROFILES_ACTIVE% DB_URL=%DB_URL%
call gradlew.bat bootRun
endlocal