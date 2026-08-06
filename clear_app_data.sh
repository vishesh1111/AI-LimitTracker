#!/bin/bash

# Script to clear ClaudeTracker app data on connected Android device
# This will remove all stored accounts and force a fresh login

PACKAGE_NAME="com.claudetracker.app"

echo "Clearing app data for $PACKAGE_NAME..."

# Check if device is connected
if ! adb devices | grep -q "device$"; then
    echo "Error: No Android device connected"
    echo "Please connect your device and enable USB debugging"
    exit 1
fi

# Clear app data
echo "Clearing app data..."
adb shell pm clear $PACKAGE_NAME

if [ $? -eq 0 ]; then
    echo "✓ App data cleared successfully"
    echo ""
    echo "Next steps:"
    echo "1. Open the ClaudeTracker app"
    echo "2. Log in again with your Antigravity accounts"
    echo "3. Monitor logcat with: adb logcat | grep -E '(UsageApiClient|UsageRepository|LoginViewModel|LoginScreen)'"
else
    echo "✗ Failed to clear app data"
    echo "You may need to manually uninstall and reinstall the app"
fi
