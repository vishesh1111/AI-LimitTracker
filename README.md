# Claude Tracker

A personal-use Android app that tracks your [Claude.ai](https://claude.ai) usage limits
(session %, weekly %, reset times) by reading the same internal endpoint the claude.ai
web app uses, via a captured session cookie.

**Not for Play Store distribution** — sideloaded directly to devices. Each user logs into
their own Claude account independently.

---

## Features

- **Multi-Account Support**: Add and monitor multiple Claude.ai accounts simultaneously.
- **Automated Login Flow**: Smart URL detection automatically captures your session without any manual buttons after logging in.
- **Usage Tracking**: Monitor your Session (5h) and Weekly usage percentage and reset times.
- **Premium Home Screen Widget**: A beautiful Glance widget with text-based progress bars, plan name badges, and color-coded status indicators (🟢🟡🔴).
- **Background Sync**: Runs in the background and sends you a notification exactly when your usage window resets.
- **Account Management**: Swipe to delete specific accounts or use the secure "Logout All" function with confirmation.
- **Privacy First**: Secure `EncryptedSharedPreferences` for your cookies. All data stays locally on your device.

---
## Quick Start

### 1. Plug in your two config values

Open [`Config.kt`](app/src/main/kotlin/com/claudetracker/app/Config.kt) and replace:

```kotlin
object Config {
    const val USAGE_ENDPOINT_TEMPLATE = "REPLACE_ME_WITH_USAGE_ENDPOINT"
    //                                  ↑ The real endpoint path, with {org_id} placeholder
    //                                    e.g. "https://claude.ai/api/organizations/{org_id}/usage"

    const val SESSION_COOKIE_NAME = "REPLACE_ME_WITH_COOKIE_NAME"
    //                              ↑ The session cookie name from claude.ai
    //                                e.g. "sessionKey"
    ...
}
```

### 2. Plug in the JSON field mapping

Open [`UsageData.kt`](app/src/main/kotlin/com/claudetracker/app/data/model/UsageData.kt)
and update the `fromJson()` companion function to map the real JSON fields from the API
response to the data class fields. Look for the `// TODO: UPDATE FIELD MAPPING` comment.

### 3. Build a debug APK

```bash
cd ClaudeTracker
./gradlew assembleDebug
```

The APK will be at `app/build/outputs/apk/debug/app-debug.apk`.

Install directly:
```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

### 4. Build a signed release APK (for sharing with friends)

#### a) Create a keystore (one-time)

```bash
keytool -genkey -v \
  -keystore claude-tracker-release.jks \
  -alias claudetracker \
  -keyalg RSA -keysize 2048 \
  -validity 10000
```

#### b) Configure signing in `app/build.gradle.kts`

Uncomment and fill in the signing config section, or create a `keystore.properties` file:

```kotlin
android {
    signingConfigs {
        create("release") {
            storeFile = file("../claude-tracker-release.jks")
            storePassword = "your-store-password"
            keyAlias = "claudetracker"
            keyPassword = "your-key-password"
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            // ... rest of config
        }
    }
}
```

#### c) Build

```bash
./gradlew assembleRelease
```

The signed APK will be at `app/build/outputs/apk/release/app-release.apk`.
Share this file directly — each recipient installs it and logs into their own Claude
account on first launch.

---

## How it works

### Login & credential capture

1. The app opens a WebView to `https://claude.ai/login`
2. You log in normally through Claude's web interface
3. The app detects when the URL changes away from the login page
4. It reads the session cookie from the WebView's cookie jar
5. It makes one API call to `https://claude.ai/api/organizations` to discover your
   org ID (this is per-account, not a fixed config value)
6. Both the cookie and org ID are stored in `EncryptedSharedPreferences`

### Usage fetching

- **On app open**: The status screen immediately triggers a fresh fetch
- **Background**: A WorkManager periodic job runs every ~15 minutes (Android's minimum
  floor; actual timing may vary by OEM)
- **Manual**: Tap the refresh button on the status screen, or the refresh action on
  the home screen widget

### Reset detection & notifications

This is the key differentiator — the app does **not** spam you with periodic
notifications. Instead:

1. Each time the background worker fetches usage data, it compares the **session reset
   timestamp** and **weekly reset timestamp** from the API response against the
   last-known values stored locally
2. If a reset timestamp has changed (meaning that usage window rolled over since the
   last check), a notification fires for that specific window:
   - "Your session Claude usage limit has reset!" (session window)
   - "Your weekly Claude usage limit has reset!" (weekly window)
3. If the timestamps haven't changed, no notification is sent — most poll cycles are
   silent
4. The new timestamps are saved for the next comparison

**Important**: Because this depends on WorkManager's poll cycle (~15-30 minutes in
practice), there will be a delay between when the reset actually happens and when
the notification fires.

### Home screen widget

- Always shows the latest cached session %, weekly %, and last-updated time
- Updated on every successful background poll
- Three visual states:
  - **Normal**: Shows usage percentages
  - **Not logged in**: Shows "Tap to log in"
  - **Auth expired**: Shows "Session expired — tap to log in"
- Tap the refresh button to trigger an immediate one-time fetch

---

## Architecture

```
+---------------------------------------------------------+
|                      UI Layer                           |
|  LoginScreen <-> LoginViewModel                         |
|  StatusScreen <-> StatusViewModel                       |
|  UsageGlanceWidget (reads SharedPrefs directly)         |
+---------------------------------------------------------+
|                      Data Layer                         |
|  UsageRepository                                        |
|    +-- SecureStorage (EncryptedSharedPreferences)        |
|    +-- UsageApiClient (OkHttp -> claude.ai)             |
|    +-- Widget SharedPreferences (cached data)           |
+---------------------------------------------------------+
|                   Background Layer                      |
|  UsageRefreshWorker (WorkManager CoroutineWorker)       |
|    +-- Calls UsageRepository.fetchUsage()               |
|    +-- Reset detection (timestamp comparison)           |
|    +-- NotificationHelper (reset alerts only)           |
|    +-- Widget update                                    |
+---------------------------------------------------------+
```

---

## Known limitations

| Limitation | Details |
|---|---|
| **Undocumented endpoint** | The usage API is internal to claude.ai and can change or break without notice. If the endpoint path or response format changes, you'll need to update `Config.kt` and `UsageData.fromJson()`. |
| **Cookie expiration** | Session cookies expire periodically. When this happens, the app shows an "auth expired" state and routes you back to login. |
| **Background timing** | Android enforces a ~15-minute minimum for WorkManager periodic jobs. In practice, OEM battery optimizations (Samsung, Xiaomi, Huawei) can push this to 30+ minutes. The reset notification will lag accordingly. |
| **No real-time reset alerts** | Reset detection depends on polling. If you need to know the instant a limit resets, check the status screen manually — the reset times are displayed there. |
| **Per-device only** | Each device has its own login and data. No sync, no cloud backup, no data sharing between devices. |
| **No offline cache** | If the device is offline, the app shows the last successfully fetched data (marked with its timestamp) but cannot refresh. |
| **WebView login quirks** | Some Claude.ai login flows (e.g., Google OAuth redirects) may behave differently in an embedded WebView vs. a full browser. If login fails, try clearing app data and retrying. |

---

## Project structure

```
ClaudeTracker/
├── build.gradle.kts                     <- project-level (plugin aliases)
├── settings.gradle.kts                  <- module include + repos
├── gradle.properties                    <- JVM args, AndroidX flags
├── gradle/libs.versions.toml            <- version catalog
├── app/
│   ├── build.gradle.kts                 <- app config, deps, signing
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── res/                         <- icons, colors, strings, widget XML
│       └── kotlin/com/claudetracker/app/
│           ├── Config.kt                * YOUR TWO CONFIG VALUES GO HERE
│           ├── ClaudeTrackerApp.kt      <- Application + DI singletons
│           ├── MainActivity.kt          <- Single-activity Compose host
│           ├── data/
│           │   ├── model/
│           │   │   ├── UsageData.kt     * JSON FIELD MAPPING TODO HERE
│           │   │   └── UsageResult.kt
│           │   ├── local/
│           │   │   └── SecureStorage.kt
│           │   ├── remote/
│           │   │   └── UsageApiClient.kt
│           │   └── UsageRepository.kt
│           ├── ui/
│           │   ├── theme/
│           │   ├── navigation/
│           │   ├── login/
│           │   ├── status/
│           │   └── components/
│           ├── widget/
│           │   ├── UsageGlanceWidget.kt
│           │   └── UsageWidgetReceiver.kt
│           ├── worker/
│           │   └── UsageRefreshWorker.kt
│           └── notification/
│               ├── NotificationHelper.kt
│               └── RefreshActionReceiver.kt
```

---

## Permissions

| Permission | Why |
|---|---|
| `INTERNET` | Call the claude.ai usage endpoint over HTTPS |
| `POST_NOTIFICATIONS` | Show reset notifications (Android 13+ requires runtime permission) |

No `WAKE_LOCK`, no `BOOT_COMPLETED`, no `ACCESS_NETWORK_STATE`, no location,
no camera, no storage access.
