# Test results

**Run:** 1 October 2026
**Command:** `./gradlew -p verification check` (JVM, Linux, JDK 21)
**Result:** **101 tests, 0 failures, 0 skipped**

| Suite | Module | Tests | Result |
|---|---|---|---|
| CatalogTest | core/engine | 5 | pass |
| ConstraintTest | core/engine | 8 | pass |
| ProgressionTest | core/engine | 12 | pass |
| AdjustmentTest | core/engine | 9 | pass |
| ScheduleTest | core/engine | 7 | pass |
| EquipmentLoadsTest | core/engine | 6 | pass |
| SessionLifecycleTest | core/domain | 11 | pass |
| PreferencesAndPlanningTest | core/domain | 10 | pass |
| AccessAndBackupTest | core/domain | 8 | pass |
| HolderFlowTest | presentation | 10 | pass |
| ReminderTimingTest | presentation | 4 | pass |
| FlowUiTest (Compose UI) | desktop-harness | 5 | pass |
| ScreenshotTest | desktop-harness | 5 | pass |
| GalleryTest | desktop-harness | 1 | pass |

## Not run

- **`app/src/test/.../RoomRepositoriesTest.kt`.** This Robolectric test runs the real services over
  Room and DataStore. It is written but **not run**, because the Android module cannot build here.
- **Android build, lint and instrumented tests.** None were run.
- **Device testing.** None was done. No emulator or physical device was used.

## Acceptance criteria from the brief

"Verified" means a passing automated test exercises the behavior on the JVM. The Android persistence
layer (Room and DataStore) is not covered by these runs; the in-memory repositories implement the
same interfaces.

| # | Criterion | Evidence | Status |
|---|---|---|---|
| 1 | Permanent exclusions never appear in new workouts | ConstraintTest: *permanently excluded exercises never appear in generated workouts*; *temporary replacements cannot bring back an excluded exercise*. PreferencesAndPlanningTest: *exclusions survive restarts, program changes and regeneration until restored*. FlowUiTest: *excludingFromThePreview…* | Verified (JVM) |
| 2 | A temporary swap does not change permanent preferences | PreferencesAndPlanningTest: *a temporary swap never changes permanent preferences*. AdjustmentTest: *a temporary replacement applies to this plan and the slot returns to the original next time* | Verified (JVM) |
| 3 | Plans never require unavailable equipment or weights | ConstraintTest: *plans never require equipment or weights the person does not have*; *a single fixed dumbbell is never prescribed as a pair*. EquipmentLoadsTest (4 tests) | Verified (JVM) |
| 4 | Conflicting constraints produce an explanation | ConstraintTest: *conflicting constraints produce a plain-language explanation…*. AdjustmentTest: *no suitable replacement is explained*; *an impractically short request is explained…* | Verified (JVM) |
| 5 | Progression uses actual performance | ProgressionTest (12 tests), including *progression starts from the load actually used, not the load that was planned* | Verified (JVM) |
| 6 | Missed sessions do not count as completed | ScheduleTest: *missed planned days are never counted as completed*. PreferencesAndPlanningTest: *missed planned days are shown as not done and never counted* | Verified (JVM) |
| 7 | Returning after a break preserves history | PreferencesAndPlanningTest: *returning after a long break offers a check-in and preserves all history*. ScheduleTest (3 check-in tests). AdjustmentTest: *returning after a break eases main work…* | Verified (JVM) |
| 8 | Timers and session state survive interruption and recreation | SessionLifecycleTest: *rest timers are derived from stored timestamps and survive process death*; *pausing freezes timers…*. HolderFlowTest and FlowUiTest: *back during a workout… save and exit keeps the session resumable* | Logic verified (JVM). Android process death and ViewModel recreation **unverified** |
| 9 | Completion is recorded once | SessionLifecycleTest: *completion is recorded exactly once even with repeated taps*; *logging the same set twice never duplicates it*. The player also guards against double taps | Verified (JVM); Room `WHERE status='IN_PROGRESS'` path **unverified** |
| 10 | Partial sessions are represented honestly | SessionLifecycleTest: *skipped work is represented honestly as partial*. HolderFlowTest: *finishing early asks first and saves an honest partial session*. ScheduleTest: *…partial sessions are shown as partial* | Verified (JVM) |
| 11 | Unit conversion does not corrupt stored performance | PreferencesAndPlanningTest: *changing the display unit never rewrites stored performance*. EquipmentLoadsTest: *stored loads keep their unit…*. ProgressionTest: *pound-based equipment progresses in pounds* | Verified (JVM) |
| 12 | Theme choice persists | HolderFlowTest: *theme choice persists across app restarts* (in-memory store recreated). Light and dark screenshots | Logic verified. DataStore persistence on Android **unverified** |
| 13 | Large text remains usable | FlowUiTest: *largeTextOnASmallPhoneKeepsPrimaryActionsReachable* (360 dp at 200% font). Screenshots at 150% and 200% were inspected | Verified (JVM render). Android **unverified** |
| 14 | Export/import round-trips supported data | AccessAndBackupTest: *export and import round-trip all supported data*; *importing cannot reset the free sample and rejects other files*; *csv export…* | Verified (JVM). Storage Access Framework picker **unverified** |
| 15 | Free/Pro gating works without locking existing history | AccessAndBackupTest: *free users get three adaptive sessions…*; *pro content is gated…*; *an expiring entitlement never interrupts an active workout and keeps history*. ProgressionTest: *reductions still apply without adaptive access* | Verified (JVM) with the development provider |
| 16 | Purchase failures or cancellation do not crash | AccessAndBackupTest: *failed and cancelled purchases leave the person on free without errors*. HolderFlowTest: *purchase cancellation and failure leave the person on free without crashing* | Verified against the development provider. Real Play Billing **unverified** |

## Visual inspection

The screenshots in `docs/screenshots/` are rendered by the JVM harness from the real shared UI
code, with the bundled fonts and seeded data. They are not device captures.

| Folder | Configuration |
|---|---|
| `phone/` | 411×891 dp, dark |
| `phone-light/` | Light theme |
| `small/` | 360×640 dp |
| `largetext/` | 150% font |
| `small-hugetext/` | 360×640 dp at 200% font |
| `dev/gallery-*.png` | Component gallery |

Problems found by inspecting them and then fixed:

- stat tiles stretched to full width;
- week labels were truncated;
- the bottom navigation and button labels overflowed at 200% text;
- two categories had the same icon.

Not inspected:

- real device rendering, including system bars and insets, cut-outs and gesture navigation;
- TalkBack behavior;
- animations.
