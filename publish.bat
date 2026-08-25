@echo off
rem ============================================================
rem  MeowMeowAssistant -> GitHub (cat-apk) one-click publish
rem  v1.1 edition. Pure ASCII on purpose (cmd codepage safety);
rem  Chinese content lives in UTF-8 files read by gh:
rem    - description.json (repo description)
rem    - RELEASE_NOTES.md  (release notes)
rem  Prerequisite: gh CLI logged in (gh auth status)
rem  Usage: double-click, or run publish.bat in cmd
rem ============================================================
cd /d "%~dp0"
set "REPO=Leon-kennedy-god/cat-apk"

echo [1/6] Check gh login...
gh auth status
if errorlevel 1 (
    echo ERROR: gh is not logged in. Please run: gh auth login
    pause
    exit /b 1
)

echo [2/6] Setup git credentials and remote...
gh auth setup-git
git remote remove origin >nul 2>&1
git remote add origin https://github.com/%REPO%.git

echo [3/6] Commit latest changes...
git add -A
git -c user.name="Leon-Kenne-bit" -c user.email="13598220865@163.com" commit -m "Publish prep: derivative declaration, release notes, download links" >nul 2>&1
if errorlevel 1 echo No new changes to commit.

echo [4/6] Push code to GitHub...
git branch -M main
git push -u origin main --force

echo [5/6] Update repo description and topics...
gh api --method PATCH repos/%REPO% --input description.json
if errorlevel 1 echo WARNING: description update failed.
gh repo edit %REPO% --add-topic android --add-topic accessibility-service --add-topic text-transformer --add-topic chat-assistant --add-topic qq --add-topic wechat --add-topic telegram --add-topic agpl-3-0 >nul 2>&1
if errorlevel 1 echo WARNING: topics update failed.

echo [6/6] Create Release v1.1.1 with APK...
if not exist "app\build\outputs\apk\debug\app-debug.apk" (
    echo ERROR: APK not found. Run build.bat FIRST, then rerun this script.
    pause
    exit /b 1
)
rem Stable asset name: direct URL never breaks across versions
copy /y "app\build\outputs\apk\debug\app-debug.apk" "dist\MeowMeowAssistant.apk" >nul
rem Remove any stale v1.1.1 release/tag so creation always succeeds
gh release delete v1.1.1 --yes >nul 2>&1
git push origin :refs/tags/v1.1.1 >nul 2>&1
gh release create v1.1.1 "dist/MeowMeowAssistant.apk" --notes-file RELEASE_NOTES.md --repo %REPO%
if errorlevel 1 (
    echo ERROR: release creation failed. See message above.
    pause
    exit /b 1
)

echo.
echo ============================================================
echo  DONE! Direct download URL (always latest):
echo  https://github.com/%REPO%/releases/latest/download/MeowMeowAssistant.apk
echo ============================================================
for /f "delims=" %%v in ('gh repo view %REPO% --json visibility -q .visibility 2^>nul') do set "VIS=%%v"
if /i "%VIS%"=="PRIVATE" (
    echo WARNING: repo is PRIVATE - download links return 404 for others!
    echo Make it public:  gh repo edit %REPO% --visibility public
)
echo Open: https://github.com/%REPO%/releases
pause
