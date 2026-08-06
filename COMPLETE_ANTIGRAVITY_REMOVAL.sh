#!/bin/bash

# Script to completely remove Antigravity feature from ClaudeTracker
# Run this from the project root directory

echo "🗑️  Removing Antigravity Feature from ClaudeTracker..."
echo ""

# Backup first
echo "📦 Creating backup..."
BACKUP_DIR="antigravity_backup_$(date +%Y%m%d_%H%M%S)"
mkdir -p "$BACKUP_DIR"

# List of files to backup
FILES=(
    "app/src/main/kotlin/com/claudetracker/app/data/model/Account.kt"
    "app/src/main/kotlin/com/claudetracker/app/data/model/UsageData.kt"
    "app/src/main/kotlin/com/claudetracker/app/data/remote/UsageApiClient.kt"
    "app/src/main/kotlin/com/claudetracker/app/data/UsageRepository.kt"
    "app/src/main/kotlin/com/claudetracker/app/data/local/SecureStorage.kt"
    "app/src/main/kotlin/com/claudetracker/app/LoginViewModel.kt"
    "app/src/main/kotlin/com/claudetracker/app/LoginScreen.kt"
    "app/src/main/kotlin/com/claudetracker/app/PlatformPickerScreen.kt"
    "app/src/main/kotlin/com/claudetracker/app/StatusScreen.kt"
    "app/src/main/kotlin/com/claudetracker/app/Config.kt"
)

for file in "${FILES[@]}"; do
    if [ -f "$file" ]; then
        cp "$file" "$BACKUP_DIR/"
        echo "  ✓ Backed up $file"
    fi
done

echo ""
echo "✅ Backup complete in $BACKUP_DIR"
echo ""
echo "⚠️  MANUAL STEPS REQUIRED:"
echo ""
echo "The following changes need to be made manually:"
echo ""
echo "1. Config.kt - Remove all AGY_ constants"
echo "2. UsageApiClient.kt - Remove Antigravity API functions (lines ~185-455)"
echo "3. UsageRepository.kt - Remove fetchAntigravityWithTokenRefresh() function"
echo "4. SecureStorage.kt - Remove Antigravity update functions"
echo "5. LoginViewModel.kt - Remove Antigravity login functions (lines ~407-570)"
echo "6. LoginScreen.kt - Remove Antigravity WebView and input UI"
echo "7. PlatformPickerScreen.kt - Remove Antigravity card"
echo "8. StatusScreen.kt - Remove Antigravity from platform order and icon handling"
echo ""
echo "📝 See REMOVING_ANTIGRAVITY_FEATURE.md for detailed instructions"
echo ""
echo "After manual changes, run:"
echo "  ./gradlew clean"
echo "  ./gradlew assembleDebug"
echo ""
