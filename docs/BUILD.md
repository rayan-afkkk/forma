# Build instructions

## Requirements

- JDK 17 or newer. The Gradle wrapper (Gradle 9.6.0) downloads Gradle itself.
- Android SDK with platform 36 and build tools. The easiest route is a current Android Studio.
- Network access to `https://dl.google.com` (Google Maven) and `https://repo.maven.apache.org` (Maven Central).

## Android app

```bash
./gradlew :app:assembleDebug          # debug APK: app/build/outputs/apk/debug/
./gradlew :app:testDebugUnitTest      # Robolectric tests for Room and DataStore (app/src/test)
./gradlew :app:installDebug           # install on a connected device or emulator
```

- The debug build has the application ID `app.forma.debug`. It uses the **development entitlement
  provider**, a local Free/Pro switch under You › Developer options. That provider is in the
  `debug` source set only, so release builds cannot contain it.
- To test real Google Play Billing in a debug build, set `USE_PLAY_BILLING` to `true` in
  `app/build.gradle.kts`. See [BILLING.md](BILLING.md).

### Release

`./gradlew :app:bundleRelease` builds an R8-minified App Bundle. Signing is not in source control.
Before release:

1. Create an upload key and configure signing, for example with a `keystore.properties` file
   that is not committed, or with Play App Signing and CI secrets.
2. Choose the final application ID. It is permanent once the app is published.
3. Set `versionCode` and `versionName`.

## JVM verification build (no Google Maven needed)

```bash
./gradlew -p verification check
```

The `verification/` directory is a separate Gradle build that uses only Maven Central. It compiles:

- `core/model`, `core/engine`, `core/domain` and `presentation`, with their unit tests.
- `desktop-harness`, which compiles the **same shared UI source files** from
  `app/src/main/kotlin/app/forma/ui/**` with Compose Multiplatform for desktop. It runs Compose UI
  flow tests and renders screenshots into `docs/screenshots/`.

Android-only files (`*.android.kt`, and everything under `app/forma/android`) are excluded there.
The harness provides its own versions of the small platform seams listed in
`ui/platform/PlatformContracts.kt`.

Compose for desktop is pinned to 1.5.12, the newest release whose desktop dependencies resolve
entirely from Maven Central. Newer releases need androidx artifacts that are only on Google Maven.
Shared UI code therefore uses only Compose APIs that exist both in Compose 1.5 and in the
Compose BOM 2026.09 used by the app.

## Toolchain versions

Versions are kept in `gradle/libs.versions.toml`.

- **Maven Central artifacts:** checked by resolving them in the verification build.
- **Google Maven artifacts:** taken from the official release notes and release tables as of
  1 October 2026. These are AGP 9.4.0, Compose BOM 2026.09.00, Activity, Lifecycle, Navigation,
  Room, DataStore, WorkManager, Core and Play Billing 9.1.0.
- **What is unchecked:** these Google Maven artifacts could not be downloaded in the authoring
  environment. Their resolution and their compatibility with each other are therefore
  **unverified**.

| Area | Choice | Notes |
|---|---|---|
| SDK levels | compileSdk and targetSdk 36, minSdk 26 | Play requires target 36 for new apps and updates from 31 Aug 2026 |
| Kotlin | Kotlin 2.4.20 with AGP 9's built-in Kotlin support | So there is no `kotlin-android` plugin |
| Room | KSP 2.3.12 | KSP 2.x versions are no longer tied to Kotlin versions |

The Gradle wrapper has no `distributionSha256Sum`. The checksum URL was unreachable in the
authoring environment, so add it once you have network access.

## If the first Android build fails

The app module has never been compiled, so expect a small number of compile errors on the first
run. Most likely they will be imports or API signature drift in the Android-only files:

- `PlayBillingGateway.kt`
- `AndroidRepositories.kt`
- `Database.kt`
- `Platform.android.kt`
- `FormaNavHost.kt`
- `MainActivity.kt`

The shared UI and all logic are already compiled and tested, so fixes should stay local to these
files.

## Why there is no APK

The authoring environment's network policy blocked `dl.google.com`, and `maven.google.com`
redirects there. As a result:

- the Android Gradle Plugin, AndroidX libraries and Play Billing could not be downloaded;
- the Android SDK could not be installed.

To build in a similar cloud environment, allow `dl.google.com` in that environment's network
settings.
