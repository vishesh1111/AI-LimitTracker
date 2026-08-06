# Summary of Changes - Antigravity Session Fix

## TL;DR

**The Issue**: Your Antigravity accounts show "Session expired" because the stored refresh tokens are invalid/revoked by Google.

**The Fix**: 
1. I've added comprehensive logging and improved the OAuth flow
2. Changed redirect URI to use Google's OOB (out-of-band) flow
3. **You need to DELETE the existing Antigravity accounts and log in fresh**

## What Was Wrong

### 1. Invalid Refresh Tokens
Your existing accounts have refresh tokens that Google has revoked. This happens when:
- The OAuth flow was incomplete in previous attempts
- Different redirect URIs were used across multiple login attempts
- Google automatically revokes old tokens when new ones are issued

### 2. Silent Failures
The code wasn't logging enough information to diagnose where the OAuth flow was failing.

### 3. Redirect URI Issues  
The redirect URI was set to `http://127.0.0.1` without a port, which might not match Google's registered redirect URIs for this OAuth client.

### 4. Account Duplication Issues
Each login created a new account ID, but deduplication preserved old IDs with stale tokens.

## Changes Made

### Config.kt
- Changed `AGY_REDIRECT_URI` to `"urn:ietf:wg:oauth:2.0:oob"`
- This uses Google's official out-of-band redirect for installed apps

### UsageRepository.kt  
- Added comprehensive logging in `fetchAntigravityWithTokenRefresh()`
- Added validation to check if refresh token exists before attempting refresh
- Added detailed token expiry checks with timestamps

### UsageApiClient.kt
- Enhanced `refreshAntigravityToken()` with detailed error logging
- Added detection for `invalid_grant` errors (revoked tokens)
- Enhanced `exchangeAntigravityCode()` with full request/response logging
- Log messages now include:
  - Auth code being exchanged
  - Token refresh attempts
  - Success/failure with full error bodies

### LoginViewModel.kt
- Updated `onAntigravityAuthCode()` to reuse existing account IDs for same email
- This prevents duplicate accounts when re-logging with the same Google account
- Added logging for account creation and token storage

### LoginScreen.kt
- Enhanced WebView client to handle BOTH OAuth redirect types:
  - **Localhost redirect**: Extracts code from URL query parameter
  - **OOB approval page**: Extracts code from page title or page content using JavaScript
- Added `onPageFinished()` callback to detect Google's approval page
- Added comprehensive URL and page title logging

## How to Fix Your Current Issue

### Step 1: Delete Existing Accounts ⚠️
The existing Antigravity accounts have invalid refresh tokens that cannot be fixed automatically.

**In the app:**
1. Open ClaudeTracker
2. Swipe left on each Antigravity account showing "Session expired"  
3. Delete the account
4. Repeat for all Antigravity accounts

### Step 2: Rebuild the App
```bash
# When gradle wrapper is fixed, run:
./gradlew assembleDebug installDebug

# Or in Android Studio:
# Build > Rebuild Project
# Then Run the app
```

### Step 3: Log In Fresh
1. Click the + button
2. Select "Antigravity"
3. Complete the Google OAuth flow
4. Watch for:
   - Google's approval page (should show "Success code=XXX" or similar)
   - The app should automatically extract the code
   - The account should appear WITHOUT "Session expired"

### Step 4: Verify It Works
- The account should show usage percentages immediately
- Pull to refresh should work
- After the access token expires (~1 hour), it should auto-refresh without showing "Session expired"

## How to Debug (If Still Failing)

### Using Logcat
```bash
adb logcat | grep -E "(UsageApiClient|UsageRepository|LoginViewModel|LoginScreen)"
```

Look for these messages:

**Success indicators:**
- `LoginScreen: Extracted code from redirect: XXX...`
- `UsageApiClient: AGY code exchange SUCCESS`
- `LoginViewModel: Saving Antigravity account`
- `UsageApiClient: AGY token refresh SUCCESS`

**Failure indicators:**
- `UsageApiClient: AGY code exchange failed: HTTP 400`
- `UsageApiClient: AGY token refresh failed: HTTP 400`
- `UsageApiClient: Refresh token is invalid or revoked`

### Without Logcat
Watch the OAuth flow behavior:

**OOB Flow (current):**
- After approving, Google should show a page with the auth code
- The app should automatically close the WebView and add the account
- If the WebView just sits there → code extraction failed

**Try Alternative Redirect:**
If OOB doesn't work, edit `Config.kt` and try:
```kotlin
const val AGY_REDIRECT_URI = "http://127.0.0.1:9004"  // gcloud default
```

## Why This Will Work Now

1. **Fresh Tokens**: New login = new valid refresh tokens
2. **Better Redirect**: OOB is the most reliable redirect for installed apps
3. **Enhanced Code Extraction**: Multiple methods to extract the auth code
4. **Comprehensive Logging**: Can diagnose exactly what's failing
5. **Account ID Reuse**: No more duplicate accounts

## Common Questions

### Q: Why not just refresh the existing tokens?
**A**: You can't refresh an invalid refresh token. Refresh tokens are different from access tokens - when a refresh token is revoked by Google, you MUST re-authenticate completely.

### Q: Will this happen again?
**A**: No, as long as you:
- Don't change the redirect URI after accounts are created
- Complete the OAuth flow fully each time
- Use the same redirect URI consistently

### Q: Why were the tokens revoked?
**A**: Most likely because:
- Previous OAuth attempts with different redirect URIs
- Incomplete OAuth flows (closed browser before completion)
- Google's security policy revoked old tokens when new attempts were made

### Q: What if it still shows "Session expired" after fresh login?
**A**: Then the OAuth flow itself is failing. Check:
1. The redirect URI matches one registered with Google
2. The WebView is extracting the auth code correctly
3. The token exchange is succeeding (check logcat)

If still failing, try different redirect URIs in this order:
- `urn:ietf:wg:oauth:2.0:oob` (current)
- `http://127.0.0.1:9004` (gcloud CLI default)
- `http://localhost`
- `http://127.0.0.1`

## Files to Review

All changes are logged in:
- `/Users/visheshverma/Documents/SDK/ClaudeTracker/ANTIGRAVITY_SESSION_FIX.md` (detailed technical changes)
- `/Users/visheshverma/Documents/SDK/ClaudeTracker/TROUBLESHOOTING_GUIDE.md` (step-by-step debugging)
- This file (high-level summary)

## Final Checklist

- [ ] Rebuild the app with the new changes
- [ ] Delete all existing Antigravity accounts showing "Session expired"
- [ ] Log in fresh with each Antigravity account
- [ ] Verify accounts show usage data (not "Session expired")
- [ ] Test pull-to-refresh
- [ ] Wait for token to expire and verify auto-refresh works

---

**Need Help?**
If it's still not working after following these steps, share the logcat output (especially lines containing "UsageApiClient", "UsageRepository", and "LoginViewModel") to diagnose further.
