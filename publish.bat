@echo off
rem ============================================================
rem  MeowMeowAssistant -> GitHub (cat-apk) one-click publish
rem  NOTE: this script is pure ASCII on purpose (cmd codepage
rem  safety). Chinese content lives in UTF-8 data files:
rem    - description.json (repo description, read by gh api)
rem    - RELEASE_NOTES.md  (release notes, read by gh release)
rem  Prerequisite: gh CLI logged in (gh auth status)
rem  Usage: double-click, or run publish.bat in cmd
rem ============================================================
cd /d "%~dp0"
set "REPO=Leon-kennedy-god/cat-apk"

echo [1/6] Check gh login status...
gh auth status >nul 2>&1
if errorlevel 1 (
    echo ERROR: gh is not logged in. Please run: gh auth login
    pause
    exit /b 1
)

echo [2/6] Configure git credentials and remote origin...
gh auth setup-git >nul 2>&1
git remote remove origin >nul 2>&1
git remote add origin https://github.com/%REPO%.git

echo [3/6] Commit latest changes (README/NOTICE/RELEASE_NOTES)...
git add -A
git -c user.name="Leon-Kenne-bit" -c user.email="13598220865@163.com" commit -m "Publish prep: derivative declaration (README/NOTICE), release notes" >nul 2>&1
if errorlevel 1 echo No new changes to commit.

echo [4/6] Push code to GitHub...
git branch -M main
git push -u origin main --force

echo [5/6] Update repo description and topics...
gh api --method PATCH repos/%REPO% --input description.json >nul 2>&1
if errorlevel 1 echo WARNING: description update failed (check description.json).
gh repo edit %REPO% --add-topic android --add-topic accessibility-service --add-topic text-transformer --add-topic chat-assistant --add-topic qq --add-topic wechat --add-topic telegram --add-topic agpl-3-0 >nul 2>&1
if errorlevel 1 echo WARNING: topics update failed (ignored).

echo [6/6] Create Release v1.1 with APK...
rem Upload the freshly built APK (build.bat must be run first)
if not exist "app\build\outputs\apk\debug\app-debug.apk" (
    echo WARNING: no built APK found. Run build.bat first, then rerun this script.
) else (
    copy /y "app\build\outputs\apk\debug\app-debug.apk" "dist\app-debug-v1.1.apk" >nul
    gh release create v1.1 "dist/app-debug-v1.1.apk" --notes-file RELEASE_NOTES.md --repo %REPO% >nul 2>&1
    if errorlevel 1 echo WARNING: release creation failed (tag may already exist; create manually).
)

echo.
echo Done! Repo: https://github.com/%REPO%
pause
