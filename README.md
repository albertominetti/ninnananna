# NinnaNanna 🌙

A **native Android** app to download the audio of a lullaby (or of any YouTube video)
and play it **offline** full screen, with themes designed for a kids' room.

Everything runs **on-device**: no backend, no account, no ads.
Audio files are saved in the app's **internal storage**
(`filesDir/lullabies`), safe from other users/apps.

> ⚠️ **Responsible use** — read the [YouTube legal notes](#youtube-legal-notes) at the bottom.
> Download **only** your own content, content under a Creative Commons/free license or for
> which you have authorization. Respect YouTube's Terms of Service.

---

## Features

| Area | What it does |
|---|---|
| **Main screen** | Field to paste a YouTube URL + **"Download audio"** button |
| **Intents** | `ACTION_SEND` (Share → NinnaNanna) and `ACTION_VIEW` (links `youtube.com/watch`, `youtu.be`, …): opens the app, pre-fills the field and starts the download |
| **Audio list** | File name, duration, size and date; sorted by name |
| **Playback** | **Single player** at a time (ExoPlayer singleton): Play / Stop for each item |
| **Mini-player bar** | Bottom bar with track title + **Stop** button |
| **File management** | **Rename** and **Delete** each audio |
| **Settings** | Theme **Light / Dark / Amoled** (pure black), **Keep screen on** (`FLAG_KEEP_SCREEN_ON`), **Reset all** with confirmation dialog |
| **Downloading** | Best **progressive audio stream** resolution with **NewPipeExtractor**, download with **OkHttp** (system `DownloadManager` not used: direct HTTP download) |

## Screenshots

> 📷 _Add real app screenshots here (Main screen, Mini-player, Settings)._

| Main screen | Settings |
|---|---|
| _placeholder_ | _placeholder_ |

## Tech stack

- **Kotlin** + **Jetpack Compose** (Material 3)
- **Gradle Kotlin DSL** — **Gradle 8.7** wrapper
- **AGP 8.2.2** · `minSdk 26` · `targetSdk 34` · package `com.alberto.ninnananna`
- **ExoPlayer** `androidx.media3:media3-exoplayer` (local player)
- **NewPipeExtractor** `com.github.TeamNewPipe:NewPipeExtractor:v0.26.5` (on-device stream resolution)
- **OkHttp** `com.squareup.okhttp3:okhttp:4.12.0` (extractor downloader + file download)
- **DataStore Preferences** (theme / keep-screen-on settings)
- **Navigation Compose** (main screen ↔ settings)
- **No** `yt-dlp` dependency, no backend, no ads

### Project structure

```
ninnananna/
├── settings.gradle.kts
├── build.gradle.kts
├── gradle.properties
├── gradle/wrapper/…                    # Gradle 8.7 wrapper
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml         # INTERNET + SEND/VIEW + FOREGROUND_SERVICE_MEDIA_PLAYBACK + POST_NOTIFICATIONS
│       ├── res/…                       # strings, themes, adaptive launcher icon
│       └── java/com/alberto/ninnananna/
│           ├── MainActivity.kt         # activity + SEND/VIEW intents + FLAG_KEEP_SCREEN_ON
│           ├── NinnanannaApp.kt        # theme + navigation + DataStore (SettingsStore)
│           ├── Theme.kt                # Light / Dark / Amoled color schemes
│           ├── DownloadRepository.kt   # NewPipeExtractor + OkHttp + filesDir/lullabies
│           ├── PlayerManager.kt        # ExoPlayer singleton (single player at a time)
│           ├── LullabyList.kt          # main screen + mini-player bar
│           └── SettingsScreen.kt       # settings + reset
├── scripts/build-apk.sh                # automatic build (see below)
├── .github/workflows/release.yml       # APK/AAB release on v* tags
└── README.md
```

## How to build

### Prerequisites

- **JDK 17 or 21** (recommended). Set `JAVA_HOME` if it's not already in the PATH.
- **Android SDK** (optional): if `ANDROID_SDK_ROOT`/`ANDROID_HOME` are not configured,
  the script installs it automatically into `~/android-sdk`.

### With the automatic script (recommended)

`scripts/build-apk.sh`:

1. checks `JAVA_HOME`;
2. installs **cmdline-tools** if `ANDROID_SDK_ROOT` is missing;
3. accepts the **SDK licenses**;
4. installs `platforms;android-34` and `build-tools;34.0.0`;
5. runs `./gradlew assembleDebug` (default) or `assembleRelease`;
6. copies the final APK into `./dist/`.

```bash
cd ninnananna

# Debug APK
./scripts/build-apk.sh

# Release APK
# (without a keystore: signed with the debug key, installable but not for Google Play)
./scripts/build-apk.sh release

# Release with a real signature (optional environment variables)
export KEYSTORE_FILE=/path/to/keystore.jks
export KEYSTORE_PASSWORD=...
export KEY_ALIAS=...
export KEY_PASSWORD=...
./scripts/build-apk.sh release

# Or keystore as base64 (handy for CI):
export KEYSTORE_BASE64="$(base64 -w0 keystore.jks)"
./scripts/build-apk.sh release
```

The script is idempotent: on the second run it skips the already-installed SDK setup.

### Manually (debug)

```bash
cd ninnananna
export ANDROID_SDK_ROOT=$HOME/android-sdk   # if not already set
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

## How to install the APK

Connect an Android device with **USB debugging** enabled or use an emulator:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Alternatively: copy the APK to the phone and tap it to install
(allow "install from unknown sources" if prompted).
The app only requires the `INTERNET` permission for downloading; it does not ask for any other permissions.

## YouTube legal notes

- This app uses **NewPipeExtractor**, an open source (GPLv3) library that **does not** use
  official YouTube APIs. Stream availability can change and is outside the app's control.
- YouTube and individual videos are subject to **Google/YouTube Terms of Service**.
- **Download exclusively**:
  - videos you own;
  - videos under a **Creative Commons / free license** (visible in the video description);
  - content for which you have the author's explicit permission.
- **Do not** download copyrighted music or videos without authorization:
  in many countries it is illegal and, in any case, contrary to YouTube's ToS.
- The app's author is not responsible for misuse.

> A typical legal use case: downloading **your own** lullabies
> (e.g. personal recordings uploaded to YouTube) to play them offline at night.

## Automatic releases (GitHub Actions)

On a `v*` tag (e.g. `git tag v1.0.0 && git push origin v1.0.0`) the workflow
`.github/workflows/release.yml`:

1. sets up JDK 17 (Temurin) with `actions/setup-java` — the **Android SDK** is already
   preinstalled on the runner (no more `android-actions/setup-android`, now deprecated and broken);
2. builds `assembleRelease` + `bundleRelease`;
3. uploads APK and AAB as **artifacts**;
4. creates a **GitHub Release** with the attached files (`softprops/action-gh-release`).

To sign with the release key on GitHub, add the **repository secrets**:
`KEYSTORE_BASE64` (base64-encoded keystore), `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.
Without secrets the build is signed with the debug key (installable, not publishable on Google Play).

## Author

Alberto Minetti — app published for demo/testing purposes under the GPLv3 license.