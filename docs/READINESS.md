# Readiness assessment

**Summary.**

- **What works:** the product logic, the shared UI and the screen flows are implemented and tested
  on the JVM.
- **What is untested:** the Android shell (persistence, billing, reminders, navigation host) is
  written but has **never been compiled or run**. No APK exists and no device testing was done.
- **Not ready for release.** Release also needs:
  - qualified content review;
  - exercise demonstrations;
  - Play Console setup;
  - real-device testing.

## 1. Implemented and verified (JVM tests and rendered UI)

- **Training engine** (`core/engine`, 47 tests):
  - hard constraints: equipment, pair or single, bench, quiet, space, level, exclusions;
  - explanations when constraints conflict;
  - load snapping to owned weights;
  - progression from actual performance;
  - reductions and holds;
  - duration fitting with explanations;
  - replacements, "make easier" and leaving an exercise out;
  - return check-ins;
  - week planning;
  - missed days never counted as done.
- **Domain services** (`core/domain`, 29 tests):
  - onboarding, with a real first-workout preview;
  - planning snapshots;
  - session lifecycle: start, log, undo, pause and resume, save and exit, partial finish,
    completion recorded once, editing after finishing;
  - timers derived from timestamps;
  - Free/Pro access with a 3-session adaptive sample;
  - entitlement expiry without interrupting a workout;
  - JSON backup export and import with validation;
  - CSV export;
  - deleting data.
- **Screen logic** (`presentation`, 14 tests): each screen's state holder, including:
  - Adjust today;
  - paywall cancellation and failure;
  - theme persistence;
  - empty states;
  - reminder timing across daylight-saving changes.
- **Compose UI** (compiled with Compose for desktop from the same source files):
  - 5 end-to-end UI flow tests, including the brief's first vertical slice:
    Onboarding → Today → Preview → Player → Completion → History;
  - screenshots on:
    - a standard phone, dark and light;
    - a 360 dp small phone;
    - 150% text;
    - 360 dp at 200% text.
- **Accessibility tokens:** every text token passes WCAG 4.5:1 ([DESIGN.md](DESIGN.md)).

## 2. Implemented but unverified (written, not compiled or run)

**Android shell** (`app/src/main/kotlin/app/forma/android`, `Platform.android.kt`):

| Area | What is written |
|---|---|
| Persistence | Room database, DAOs and repositories; DataStore user state and entitlement cache |
| Billing | `PlayBillingGateway`: offers, purchase, pending purchases, acknowledgement, restore, refresh on resume, 7-day offline grace, error mapping |
| Reminders | WorkManager worker, notification channel, permission request, reschedule on time-zone change |
| App and navigation | Application, manual DI container, `MainActivity` (edge-to-edge, theme-aware system bars); type-safe Navigation Compose routes; back-stack rules (tab switching, replacing the player with the summary, Back to Today from other tabs) |
| Platform seams | ViewModel-scoped state holders, lifecycle-aware collection, keep screen on, tone and vibration cues, file picker export and import, the "Remove animations" setting |
| Tests | Robolectric `RoomRepositoriesTest` (written, not run) |
| Build | `app/build.gradle.kts`, the manifest, resources, R8 rules, backup and data-extraction rules |

**Behaviour that needs a device to confirm:**

- process death in the middle of a workout;
- TalkBack;
- system bars and insets;
- notification delivery;
- real Play Billing.

**Versions.** The versions of the Google Maven artifacts could not be resolved here (see
[BUILD.md](BUILD.md)).

## 3. Requires external setup

See [EXTERNAL_SETUP.md](EXTERNAL_SETUP.md) and [BILLING.md](BILLING.md):

- **Build:** a machine with Google Maven access, or allowing `dl.google.com` in the cloud
  environment's network settings.
- **Release identity:**
  - signing keys;
  - the final application ID and app name.
- **Play Console:**
  - the `forma_pro` subscription with `monthly` and `annual` base plans;
  - license testers;
  - the Data safety form;
  - the store listing.
- **Privacy policy:** legal review and hosting.
- **Support email.**
- **Optional:** a billing backend for server-side verification and Real-Time Developer
  Notifications.

## 4. Requires qualified content review

See [CONTENT_REVIEW_CHECKLIST.md](CONTENT_REVIEW_CHECKLIST.md):

- all 48 exercises: cues, levels, flags and substitutes;
- all 3 programs and 7 sessions;
- every progression, reduction and return-after-break threshold;
- safety and disclaimer copy;
- whether a readiness questionnaire is needed.

## 5. Deferred

- **Exercise demonstration media.** Placeholders are shown.
- **Final launcher icon and notification icon.** Placeholders are used.
- **Localisation.** All copy is English and in code, not string resources. Number and date
  formatting is English-only.
- **Deeper Pro progress analysis.** Pro currently adds per-exercise trend charts only.
- **Tablet and foldable layouts.** These are single-column phone layouts that stretch.
- **Server-side purchase verification.** The free-sample counter can be reset by clearing app data.
- **Analytics.** Events are defined but not sent anywhere (see [PRIVACY_DRAFT.md](PRIVACY_DRAFT.md)).
- **Wear OS, Health Connect, accounts and cloud sync.** Out of scope.

## Known risks to check first on a real build

1. **First compile of the Android-only files.** Expect minor API drift, for example in Play
   Billing 9 method names, the Room 2.8 KSP setup, and AGP 9 built-in Kotlin with the Compose
   compiler plugin.
2. **Room write-ahead log and Android Auto Backup.** Confirm that a restore contains recent
   sessions ([DATA.md](DATA.md)).
3. **Play Billing pending-to-purchased transitions while the app is in the background.**
   Confirm that the refresh on resume picks them up.
4. **Material 3 bottom sheet and keyboard insets with edge-to-edge** on Android 15 and later.
