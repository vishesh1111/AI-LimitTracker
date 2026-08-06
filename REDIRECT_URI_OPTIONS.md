# Redirect URI Options to Try

Google OAuth is rejecting the request because the redirect URI doesn't match what's registered for this client.

## Try These in Order

### 1. `http://localhost` (CURRENT)
```kotlin
const val AGY_REDIRECT_URI = "http://localhost"
```

### 2. `http://localhost:8085`
```kotlin
const val AGY_REDIRECT_URI = "http://localhost:8085"
```

### 3. `http://127.0.0.1:8085`
```kotlin
const val AGY_REDIRECT_URI = "http://127.0.0.1:8085"
```

### 4. `http://localhost:8085/`
```kotlin
const val AGY_REDIRECT_URI = "http://localhost:8085/"
```

### 5. `http://127.0.0.1`
```kotlin
const val AGY_REDIRECT_URI = "http://127.0.0.1"
```

## How to Test Each One

1. Edit `app/src/main/kotlin/com/claudetracker/app/Config.kt`
2. Change the `AGY_REDIRECT_URI` value
3. Rebuild and run the app
4. Try logging in with Antigravity
5. If you still see "Access blocked", try the next one

## Finding the Correct Redirect URI

The error "This app's request is invalid" specifically means:
- The `redirect_uri` parameter in the OAuth URL doesn't match ANY of the registered redirect URIs for this client ID
- Google is very strict about this - even trailing slashes matter

### Common Registered Redirect URIs for gcloud CLI:
- `http://localhost:8085`
- `http://localhost:8085/`
- `http://localhost`
- `http://127.0.0.1:8085`
- `http://127.0.0.1`

### Less Common but Possible:
- `http://localhost:9004` (another common port)
- `http://localhost:8080`
- `http://localhost:8090`
- `urn:ietf:wg:oauth:2.0:oob` (OOB, but seems not registered for this client)

## Alternative: Use a Different OAuth Client

If none of the above work, it might mean the public gcloud client ID/secret have restricted redirect URIs.

You could try using a different well-known OAuth client:

### Google Cloud SDK (another variant)
```kotlin
const val AGY_CLIENT_ID = "32555940559.apps.googleusercontent.com"
const val AGY_CLIENT_SECRET = "ZmssLNjJy2998hD4CTg2ejr2"
const val AGY_REDIRECT_URI = "urn:ietf:wg:oauth:2.0:oob"
```

### Or: Create Your Own OAuth Client
1. Go to [Google Cloud Console](https://console.cloud.google.com/)
2. Create a new project or use existing
3. Enable required APIs
4. Create OAuth 2.0 credentials (Desktop app type)
5. Add redirect URI: `http://localhost:8085`
6. Use your own client ID and secret

## Current Setting

The app is currently set to:
```kotlin
const val AGY_CLIENT_ID = "764086051850-6qr4p6gpi6hn506pt8ejuq83di341hur.apps.googleusercontent.com"
const val AGY_CLIENT_SECRET = "d-FL95Q19q7MQmFpd7hHD0Ty"
const val AGY_REDIRECT_URI = "http://localhost"  // ← Try changing this
```

## Debug: Check What Google Receives

When you see the "Access blocked" error page, the URL in the WebView will show what parameters Google received. Check if the `redirect_uri` parameter is correctly encoded.

Should look like:
```
https://accounts.google.com/o/oauth2/v2/auth?
  client_id=764086051850-6qr4p6gpi6hn506pt8ejuq83di341hur.apps.googleusercontent.com
  &redirect_uri=http%3A%2F%2Flocalhost
  &response_type=code
  &scope=...
  &access_type=offline
  &prompt=consent
```

If the URL looks correct but still fails, then that redirect URI is not registered.
