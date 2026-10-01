package app.forma.core.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/** Wall clock and time zone. Injected so tests can control time. */
interface AppClock {
    fun nowMillis(): Long
    fun zone(): ZoneId

    fun today(): LocalDate = Instant.ofEpochMilli(nowMillis()).atZone(zone()).toLocalDate()
}

object SystemClock : AppClock {
    override fun nowMillis(): Long = System.currentTimeMillis()
    override fun zone(): ZoneId = ZoneId.systemDefault()
}

/** A controllable clock for tests and the verification harness. */
class FakeClock(
    var millis: Long = Instant.parse("2026-09-28T08:00:00Z").toEpochMilli(),
    var zoneId: ZoneId = ZoneId.of("UTC"),
) : AppClock {
    override fun nowMillis(): Long = millis
    override fun zone(): ZoneId = zoneId
    fun advanceSeconds(seconds: Long) {
        millis += seconds * 1000
    }
    fun advanceDays(days: Long) {
        millis += days * 86_400_000L
    }
}

fun interface IdGenerator {
    fun newId(): String

    companion object {
        val Random = IdGenerator { UUID.randomUUID().toString() }
    }
}

/**
 * Minimal, privacy-preserving product events. Payloads never include workout details, notes,
 * names or health information. The first version ships with no analytics backend; see
 * docs/PRIVACY_DRAFT.md before adding one.
 */
enum class ProductEvent {
    ONBOARDING_COMPLETED,
    FIRST_WORKOUT_STARTED,
    FIRST_WORKOUT_COMPLETED,
    EXERCISE_REPLACED,
    PERMANENT_EXCLUSION_ADDED,
    SESSION_RESUMED,
    PAYWALL_VIEWED,
    PURCHASE_COMPLETED,
}

fun interface ProductEvents {
    fun track(event: ProductEvent)

    companion object {
        val None = ProductEvents { }
    }
}
