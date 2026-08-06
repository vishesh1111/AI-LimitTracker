# Removing Antigravity Feature - Complete Guide

## Why Remove?
The Antigravity API is an internal Google tool that requires special account permissions not available to regular users. Since the feature cannot work for most users, we're removing it entirely to simplify the app.

## Files That Need Changes

### 1. ✅ **Platform enum** - `/app/src/main/kotlin/com/claudetracker/app/data/model/Account.kt`
- Remove `ANTIGRAVITY` from Platform enum
- Remove Antigravity-specific fields from Account data class:
  - `agyRefreshToken`
  - `agyAccessToken`
  - `agyAccessTokenExpiry`
  - `agyProjectId`

### 2. ✅ **UsageData model** - `/app/src/main/kotlin/com/claudetracker/app/data/model/UsageData.kt`
- Remove Antigravity-specific fields:
  - `geminiSession`
  - `geminiWeekly`
  - `claudeGptSession`
  - `claudeGptWeekly`
  - `hasModelGroups`
- Remove `fromAntigravityJson()` function

### 3. **UsageApiClient** - `/app/src/main/kotlin/com/claudetracker/app/data/remote/UsageApiClient.kt`
- Remove `AntigravityUsageFetch` data class
- Remove all Antigravity API functions:
  - `refreshAntigravityToken()`
  - `exchangeAntigravityCode()`
  - `fetchAntigravityUsage()`

### 4. **UsageRepository** - `/app/src/main/kotlin/com/claudetracker/app/data/UsageRepository.kt`
- Remove `Platform.ANTIGRAVITY` case from `fetchSingleAccountUsage()`
- Remove `fetchAntigravityWithTokenRefresh()` function

### 5. **SecureStorage** - `/app/src/main/kotlin/com/claudetracker/app/data/local/SecureStorage.kt`
- Remove Antigravity deduplication logic from `addAccount()`
- Remove `updateAntigravityToken()` function
- Remove `updateAntigravityProject()` function

### 6. **LoginViewModel** - `/app/src/main/kotlin/com/claudetracker/app/LoginViewModel.kt`
- Remove `Platform.ANTIGRAVITY` case from `onPageFinished()`
- Remove all Antigravity login functions:
  - `getAntigravityOAuthUrl()`
  - `onAntigravityAuthCode()`
  - `onAntigravityImplicitToken()`
  - `submitAntigravityRefreshToken()`
- Remove `fetchGoogleEmail()` helper

### 7. **LoginScreen** - `/app/src/main/kotlin/com/claudetracker/app/LoginScreen.kt`
- Remove `Platform.ANTIGRAVITY` WebView case
- Remove `AntigravityTokenInput()` composable

### 8. **PlatformPickerScreen** - `/app/src/main/kotlin/com/claudetracker/app/PlatformPickerScreen.kt`
- Remove Antigravity PlatformCard

### 9. **StatusScreen** - `/app/src/main/kotlin/com/claudetracker/app/StatusScreen.kt`
- Remove `Platform.ANTIGRAVITY` from platformOrder list
- Remove Antigravity icon case
- Remove Antigravity-specific error handling
- Remove `hasModelGroups` / dual model group display logic

### 10. **Config** - `/app/src/main/kotlin/com/claudetracker/app/Config.kt`
- Remove all AGY_ constants:
  - `AGY_TOKEN_ENDPOINT`
  - `AGY_USAGE_ENDPOINT`
  - `AGY_CLIENT_ID`
  - `AGY_CLIENT_SECRET`
  - `AGY_REDIRECT_URI`
  - `AGY_OAUTH_SCOPES`

### 11. **AppNavigation** - `/app/src/main/kotlin/com/claudetracker/app/AppNavigation.kt`
- Remove any Antigravity-specific navigation logic

### 12. **NotificationHelper** - `/app/src/main/kotlin/com/claudetracker/app/notification/NotificationHelper.kt`
- Remove any Antigravity-specific notification logic

### 13. **UsageRefreshWorker** - `/app/src/main/kotlin/com/claudetracker/app/worker/UsageRefreshWorker.kt`
- Remove any Antigravity-specific refresh logic

### 14. **Resources** - `/app/src/main/res/drawable-nodpi/`
- Can keep or remove `ic_antigravity.png` (doesn't hurt to leave it)

## Manual Testing After Removal

1. **Build the app:**
   ```bash
   ./gradlew clean
   ./gradlew assembleDebug
   ```

2. **Install:**
   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

3. **Test scenarios:**
   - Platform picker should show only Claude and Codex
   - Login flow should work for Claude
   - Login flow should work for Codex
   - Usage display should work for both platforms
   - No crashes or compilation errors

## Benefits of Removal

- ✅ **Simpler codebase** - Remove ~1000+ lines of unused code
- ✅ **Better UX** - Users won't see a feature that doesn't work for them
- ✅ **Easier maintenance** - Less code to maintain
- ✅ **Clearer purpose** - App focuses on Claude and ChatGPT tracking
- ✅ **No confusing errors** - Users won't encounter "no permission" errors

## If You Want Antigravity Back Later

All the code is preserved in git history. You can:
1. Check out this commit before removal
2. Cherry-pick the Antigravity-specific commits
3. Or refer to the files in this commit to re-implement

The git commit before removal will serve as a reference implementation.

## Alternative: Keep as "Coming Soon"

Instead of complete removal, you could:
1. Keep the Platform enum entry
2. Remove all implementation code
3. Show "Coming Soon" or "Requires Google Internal Access" message
4. This reserves the option to add it back if you get access

But for now, complete removal is cleaner and simpler.
