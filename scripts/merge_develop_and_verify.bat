@echo off
setlocal
cd /d "%~dp0.."

echo === strange_back: fix/vlm-media-source-contract ===
git status -sb
if errorlevel 1 exit /b 1

git fetch origin develop
if errorlevel 1 exit /b 1

git merge origin/develop --no-edit
if errorlevel 1 (
  echo.
  echo MERGE CONFLICT. Resolve files, then: git add -A ^&^& git commit
  exit /b 1
)

call gradlew.bat runVlmProcessingSchedulerTests --rerun-tasks
if errorlevel 1 exit /b 1

echo.
echo Tests OK. Push with:
echo   git push origin fix/vlm-media-source-contract
endlocal