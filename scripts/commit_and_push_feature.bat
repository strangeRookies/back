@echo off
setlocal
cd /d "%~dp0.."

echo Branch:
git branch --show-current
git status -sb
echo.

git add src/main/java/com/strange/safety/vlm/service/VlmProcessingScheduler.java
git add src/test/java/com/strange/safety/vlm/service/VlmProcessingSchedulerTest.java
git add src/test/java/com/strange/safety/vlm/service/VlmProcessingSchedulerTestRunner.java
git add build.gradle
git add scripts/merge_develop_and_verify.bat
git add scripts/commit_and_push_feature.bat

git diff --cached --stat
echo.
set /p CONFIRM=Commit these changes? [Y/N]:
if /I not "%CONFIRM%"=="Y" (
  echo Aborted.
  exit /b 0
)

git commit -m "feat(vlm): align clip range metadata with AI process_vlm contract"
if errorlevel 1 (
  echo Nothing to commit or commit failed.
)

git fetch origin develop
git merge origin/develop --no-edit
if errorlevel 1 (
  echo Merge conflict. Resolve, then: git add -A ^&^& git commit
  exit /b 1
)

call gradlew.bat runVlmProcessingSchedulerTests -q
if errorlevel 1 exit /b 1

git push origin fix/vlm-media-source-contract
if errorlevel 1 exit /b 1

echo.
echo Done. Open PR: base develop ^<- fix/vlm-media-source-contract
endlocal