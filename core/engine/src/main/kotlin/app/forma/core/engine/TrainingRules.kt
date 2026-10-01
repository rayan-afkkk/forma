package app.forma.core.engine

/**
 * Every threshold the training engine uses, in one place, with a version recorded on every
 * generated plan and change. Values are DRAFT pending review by a qualified fitness professional;
 * see docs/TRAINING_RULES.md.
 */
object TrainingRules {
    const val VERSION = "rules-2026.10-draft.1"

    // ----------------------------------------------------------------------- Progression
    /** A load increase is allowed if it is at most this many kg... */
    const val MAX_LOAD_JUMP_KG = 2.5

    /** ...or this many lb (whichever unit the equipment uses)... */
    const val MAX_LOAD_JUMP_LB = 5.0

    /** ...or at most this fraction of the current load. */
    const val MAX_LOAD_JUMP_FRACTION = 0.25

    /** Rep ranges are not extended beyond these caps; a harder variation is suggested instead. */
    const val REP_CAP_LOADED = 15
    const val REP_CAP_BODYWEIGHT = 20

    /** Rep range extension step when a heavier weight is not available. */
    const val REP_EXTENSION = 2

    /** Time-based holds progress by this many seconds, up to the cap. */
    const val TIME_STEP_SECONDS = 5
    const val TIME_CAP_SECONDS = 60
    const val TIME_FLOOR_SECONDS = 10

    /** A timed set counts as "below target" if it reached less than this fraction of the target. */
    const val TIME_BELOW_FRACTION = 0.7

    /** Lowest rep minimum the reduction rules may produce. */
    const val REP_FLOOR = 4

    // ----------------------------------------------------------------------- Returning after a break
    /** Gaps up to this many days since the last session need no check-in. */
    const val RETURN_NO_CHECK_IN_MAX_DAYS = 7

    /** Gaps from 8 days up to this many days are a short break; longer gaps are a long break. */
    const val RETURN_SHORT_BREAK_MAX_DAYS = 20

    const val RETURN_SHORT_BREAK_EASE_SESSIONS = 1
    const val RETURN_LONG_BREAK_EASE_SESSIONS = 2

    // ----------------------------------------------------------------------- Rotation
    /** A program session advances the rotation when at least this fraction of its main sets was done. */
    const val ROTATION_ADVANCE_MAIN_FRACTION = 0.5

    // ----------------------------------------------------------------------- Duration estimates
    const val SECONDS_PER_REP = 3.5
    const val TRANSITION_SECONDS = 25
    const val MIN_MAIN_REST_SECONDS = 45

    /** Shortened sessions may finish up to this fraction over the requested time. */
    const val DURATION_TOLERANCE = 0.1

    /** Shortening keeps at least this many main exercises; below that the request is impractical. */
    const val MIN_MAIN_ITEMS_WHEN_SHORTENED = 3

    /** Free adaptive sessions before the main upgrade offer. */
    const val FREE_ADAPTIVE_SAMPLE_SESSIONS = 3
}
