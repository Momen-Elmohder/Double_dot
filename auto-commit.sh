#!/bin/bash

# Auto-commit script for Double Dot Demo project
echo "🔄 Auto-committing changes..."

# Add all changes
git add .

# Get current timestamp
timestamp=$(date "+%Y-%m-%d %H:%M:%S")

# Commit with timestamp
git commit -m "Auto-commit: $timestamp - $(git status --porcelain | wc -l) files changed"

# Push to GitHub
git push origin main

echo "✅ Changes committed and pushed successfully!"
echo "📅 Timestamp: $timestamp" 