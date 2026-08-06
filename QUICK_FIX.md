# ⚡ Quick Fix for "Session Expired"

## The Problem
Your existing Antigravity accounts have **revoked refresh tokens** from Google. They cannot be fixed - you must delete and re-login.

## The Solution (3 Steps)

### 1️⃣ Rebuild the App
```bash
# In Android Studio: Build > Rebuild Project, then Run
# Or with terminal (when gradle is fixed):
./gradlew installDebug
```

### 2️⃣ Delete Old Accounts
In the app:
- Swipe LEFT on each Antigravity account
- Tap DELETE
- Remove ALL Antigravity accounts showing "Session expired"

### 3️⃣ Log In Fresh
- Tap the + button
- Select "Antigravity"  
- Complete Google OAuth
- ✅ Account should show usage percentages (NOT "Session expired")

## If It Still Fails

### Try Different Redirect URI
Edit `app/src/main/kotlin/com/claudetracker/app/Config.kt`:

```kotlin
// Try these in order:
const val AGY_REDIRECT_URI = "urn:ietf:wg:oauth:2.0:oob"  // ← Current
const val AGY_REDIRECT_URI = "http://127.0.0.1:9004"      // ← Try this next
const val AGY_REDIRECT_URI = "http://localhost"            // ← Then this
```

After each change, rebuild and try logging in again.

## Check If It Worked

✅ **Success signs:**
- Account shows "47%" or similar usage percentages
- Pull-to-refresh updates the data
- No "Session expired" message

❌ **Still failing if:**
- "Session expired" appears immediately after login
- Account shows red error message
- Pull-to-refresh doesn't work

## Debug Logs

If available, run:
```bash
adb logcat | grep -E "(UsageApiClient|UsageRepository|LoginViewModel)"
```

Look for:
- ✅ `"AGY code exchange SUCCESS"`  
- ✅ `"AGY token refresh SUCCESS"`
- ❌ `"Token refresh failed: HTTP 400"`
- ❌ `"invalid_grant"` or `"Token has been expired or revoked"`

## Why This Happened

Google revoked your old refresh tokens because:
- Multiple login attempts with different redirect URIs
- Incomplete OAuth flows in previous attempts  
- Security policy when issuing new tokens

## Why This Fix Works

1. **Fresh tokens** from new login = valid refresh tokens
2. **OOB redirect** = most reliable for mobile apps
3. **Enhanced logging** = can see exactly what's failing
4. **No more duplicates** = accounts update instead of duplicate

---

**That's it!** Delete old accounts → Rebuild → Log in fresh → Should work ✨

_For detailed technical explanation, see `SUMMARY_OF_CHANGES.md`_
_For step-by-step debugging, see `TROUBLESHOOTING_GUIDE.md`_
