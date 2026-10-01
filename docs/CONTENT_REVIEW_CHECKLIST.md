# Fitness content review checklist

All bundled content is a **draft**. It was written to be conservative and plausible. It was **not**
written or reviewed by a qualified fitness professional.

Every exercise has `review = ReviewStatus.DRAFT`. A test (`CatalogTest`) fails if content claims to be
reviewed. When a reviewer signs off an item, change it to `ReviewStatus.TRAINER_REVIEWED` and update that test.

Reviewer: ____________________  Qualification: ____________________  Date: __________

## A. Safety copy

- [ ] Onboarding safety note: wording, placement, and whether it is enough for the launch markets.
- [ ] "Stop if you feel pain, dizziness or unusual shortness of breath" guidance in the player and
      Help.
- [ ] Guidance for people who are pregnant or postpartum, have heart conditions or injuries, or are
      returning after illness. Decide what the app must say and where.
- [ ] Is a pre-exercise readiness questionnaire (e.g. PAR-Q+) needed before onboarding finishes?
- [ ] Legal review of disclaimers and terms.

## B. Exercises (48), in `core/engine/.../catalog/Exercises.kt`

For **each** exercise, check:

- [ ] The name is clear and commonly understood.
- [ ] Setup, steps and form cues are accurate, short and safe. There are three to five cues.
- [ ] Common mistakes and "make it easier" notes.
- [ ] Movement pattern and muscle groups are correct.
- [ ] Equipment requirement: dumbbells, pair, single or goblet, and bench.
- [ ] Flags: quiet (no jumping or stomping) and space needed.
- [ ] Minimum experience level.
- [ ] Variation family and rank (easier ↔ harder order).
- [ ] Listed substitutes keep the training role.
- [ ] Default dose: sets, rep range or hold time, and rest.
- [ ] Bilateral or unilateral handling ("each side").

By group:

| Group | Exercises |
|---|---|
| Warm-up | March in place, step jacks, jumping jacks, arm circles, hip hinge drill, lunge with reach |
| Mobility and cool-down | Cat–cow, open book rotation, half-kneeling hip flexor stretch, standing hamstring stretch, child's pose, figure-four stretch |
| Squat and lunge | Squat to bench, bodyweight squat, slow-lowering squat, goblet squat, wall sit, supported split squat, split squat, reverse lunge, dumbbell split squat |
| Hinge | Glute bridge, dumbbell glute bridge, single-leg glute bridge, single-leg hip hinge, dumbbell Romanian deadlift |
| Push | Wall push-up, incline push-up, kneeling push-up, push-up, dumbbell floor press, dumbbell bench press, dumbbell shoulder press, pike push-up |
| Pull | One-arm dumbbell row, dumbbell bent-over row, bent-over reverse fly, prone Y raise |
| Core | Dead bug, bird dog, kneeling plank, forearm plank, side plank from knees, suitcase hold |
| Arms and conditioning | Dumbbell curl, shadow boxing, mountain climber, squat jump |

## C. Programs and sessions, in `core/engine/.../catalog/Programs.kt`

- [ ] **Foundations** (free). Full body A and B. Is it suitable for complete beginners with
      bodyweight only?
- [ ] **Dumbbell Strength** (Pro):
  - [ ] the three days: squat and press; hinge and pull; single-leg and overhead;
  - [ ] weekly volume per muscle group;
  - [ ] recovery between sessions.
- [ ] **Steady Habit** (Pro). Its strength, mobility and conditioning days are low impact.
- [ ] Recommended sessions per week for each program.
- [ ] Standalone sessions:
  - [ ] First steps
  - [ ] 15-minute full body
  - [ ] Quiet 20
  - [ ] 10-minute mobility reset
  - [ ] Desk break
  - [ ] 20-minute dumbbell express
  - [ ] 12-minute core
- [ ] Warm-up adequacy before main work, including in shortened sessions. Warm-ups can be removed
      when shortening. Should a minimum always remain?
- [ ] Slot candidate order: the preferred exercise and fallbacks for each slot.

## D. Progression and adjustment rules, in `TrainingRules.kt` and [TRAINING_RULES.md](TRAINING_RULES.md)

- [ ] Load jump limits: 2.5 kg, 5 lb or 25%.
- [ ] Rep caps: 15 loaded, 20 bodyweight. Rep extension step: 2.
- [ ] Hold progression: +5 s up to 60 s, with a floor of 10 s.
- [ ] Reduction triggers:
  - [ ] rated too hard;
  - [ ] below target twice;
  - [ ] the 70% threshold for timed holds.
- [ ] Reduction floor: 4 reps.
- [ ] Return after a break:
  - [ ] the 7, 20 and over-20 day thresholds;
  - [ ] how many easier sessions are offered;
  - [ ] how much easier they are.
- [ ] "Easier session" for today: how much main volume is removed.
- [ ] Shortening order and the minimum of 3 main exercises.
- [ ] First-time guidance text for choosing a starting weight.
- [ ] Beginner level gate: which variations count as advanced.

## E. Copy tone

- [ ] No guilt, streak pressure or body-image language anywhere. Check:
  - [ ] Today;
  - [ ] the return check-in;
  - [ ] Completion;
  - [ ] the Paywall;
  - [ ] reminders.
- [ ] Explanations of changes are accurate and not overconfident.
- [ ] Health claims: the app makes none. Confirm the paywall and store listing make none either.

## Sign-off

- [ ] All items above are reviewed. Changes were made in code, and the rule version was bumped
      (`TrainingRules.VERSION`).
