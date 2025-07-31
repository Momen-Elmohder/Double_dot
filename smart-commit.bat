@echo off
echo Checking for changes...

REM Check if there are any changes
git diff --quiet
if %errorlevel% equ 0 (
    echo No changes detected.
    pause
    exit /b
)

echo Changes detected! Adding files...
git add .

REM Get the current date and time
for /f "tokens=2 delims==" %%a in ('wmic OS Get localdatetime /value') do set "dt=%%a"
set "YY=%dt:~2,2%" & set "YYYY=%dt:~0,4%" & set "MM=%dt:~4,2%" & set "DD=%dt:~6,2%"
set "HH=%dt:~8,2%" & set "Min=%dt:~10,2%" & set "Sec=%dt:~12,2%"
set "datestamp=%YYYY%-%MM%-%DD% %HH%:%Min%"

echo.
echo What commit message do you want to write?
echo (Example: "Added new coach role", "Fixed login bug", "Updated UI design")
echo.
set /p commit_message="Enter your commit message: "

echo.
echo Committing changes with your message...
git commit -m "%commit_message% - %datestamp%"

echo Pushing to GitHub...
git push origin main

echo Successfully committed and pushed changes!
echo Commit message: "%commit_message%"
echo Timestamp: %datestamp%
pause 