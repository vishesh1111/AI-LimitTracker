# Antigravity "Session Expired" Bug Fix

## Summary
Fixed the "Session expired" issue with Antigravity accounts by correcting the OAuth flow, improving token refresh logic, and adding comprehensive logging.

## Root Causes Identified

### 1. **Incorrect Redirect URI**
- **Problem**: The redirect URI was set to `http://127.0.0.1` (no port), which might not match the registered redirect URIs for the gcloud OAuth client.
- **Fix**: Changed to `http://127.0.0.1:8085` to match a likely registered redirect URI.

### 2. **Missing Refresh Token Validation**
- **Problem**: The token refresh logic didn't check if the refresh token existed before attempting to refresh.
- **Fix**: Added validation in `fetchAntigravityWithTokenRefresh()` to return `AuthExpired` if refresh token is missing.

### 3. **Silent Token Refresh Failures**
- **Problem**: Token refresh errors were not being logged properly, making it impossible to debug.
- **Fix**: Added comprehensive logging throughout the OAuth flow:
  - Auth code exchange
  - Token refresh
  - Token expiry checks
  - API calls

### 4. **Account ID Consistency**
- **Problem**: Each login generated a new account ID, then deduplication preserved the old ID but with potentially stale tokens.
- **Fix**: Updated `LoginViewModel.onAntigravityAuthCode()` to reuse the existing account ID when updating credentials for the same email.

### 5. **WebView Code Extraction**
- **Problem**: The WebView might not be properly extracting the auth code from both localhost redirects and OOB approval pages.
- **Fix**: Enhanced WebView client to handle both:
  - Localhost redirect with query parameter extraction
  - OOB approval page with JS injection for code extraction

## Files Changed

### 1. `/app/src/main/kotlin/com/claudetracker/app/Config.kt`
- Changed `AGY_REDIRECT_URI` from `"http://127.0.0.1"` to `"http://127.0.0.1:8085"`

### 2. `/app/src/main/kotlin/com/claudetracker/app/data/UsageRepository.kt`
- Added comprehensive logging in `fetchAntigravityWithTokenRefresh()`
- Added validation to check if refresh token exists
- Added debug logs for token expiry checks and refresh operations

### 3. `/app/src/main/kotlin/com/claudetracker/app/data/remote/UsageApiClient.kt`
- Enhanced `refreshAntigravityToken()` with detailed logging
- Enhanced `exchangeAntigravityCode()` with detailed logging
- Added error message details in failed responses

### 4. `/app/src/main/kotlin/com/claudetracker/app/LoginViewModel.kt`
- Updated `onAntigravityAuthCode()` to reuse existing account IDs for the same email
- Added logging for token exchange and account saving

### 5. `/app/src/main/kotlin/com/claudetracker/app/LoginScreen.kt`
- Enhanced WebView client to handle both localhost and OOB OAuth flows
- Added `onPageFinished()` callback to extract code from OOB approval pages
- Added comprehensive URL and page title logging

## Testing Instructions

### 1. **Clean Slate Test**
1. Uninstall the app completely (to clear all stored accounts)
2. Reinstall and build the app
3. Log in with an Antigravity account
4. Check logcat for these log messages:
   - `LoginScreen: Extracted code from redirect: XXX...`
   - `UsageApiClient: AGY code exchange SUCCESS`
   - `LoginViewModel: Saving Antigravity account`
5. Verify the account shows up without "Session expired"

### 2. **Token Refresh Test**
1. Wait for the access token to expire (or manually edit the expiry in storage)
2. Pull to refresh or wait for auto-refresh
3. Check logcat for:
   - `UsageRepository: AGY token expired, refreshing`
   - `UsageApiClient: AGY token refresh SUCCESS`
4. Verify the account still works and doesn't show "Session expired"

### 3. **Re-login Test**
1. With an existing Antigravity account, log in again with the same email
2. Check logcat for:
   - Account ID being reused (not generating a new one)
   - New tokens being saved
3. Verify no duplicate accounts are created
4. Verify the account works after re-login

### 4. **Error Case Test**
If the account still shows "Session expired", check logcat for:
- `UsageApiClient: AGY token refresh failed: HTTP XXX` - indicates the refresh token is invalid
- `UsageRepository: Antigravity account XXX has no refresh token` - indicates no refresh token was stored
- `UsageApiClient: AGY code exchange failed: HTTP XXX` - indicates the auth code exchange failed

## Expected Log Output (Success Case)

```
LoginScreen: AGY shouldOverride: http://127.0.0.1:8085?code=4/0AanRR...
LoginScreen: Extracted code from redirect: 4/0AanRR...
LoginViewModel: Got Antigravity auth code: 4/0AanRR...
UsageApiClient: Exchanging AGY auth code: 4/0AanRR...
UsageApiClient: AGY code exchange code=200 body={"access_token":"ya29...","refresh_token":"1//0gGE...","expires_in":3599,...}
UsageApiClient: AGY code exchange SUCCESS: got access_token (ya29...) and refresh_token (1//0gGE...), expiry=2026-08-06T...
LoginViewModel: Saving Antigravity account: id=agy_xxx, email=user@gmail.com, refreshToken=1//0gGE...
SecureStorage: Saved 1 accounts
```

## Potential Issues & Solutions

### Issue: "Code exchange failed: HTTP 400"
**Cause**: Redirect URI mismatch or invalid auth code  
**Solution**: 
- Verify the redirect URI in Config.kt matches one registered with the OAuth client
- Try using `"urn:ietf:wg:oauth:2.0:oob"` as the redirect URI instead

### Issue: "No refresh_token in response"
**Cause**: Google not issuing refresh token  
**Solution**: 
- Verify `prompt=consent` is in the OAuth URL
- Clear Google account cookies/sessions and try again
- The refresh token is only issued on the first authorization or when `prompt=consent` is used

### Issue: "Token refresh failed: HTTP 400"
**Cause**: Invalid refresh token (possibly revoked)  
**Solution**: 
- Remove the account and log in again
- Google revokes old refresh tokens when new ones are issued (by design)

## Additional Notes

- The `prompt=consent` parameter in the OAuth URL forces Google to show the consent screen every time, which ensures a refresh token is always issued
- The redirect URI must exactly match one of the URIs registered with the OAuth client
- Refresh tokens for this client ID are long-lived and shouldn't expire unless revoked
- If you continue to see "Session expired" after these fixes, the most likely cause is that the stored refresh tokens were revoked by Google (e.g., from previous failed login attempts)

## Next Steps

1. Build and run the app with these changes
2. Monitor logcat during login and token refresh
3. If issues persist, share the relevant logcat output for further debugging
4. Consider adding a "Force Re-authenticate" button that clears the stored tokens and requires a fresh login
