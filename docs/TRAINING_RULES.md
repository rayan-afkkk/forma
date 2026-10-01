# Training rules and constraints

Rule set version: **`rules-2026.10-draft.1`**. Each generated plan and each explained change stores
this version, so history always shows which rules produced it.

All thresholds are in `core/engine/.../TrainingRules.kt`. All rules and content are **drafts**. A
qualified fitness professional must review them before release; see
[CONTENT_REVIEW_CHECKLIST.md](CONTENT_REVIEW_CHECKLIST.md).

The engine is plain Kotlin with no UI or Android code. It is deterministic: the same inputs always
give the same plan. The JVM tests cover it (see [TEST_RESULTS.md](TEST_RESULTS.md)).

## 1. Inputs

- **Profile:**
  - experience level (new, some, regular);
  - goal;
  - usual session length;
  - quiet mode;
  - limited space.
- **Equipment profiles:**
  - bodyweight;
  - fixed dumbbells (each weight, pair or single);
  - adjustable dumbbells (minimum, maximum and increment, pair or single);
  - a bench;
  - unit (kg or lb).
- **Permanent exclusions.**
- **Finished-session history.** This is what was actually performed: sets, reps, seconds and load
  per set, plus how the session felt.
- **Today's one-day adjustments.** These are:
  - time available;
  - easier session;
  - quiet today;
  - a different equipment profile;
  - replaced, easier or omitted exercises.

## 2. Hard constraints (never relaxed silently)

An exercise is unavailable when any of these apply:

| Constraint | Explanation shown |
|---|---|
| Permanently excluded | "you excluded it" |
| Needs dumbbells the active profile does not have | "it needs dumbbells" |
| Needs a matching pair and only a single dumbbell is owned | "it needs a matching pair of dumbbells" |
| Needs a bench that is not owned | "it needs a bench" |
| Jumping or stomping while quiet mode is on (permanently or for today) | "it involves jumping or stomping" |
| Needs room to step while limited space is on | "it needs room to step" |
| An advanced variation for a beginner | "it is a more advanced variation" |

**Loads** are chosen only from weights the person can actually make:

- fixed dumbbells use only the listed weights;
- adjustable dumbbells use the minimum to the maximum, in the stated increment;
- a pair needs two dumbbells of the same weight.

**Snapping rules:**

- When a carried-forward load is not available, it snaps to the nearest available weight that is
  **not heavier**.
- If nothing lighter exists, it uses the lightest available weight.

**Units:**

- Stored loads keep their own unit.
- Converted display values are marked with "≈".

**When a slot has no valid exercise**, the plan leaves that slot out and says why in plain language.
For example: "Bench press was left out: it needs a bench." It never substitutes something that
breaks a constraint.

## 3. Choosing exercises

- **Slots.** Session templates are lists of slots, such as "squat pattern, main" or "core,
  optional". Each slot lists candidate exercises in order of preference.
- **Continuity.** A slot reuses the exercise the person last did in it. This lets progression build
  on real history. A one-day replacement does not count as last done: the original returns next
  time.
- **No duplicates.** An exercise already in the workout is not used again in another slot.
- **Replacements.** Options are offered in this order:
  1. reviewed substitutes;
  2. the same variation family;
  3. the same movement pattern.
  All of them are checked against every hard constraint. If none fit, the app explains why and
  offers to leave the exercise out for today.
- **"Make easier".** Picks an easier variation in the same family when one exists. Otherwise it
  lowers the target for today.
- **"Exclude permanently".** Asks for confirmation and is listed under You › Excluded exercises,
  where it can be restored. It is a separate action from replacing for today.

## 4. Progression (from actual performance)

Each exercise is judged on its most recent attempted exposure, with the one before it for context.
Skipped exercises and sessions that ended early carry **no** signal.

| Last time | Result |
|---|---|
| Rated **too hard** | **Reduce** |
| Below target **twice in a row** | **Reduce** |
| Below target once | **Hold** ("Same targets as last time. Aim to complete every set.") |
| Rated **hard** | **Hold** |
| Every planned set at the top of the target, not rated hard | **Progress** |
| Otherwise | Hold quietly (no message) |

"Below target" means one of:

- fewer sets than planned;
- reps under the minimum;
- a timed hold under 70% of the target.

The starting point is always **what was performed**, not what was planned:

- **Load:** the last load actually used, snapped to the current equipment.
- **Target:** the last target.
- **Sets:** the last *programmed* number of sets. A one-day "easier session" or a shortened session
  therefore never lowers future sessions permanently.

**Progress** tries these steps in order and uses the first that applies:

1. **Heavier dumbbell.** Used only if the next available weight is a reasonable jump: at most
   2.5 kg, 5 lb, or 25% of the current load. The rep range then returns to the slot's starting
   range.
2. **Longer hold.** Timed holds get 5 more seconds, up to 60 seconds.
3. **Harder variation.** For bodyweight exercises, when one fits all constraints.
4. **More reps.** The range grows by 2, up to 15 reps when loaded or 20 for bodyweight. The message
   says why, for example "The next weight up (12.5 kg) is a big jump, so today aims for a few more
   reps instead."
5. **One more set,** up to the slot's maximum.
6. **A harder variation** for loaded exercises.
7. Otherwise **hold**, saying the exercise has reached the top of what the equipment allows.

**Reduce** tries these steps in order:

1. One available weight lighter.
2. An easier variation.
3. A smaller target, never below 4 reps or 10 seconds.

The very first time a person does an exercise, the app gives guidance on choosing a starting
weight. It does not invent a number.

### Free and Pro

Free users get **3 adaptive sessions**. A session counts toward these only when at least 50% of its
main sets were done.

After that, for free users:

- the plan carries forward what they last performed;
- **reductions still apply**, so a free plan never stays too hard;
- automatic increases need Pro.

History is never locked.

## 5. Duration

**Estimate:** each set takes its reps × 3.5 s (or the hold time), plus rest, plus 25 s per
transition between exercises.

When the requested time is shorter than the plan, the plan is shortened in this fixed order:

1. Optional main work.
2. Optional warm-up and cool-down.
3. Extra sets.
4. Shorter rests, down to 45 s for main work.
5. The lowest-priority main exercises. At least 3 main exercises are always kept.

The result may run up to 10% over the requested time. Each removal is listed in the preview.

If even the shortest version does not fit, the app says so and shows the shortest practical version.
It does not produce a meaningless session.

## 6. Schedule, missed days and returning after a break

- **Missed days.** A planned day with no session is shown neutrally. It never counts as done. The
  plan simply continues with the next session in the rotation.
- **Rotation.** A program session moves the rotation forward only when at least 50% of its main
  sets were done.
- **Moving a session.** Today's session can move to another day this week or in the next few days.
  This does not add sessions.
- **Return check-in.** It appears only after a real gap since the last finished session, never for
  missing a day or two:

| Days since last session | What happens |
|---|---|
| Up to 7 | Nothing |
| 8–20 | Short break: offers an optional easier first session back |
| More than 20 | Longer break: recommends easing in over 2 sessions |

  The check-in is asked once per break. The wording is neutral and never guilt-based. History is
  always kept.

## 7. Sessions and interruptions

- **Timers** are derived from stored wall-clock timestamps. They survive the app being closed,
  process death and device restarts. Pausing freezes both the timers and active time.
- **Sets** are saved as they are logged, keyed by session, item and set number. Retries therefore
  never create duplicates. The last set can be undone, and logged sets can be corrected afterwards.
- **Finishing.** "Save and exit" keeps the session resumable. Finishing early saves an honest
  **partial** session after confirmation.
- **Completion is recorded once.** The database update only applies to a session that is still in
  progress.
- **Entitlement changes never interrupt a workout in progress.**

## 8. Content

The catalog has 48 exercises, 3 programs and 7 standalone sessions. All have review status
`DRAFT`. See [CONTENT_REVIEW_CHECKLIST.md](CONTENT_REVIEW_CHECKLIST.md).

| Program | Access | Sessions |
|---|---|---|
| Foundations | Free | Full body A and Full body B |
| Dumbbell Strength | Pro | Squat and press; Hinge and pull; Single-leg and overhead |
| Steady Habit | Pro | Steady strength; Mobility flow; Steady conditioning |

**Standalone sessions:**

- First steps
- 15-minute full body
- Quiet 20
- 10-minute mobility reset
- Desk break
- 20-minute dumbbell express (Pro)
- 12-minute core (Pro)
