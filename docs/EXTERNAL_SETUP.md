# Missing production assets and external setup

## Blocking release

| Item | Notes |
|---|---|
| **Final app name and trademark check** | "Forma" is a temporary working name. Search trademarks and Play listings in your launch markets. |
| **Final application ID** | Currently `app.forma` (debug: `app.forma.debug`). It is permanent once published. |
| **Upload key and signing** | Create the key, enrol in Play App Signing and keep secrets out of the repository. |
| **Build on a machine with Google Maven access** | Fix any first-compile issues in the Android-only files (see [BUILD.md](BUILD.md)). Run lint, the Robolectric tests and the instrumented tests. |
| **Device testing** | See the matrix below. |
| **Play Console subscription products** | Product `forma_pro` with base plans `monthly` and `annual`, plus license testers. See [BILLING.md](BILLING.md). |
| **Qualified content review** | All exercises, cues, programs and progression thresholds. See [CONTENT_REVIEW_CHECKLIST.md](CONTENT_REVIEW_CHECKLIST.md). |
| **Exercise demonstrations** | Every exercise shows an original outline pose figure for its movement pattern, labelled "Development placeholder", instead of a demonstration. |
| **Privacy policy** | Finalise [PRIVACY_DRAFT.md](PRIVACY_DRAFT.md) with legal review, host it at a public URL and link it in Play Console and the app. |
| **Play Data safety form** | Based on the privacy policy: no data collected or shared by the app itself. Google Play Billing handles payments. |
| **Health and fitness declaration** | Complete Play's health apps declaration if it applies in your markets. |
| **Store listing** | Icon (512 px), feature graphic, phone screenshots from a real device, short and full descriptions, content rating questionnaire. |
| **Support contact** | `AppInfo.supportEmail` is empty. Until it is set, Help shows no email action. |

### Exercise demonstrations

Each exercise needs a demonstration made by a qualified source. Options:

- commissioned illustrations or animations in the outline style (see [DESIGN.md](DESIGN.md));
- short looping videos.

Plan for 48 exercises and both themes.

### Device testing matrix

| Area | What to test |
|---|---|
| Android versions | 8.0 (API 26) and 13 or later (notification permission) |
| Screens | A small phone, a large phone and a tablet |
| Navigation | Gesture and three-button navigation |
| Accessibility | 200% font and display size, TalkBack |
| Lifecycle | Process death mid-workout (Developer options › "Don't keep activities"), time-zone change |
| Billing | Purchase flows with license testers |

## Should do before or soon after launch

- **Adaptive launcher icon.** The icon is a placeholder drawn in vector XML.
  - Make a final adaptive icon with a monochrome layer for themed icons.
  - Add the 512 px store icon.
- **Notification icon.** `ic_notification` is a placeholder; replace it with the final one.
- **Billing backend.** Add server-side purchase verification and Real-Time Developer Notifications
  (see [BILLING.md](BILLING.md)).
- **Localisation.** All copy is English and lives in Kotlin state holders and composables. Before
  translating:
  1. Move it to string resources, or to a shared localisation layer.
  2. Format numbers, dates, weights and plurals with the device locale. Some helpers in
     `presentation/Format.kt` already assume English.
- **Crash reporting.** None is included, by design (no third-party SDKs). Consider an opt-in,
  privacy-reviewed option, or rely on Play Console vitals.
- **Accessibility audit.** Have someone audit with TalkBack and Switch Access on real devices.
- **Gradle wrapper checksum.** Add `distributionSha256Sum` to `gradle/wrapper/gradle-wrapper.properties`.

## Environment note

The authoring environment blocked `dl.google.com`, so the Android build could not run there. To
build in that cloud environment, allow `dl.google.com` in its network settings, or build locally
with Android Studio.
