# Data storage, migrations and time

## Where data lives

**Room database (`forma.db`, schema version 1)** holds:

- equipment profiles;
- exclusions;
- favourites;
- program enrollment;
- schedule moves;
- one-day plan adjustments;
- sessions with their full performed plan;
- set logs;
- the active player state.

**DataStore `user`** holds:

- profile and schedule;
- settings and reminders;
- the return check-in answer;
- the free-sample count.

All of this is one JSON document (`user_state_v1`), so updates are atomic. Unknown fields are
ignored and missing fields take their defaults.

**DataStore `billing`** holds the last confirmed entitlement (`entitlement_v1`). It is excluded from
device backup.

A corrupt DataStore file is replaced with defaults instead of crashing the app. Workout history in
Room is unaffected.

## Integrity rules

These rules are implemented in the domain layer and tested on the JVM.

- **Sets:**
  - each set is keyed by `(sessionId, itemKey, setNumber)`, so a retry replaces the set instead of
    adding a duplicate;
  - each set is written as it is logged;
  - each set stores its own load and unit.
- **Completion** is a conditional update (`WHERE status = 'IN_PROGRESS'`), so it is recorded once.
- **One session in progress.** Only one session can be in progress at a time.
- **Performed plan.** A session stores the plan that was actually performed. Later plan or catalog
  changes never rewrite history.
- **Units.** Changing the display unit never converts stored values.

## Migrations

- Room exports its schema to `app/schemas/`. Commit these files.
- `ALL_MIGRATIONS` (`android/data/Migrations.kt`) is empty, because version 1 is the first schema.
- For each future version:
  1. Bump `FormaDatabase.version`.
  2. Add an explicit `Migration` or an `AutoMigration`.
  3. Add a `MigrationTestHelper` test.
- **Destructive fallback is deliberately not enabled.** History must survive updates.
- **User state.** The DataStore JSON is versioned by key. A breaking change should write a new key
  and migrate from the old one on read.
- **Backup files.** They carry `format = "forma-backup"` and `version = 1`.
  - Import rejects other formats and newer versions.
  - Import replaces local data after confirmation.
  - Import never lowers the free-sample count.

**Unverified:** Android device backup of `forma.db` while Room has an open write-ahead log. Check
that a restore on a new device contains recent sessions. If it does not, include `forma.db-wal` in
the backup rules, or checkpoint the database before backup.

## Time and time zones

- **Session dates.** Each session stores `startedAt` (UTC epoch milliseconds), its `zoneId` and its
  `localDate`. A session done at 23:30 while travelling stays on the day it happened for the
  person.
- **"Today".** It is computed from the device's current zone.
- **Missed days.** These are derived from the planned dates and the finished sessions' local dates.
  Nothing is written for a missed day.
- **Timers.** They use stored wall-clock timestamps.
  - A rest ends at `restStartedAt + duration`, even if the app was closed.
  - If the device clock changes during a workout, the remaining time is clamped between zero and
    the full duration. It never goes negative or grows.
- **Reminders.** `ReminderTiming` computes the next planned day at the chosen local time.
  - A one-off WorkManager job fires then.
  - After each reminder, on app start, and on `TIMEZONE_CHANGED` and `TIME_SET`, the next one is
    rescheduled.
  - Daylight-saving changes keep the local time; a JVM test covers this.
