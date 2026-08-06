# AI Limit Tracker (formerly Claude Tracker)

A personal-use Android app that tracks your usage limits (session %, weekly %, reset times) across popular AI platforms: **Claude.ai**, **ChatGPT Plus/Pro (Codex)**, and **Google Antigravity**. 
By capturing session cookies or OAuth tokens, it securely reads the same internal usage endpoints that the official web apps use.

**Not for Play Store distribution** — sideloaded directly to devices. Each user logs into their own accounts independently.

---

## Features

- **Multi-Platform Support**: Track usage limits for **Claude.ai**, **Codex (ChatGPT Plus/Pro)**, and **Antigravity** (Gemini Models, Claude & GPT Models).
- **Multi-Account Support**: Add and monitor multiple accounts across different platforms simultaneously.
- **Automated Login Flow**: Smart URL and OAuth detection automatically captures your session without any manual buttons after logging in.
- **Real-Time Usage Tracking**: Monitor your Session (5h) and Weekly usage percentages, along with exact reset timestamps.
- **Premium UI & Animations**: Beautifully crafted dark and white themes with OLED-friendly high-contrast colors, dynamic stagger animations, and swipe-to-delete interactions.
- **Premium Home Screen Widget**: A beautiful Glance widget with text-based progress bars, plan name badges, and color-coded status indicators (🟢🟡🔴).
- **Background Sync**: Runs in the background and sends you a notification exactly when your usage window resets.
- **Account Management**: Swipe to delete specific accounts or use the secure "Logout All" function with confirmation.
- **Privacy First**: Uses secure `EncryptedSharedPreferences` for your cookies and tokens. All data stays locally on your device.

---

## Quick Start

### 1. Build a debug APK

```bash
cd ClaudeTracker
./gradlew assembleDebug
```

The APK will be at `app/build/outputs/apk/debug/app-debug.apk`.

Install directly:
```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

### 2. Build a signed release APK (for sharing with friends)

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
Share this file directly — each recipient installs it and logs into their own AI accounts on first launch.

---

## How it works

### Login & credential capture

- **Claude & Codex**: The app opens a WebView to the respective login page. You log in normally. The app detects when the URL changes upon successful login, reads the session cookie from the WebView's cookie jar, and captures necessary details (like org ID or tokens).
- **Antigravity**: Uses a secure OAuth 2.0 authorization code flow to obtain access and refresh tokens. 
- All credentials and cookies are stored securely using Android's `EncryptedSharedPreferences`.

### Usage fetching

- **On app open**: The status screen immediately triggers a fresh fetch across all accounts concurrently.
- **Background**: A WorkManager periodic job runs every ~15 minutes (Android's minimum floor; actual timing may vary by OEM).
- **Manual**: Tap the refresh button on the status screen, or the refresh action on the home screen widget.

### Reset detection & notifications

This is the key differentiator — the app does **not** spam you with periodic notifications. Instead:

1. Each time the background worker fetches usage data, it compares the **session reset timestamp** and **weekly reset timestamp** from the API response against the last-known values stored locally.
2. If a reset timestamp has changed (meaning that usage window rolled over since the last check), a notification fires for that specific window:
   - "Your session usage limit has reset!" (session window)
   - "Your weekly usage limit has reset!" (weekly window)
3. If the timestamps haven't changed, no notification is sent — most poll cycles are silent.
4. The new timestamps are saved for the next comparison.

**Important**: Because this depends on WorkManager's poll cycle (~15-30 minutes in practice), there will be a delay between when the reset actually happens and when the notification fires.

### Home screen widget

- Always shows the latest cached session %, weekly %, and last-updated time.
- Updated on every successful background poll.
- Three visual states:
  - **Normal**: Shows usage percentages.
  - **Not logged in**: Shows "Tap to log in".
  - **Auth expired**: Shows "Session expired — tap to log in".
- Tap the refresh button to trigger an immediate one-time fetch.

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
|  UsageRepository (Handles multi-platform fetch logic)   |
|    +-- SecureStorage (EncryptedSharedPreferences)        |
|    +-- UsageApiClient (OkHttp -> Claude/Codex/Antigravity)|
|    +-- Widget SharedPreferences (cached data)           |
+---------------------------------------------------------+
|                   Background Layer                      |
|  UsageRefreshWorker (WorkManager CoroutineWorker)       |
|    +-- Calls UsageRepository.fetchAllUsage()            |
|    +-- Reset detection (timestamp comparison)           |
|    +-- NotificationHelper (reset alerts only)           |
|    +-- Widget update                                    |
+---------------------------------------------------------+
```

---

## Known limitations

| Limitation | Details |
|---|---|
| **Undocumented endpoints** | The usage APIs are internal to their respective platforms and can change or break without notice. If endpoints change, updates to `UsageApiClient.kt` and `UsageData.kt` will be necessary. |
| **Session expiration** | Session cookies and tokens expire periodically. When this happens, the app shows an "auth expired" state on the account card and routes you back to login. |
| **Background timing** | Android enforces a ~15-minute minimum for WorkManager periodic jobs. In practice, OEM battery optimizations (Samsung, Xiaomi, Huawei) can push this to 30+ minutes. The reset notification will lag accordingly. |
| **No real-time reset alerts** | Reset detection depends on polling. If you need to know the instant a limit resets, check the status screen manually. |
| **Per-device only** | Each device has its own login and data. No sync, no cloud backup, no data sharing between devices. |
| **No offline cache** | If the device is offline, the app shows the last successfully fetched data (marked with its timestamp) but cannot refresh. |
| **WebView login quirks** | Some login flows (e.g., Google OAuth redirects inside WebViews) may behave differently than in a full browser. If login fails, try clearing app data and retrying. |

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
│           ├── Config.kt                <- Constants and endpoints
│           ├── ClaudeTrackerApp.kt      <- Application + DI singletons
│           ├── MainActivity.kt          <- Single-activity Compose host
│           ├── data/
│           │   ├── model/
│           │   │   ├── UsageData.kt     <- Parsers for Claude/Codex/Antigravity JSON
│           │   │   └── UsageResult.kt
│           │   ├── local/
│           │   │   └── SecureStorage.kt
│           │   ├── remote/
│           │   │   └── UsageApiClient.kt<- Retrofit/OkHttp clients for all APIs
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
| `INTERNET` | Call the AI platform usage endpoints over HTTPS |
| `POST_NOTIFICATIONS` | Show reset notifications (Android 13+ requires runtime permission) |

No `WAKE_LOCK`, no `BOOT_COMPLETED`, no `ACCESS_NETWORK_STATE`, no location, no camera, no storage access.
<img width="706" height="1527" alt="PHOTO-2026-08-03-18-19-30" src="https://github.com/user-attachments/assets/4e30b1c6-ad6a-4634-9560-e3110003c66a" />
