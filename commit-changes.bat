@echo off
echo Adding all changes...
git add .

echo Committing changes...
git commit -m "Auto-commit: Added new features/changes"

echo Pushing to GitHub...
git push origin main

echo Done! Changes have been committed and pushed to GitHub.
pause 