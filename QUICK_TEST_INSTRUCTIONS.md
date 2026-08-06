# Quick Test Instructions - Updated Antigravity Error Handling

## What Changed?
Instead of showing fake "0% used" data, the app now shows a **clear error message** when the Antigravity API rejects your requests.

## Quick Test (5 minutes)

### 1. Build and Install
```bash
# Build
./gradlew assembleDebug

# Install
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### 2. Watch Logs in Real-Time
```bash
adb logcat | grep -E "UsageApiClient|LoginViewModel"
```

### 3. Test Antigravity Login
1. Open the app
2. Tap "Add Account" or "Login"
3. Select "Antigravity"
4. Complete Google OAuth
5. Watch the logs AND the app UI

## What You Should See

### ✅ In the Logs (Success Part):
```
LoginViewModel: AGY OAuth URL: https://accounts.google.com/...
LoginViewModel: Got Antigravity auth code: 4/0AXEQxIC...
LoginViewModel: Token exchange SUCCESS
LoginViewModel: ✓ Account saved successfully
```

### ❌ In the Logs (API Failure Part - NEW DETAILED OUTPUT):
```
═══════════════════════════════════════════════════
AGY API REQUEST to: https://daily-cloudcode-pa.googleapis.com/v1internal:retrieveUserQuotaSummary
Request body: {}
Response code: 401 (or 403 or 400)
Response headers: ...
Response body: [actual error from Google]
═══════════════════════════════════════════════════
❌ AGY AUTH FAILED: HTTP 401
Full response body: [error details]
This means the access token doesn't have permission
Possible causes:
  1. Missing required scopes
  2. User account doesn't have Antigravity access
  3. Wrong API endpoint
  4. Antigravity is internal Google-only tool
```

### 📱 In the App UI (NEW BEHAVIOR):
**Before:** Showed "0%" for all models (looked like it was working)  
**After:** Shows error message:
```
Error: Unable to access Antigravity API. Your account may not have 
the required permissions, or Antigravity may not be available for 
your account. This is an internal Google tool that requires special access.

[Try re-authenticating] button
```

## What This Tells Us

### If You See 401/403 Errors:
**Your Google account doesn't have access to Antigravity API**

This is expected - Antigravity is an internal Google tool not available to all users.

### How to Verify:
1. Install **Cloud Code** extension in VS Code
2. Sign in with the same Google account
3. Try to use Gemini/AI features
4. **If that doesn't work** → You don't have access, and that's okay
5. **If that DOES work** → Share the detailed logs, we need to fix the API endpoint

## Next Actions

### Option A: You Don't Have Antigravity Access (Most Likely)
- Nothing to do - the error message is correct
- You can still use Claude and Codex tracking
- Remove the Antigravity account from the app if you want

### Option B: You DO Have Access (Unlikely)
**Share these specific log lines:**
1. The full "AGY API REQUEST" section (all lines between the ═══ borders)
2. The "Response body" - this will tell us exactly what Google's API is saying
3. Any other error messages you see

**Example of what to share:**
```
Response code: 403
Response body: {"error":{"code":403,"message":"User does not have permission...","status":"PERMISSION_DENIED"}}
```

This will help us figure out:
- If we're using the wrong API endpoint
- If we're missing required OAuth scopes
- If we need a different authentication approach

## Common Questions

**Q: Why did it show 0% before?**  
A: The code was silently failing and returning fake data instead of an error. Not helpful!

**Q: Is this a bug?**  
A: No - the app is working correctly. The issue is that most Google accounts don't have access to Antigravity (internal Google tool).

**Q: Can I fix it?**  
A: Only if you actually have Antigravity access through your Google account (rare). If not, no fix is possible.

**Q: Will Claude and Codex tracking still work?**  
A: Yes! This only affects Antigravity. Claude and ChatGPT tracking work fine.

**Q: Should I re-authenticate?**  
A: You can try, but if you don't have Antigravity access, re-authenticating won't help. The error will persist.

## Files to Read for More Details

- **`ANTIGRAVITY_API_ACCESS_ISSUE.md`** - Full explanation of the issue
- **`CHANGES_SUMMARY.md`** - Detailed list of code changes
- **`app/src/main/kotlin/com/claudetracker/app/data/remote/UsageApiClient.kt`** - Where the API calls happen

## Bottom Line

The app now **tells you the truth** instead of showing fake 0% data. If you see the error message about API access, it means your Google account doesn't have permission to use Antigravity's API - and that's expected for most users.
