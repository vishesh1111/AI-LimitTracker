# Troubleshooting Guide: Antigravity "Session Expired"

## Quick Fix Steps

### Step 1: Remove Old Accounts
The most likely issue is that your existing Antigravity accounts have **invalid/revoked refresh tokens**. Google revokes old refresh tokens when new ones are issued, especially if the OAuth flow was incomplete or used different redirect URIs.

**Solution**: Remove the existing Antigravity accounts and log in fresh.

1. Open the app
2. Swipe left on each Antigravity account card showing "Session expired"
3. Delete the account
4. Click the + button to add a new account
5. Select Antigravity and log in again

### Step 2: Monitor the Login Flow
When you log in, watch for these behaviors:

#### Expected OAuth Flow (with OOB redirect):
1. You see the Google account selection page
2. You approve the scopes
3. **Google shows a page with "Success code=XXX" or a text box containing the auth code**
4. The app should automatically extract the code and exchange it for tokens
5. You should see the account added without "Session expired"

#### If the Code Extraction Fails:
- The app might not be extracting the code from Google's approval page
- Check logcat (if available) for messages like:
  - `"LoginScreen: Extracted code from page content"`
  - `"LoginScreen: Extracted code from title"`

### Step 3: Alternative - Use Different Redirect URI
If the OOB flow doesn't work, try changing back to localhost:

1. Edit `Config.kt`
2. Change `AGY_REDIRECT_URI` from `"urn:ietf:wg:oauth:2.0:oob"` to one of:
   - `"http://localhost"`
   - `"http://127.0.0.1"`
   - `"http://127.0.0.1:8085"`
   - `"http://127.0.0.1:9004"` (this is what gcloud CLI uses by default)

## Understanding the Issue

### Why "Session Expired" Happens:

1. **Invalid Refresh Token**: The stored refresh token is no longer valid with Google
   - Revoked due to multiple login attempts
   - Revoked due to redirect URI mismatch during initial OAuth
   - Never properly obtained in the first place

2. **Token Refresh Failure**: When the app tries to refresh the access token:
   ```
   UsageRepository: AGY token expired, refreshing
   UsageApiClient: AGY token refresh failed: HTTP 400
   ```
   This results in `UsageResult.AuthExpired` which shows "Session expired" in the UI

3. **Cannot Auto-Fix**: Unlike access tokens (which can be refreshed), invalid refresh tokens require the user to re-authenticate completely

### What the Fixes Do:

1. **Logging**: Added comprehensive logging to see exactly what's failing
2. **OOB Redirect**: Using Google's out-of-band flow which is more reliable for installed apps
3. **Code Extraction**: Enhanced WebView to extract auth codes from Google's approval page multiple ways
4. **Account ID Reuse**: When re-logging with the same email, reuse the existing account ID to prevent duplicates

## Debugging Without ADB

If you can't access logcat, here's how to debug:

### Test 1: Fresh Login
1. Remove all Antigravity accounts
2. Add one account
3. **Immediately after login**, check if it shows "Session expired" or actual usage data
4. If it shows "Session expired" immediately → OAuth flow failed
5. If it shows usage data initially → OAuth flow succeeded, but token refresh will fail later

### Test 2: Check Redirect Behavior
When logging in, watch what happens after you approve permissions:

**OOB Flow (current setting)**:
- Google shows a page saying "Please copy this code, switch to your application and paste it there:"
- The app should automatically detect and extract this code
- If the page just sits there and nothing happens → code extraction failed

**Localhost Flow**:
- Google tries to redirect to `http://localhost` or `http://127.0.0.1`
- Browser shows "Site can't be reached" or similar
- The app should intercept this and extract the code from the URL
- If you see an error page and the app doesn't respond → redirect interception failed

## Advanced: Manual Refresh Token Entry

If all else fails, you can manually obtain a refresh token:

1. Open a terminal and run:
   ```bash
   open "https://accounts.google.com/o/oauth2/v2/auth?client_id=764086051850-6qr4p6gpi6hn506pt8ejuq83di341hur.apps.googleusercontent.com&redirect_uri=urn:ietf:wg:oauth:2.0:oob&response_type=code&scope=openid%20https://www.googleapis.com/auth/userinfo.email%20https://www.googleapis.com/auth/cloud-platform&access_type=offline&prompt=consent"
   ```

2. Complete the OAuth flow and copy the authorization code

3. Exchange it for tokens:
   ```bash
   curl -X POST https://oauth2.googleapis.com/token \
     -d "grant_type=authorization_code" \
     -d "code=YOUR_AUTH_CODE_HERE" \
     -d "client_id=764086051850-6qr4p6gpi6hn506pt8ejuq83di341hur.apps.googleusercontent.com" \
     -d "client_secret=d-FL95Q19q7MQmFpd7hHD0Ty" \
     -d "redirect_uri=urn:ietf:wg:oauth:2.0:oob"
   ```

4. Copy the `refresh_token` from the response

5. In the app, there's a manual token entry option (if implemented) or you can modify the code to accept pasted tokens

## Files Changed in Latest Fix

1. `Config.kt` - Set redirect URI to OOB
2. `UsageRepository.kt` - Added logging for token refresh
3. `UsageApiClient.kt` - Added invalid_grant detection
4. `LoginViewModel.kt` - Fixed account ID reuse
5. `LoginScreen.kt` - Enhanced code extraction from OAuth pages

## Next Steps

1. **Rebuild the app** (when gradle is available)
2. **Remove existing Antigravity accounts** (they have invalid tokens)
3. **Log in fresh** and monitor the behavior
4. **If still failing**, switch redirect URI to `http://127.0.0.1:9004` and try again

## Common Error Messages

### "Code exchange failed: HTTP 400"
- Redirect URI doesn't match what's registered with Google
- Try different redirect URIs

### "No refresh_token in response"
- Not using `prompt=consent` (but we are)
- Using implicit flow instead of authorization code flow (but we're not)
- The auth code was already used once (can only be exchanged once)

### "Token refresh failed: HTTP 400, invalid_grant"
- **This is your current issue** - refresh token is revoked
- Solution: Delete account and log in again

### "Antigravity account XXX has no refresh token"
- The account was saved without a refresh token
- This shouldn't happen with current code
- Delete and re-add the account

## Success Indicators

You'll know it's working when:
1. After login, the account shows usage percentages (not "Session expired")
2. Logcat shows: `"AGY code exchange SUCCESS: got access_token ... and refresh_token ..."`
3. After waiting for token expiry (or force refresh), you see: `"AGY token refresh SUCCESS"`
4. The account continues to work without showing "Session expired"
