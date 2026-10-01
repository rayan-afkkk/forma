# Forma

*Working name only. "Forma" is temporary and has not been checked as a brand or trademark.*

**Open the app and get a workout you can actually do.**

Forma is a native Android home-workout app (Kotlin and Jetpack Compose). It plans each session from
the person's ability, the equipment they actually own, the time they have today, their history,
the exercises they have excluded, and how recent sessions felt. Everything runs offline. There are no
accounts.

> **Build status.** The training engine, domain services, screen logic and shared Compose UI are
> compiled and tested on the JVM in this repository (101 tests pass, and screenshots are rendered).
> The Android application module itself **has not been compiled** in the authoring environment,
> because Google's Maven repository (`dl.google.com`) was blocked there. No APK was produced and no
> device testing was done. See [docs/READINESS.md](docs/READINESS.md).

## Project layout

| Module | What it contains | Verified here |
|---|---|---|
| `core/model` | Domain types: equipment, loads and units, exercises, plans, sessions, settings, entitlement | Compiled and tested (JVM) |
| `core/engine` | Deterministic, versioned training engine and the draft exercise and program catalog | Compiled; 47 tests |
| `core/domain` | Planning, the session state machine, repositories, backup, access and billing contracts | Compiled; 29 tests |
| `presentation` | Screen logic as pure-Kotlin state holders (wrapped in ViewModels on Android) | Compiled; 14 tests |
| `app/src/main/kotlin/app/forma/ui` | All Compose UI: theme, components, icons, illustrations, screens | Compiled with Compose Desktop; 5 UI flow tests and 5 screenshot suites |
| `app/src/main/kotlin/app/forma/android` | Android only: Room, DataStore, Play Billing, WorkManager, Activity, navigation | **Not compiled** |
| `verification/` | JVM-only build that compiles and tests everything above without Google Maven | Used for all results |

## Quick start

```bash
# Full Android build (needs Google Maven access and Android SDK 36)
./gradlew :app:assembleDebug

# JVM verification: engine, domain, presentation and Compose UI tests plus screenshots
./gradlew -p verification check
```

Details: [docs/BUILD.md](docs/BUILD.md).

## Documentation

- [Build instructions](docs/BUILD.md)
- [Training rules and constraints](docs/TRAINING_RULES.md)
- [Test results](docs/TEST_RESULTS.md)
- [Readiness assessment](docs/READINESS.md)
- [Billing setup and monetization](docs/BILLING.md)
- [Missing production assets and external setup](docs/EXTERNAL_SETUP.md)
- [Fitness content review checklist](docs/CONTENT_REVIEW_CHECKLIST.md)
- [Asset and license inventory](docs/ASSETS_AND_LICENSES.md)
- [Design system notes and contrast table](docs/DESIGN.md)
- [Data storage, migrations and time zones](docs/DATA.md)
- [Privacy policy draft](docs/PRIVACY_DRAFT.md)
- Screenshots: [docs/screenshots](docs/screenshots). These are JVM renders of the real shared UI, not device captures.
