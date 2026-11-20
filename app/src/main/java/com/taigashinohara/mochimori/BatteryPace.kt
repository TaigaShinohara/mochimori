package com.taigashinohara.mochimori

import kotlin.math.roundToLong

/**
 * Pure pace maths for the "あとどれくらいで0%か" estimate (v0.3.13).
 *
 * The estimate is built ONLY from measured history: the periodic
 * checker (and app opens) record (time, level, charging) samples, and
 * the drain rate of the current discharge session gives the remaining
 * minutes. When the history cannot support a number — too few samples,
 * too short a span, too small a drop — the result is null and the UI
 * says 「計測中」 instead of inventing a figure. This replaces the old
 * level × 18h rule of thumb, which was a guess wearing an estimate's
 * clothes (DESIGN §10).
 */
object BatteryPace {

    data class Sample(val timeMs: Long, val levelPercent: Int, val charging: Boolean)

    /** Samples kept: ~16 hours of 15-minute checks, hard-capped. */
    const val MAX_SAMPLES = 64

    /** Samples older than this are dropped from the store. */
    const val MAX_AGE_MS = 48L * 60 * 60 * 1000

    /** A pace needs at least this much observed time... */
    const val MIN_SPAN_MS = 30L * 60 * 1000

    /** ...and at least this much actual drain (level is whole %, so
     *  a 1% drop over a short span is mostly quantisation noise). */
    const val MIN_DROP_PERCENT = 2

    /** Only the last few hours of the session set the pace; older
     *  behaviour (e.g. overnight standby) must not dilute today's use. */
    const val WINDOW_MS = 6L * 60 * 60 * 1000

    /**
     * Estimated minutes until the battery reaches 0%, from the current
     * discharge session in [samples]. Null when the history is not
     * enough to say anything honest.
     */
    fun estimateMinutesToEmpty(samples: List<Sample>): Long? {
        val sorted = samples.sortedBy { it.timeMs }
        // The current discharge session starts after the most recent
        // charging sample; anything before it belongs to a previous
        // charge cycle and says nothing about the present drain.
        val lastCharging = sorted.indexOfLast { it.charging }
        val session = sorted.drop(lastCharging + 1).filter { !it.charging }
        if (session.size < 2) return null
        val newest = session.last()
        if (newest.levelPercent <= 0) return 0L
        val window = session.filter { newest.timeMs - it.timeMs <= WINDOW_MS }
        if (window.size < 2) return null
        val oldest = window.first()
        val spanMs = newest.timeMs - oldest.timeMs
        if (spanMs < MIN_SPAN_MS) return null
        val drop = oldest.levelPercent - newest.levelPercent
        if (drop < MIN_DROP_PERCENT) return null
        val percentPerHour = drop.toDouble() * 3_600_000.0 / spanMs
        return (newest.levelPercent / percentPerHour * 60.0).roundToLong()
    }

    /** Drop samples older than [MAX_AGE_MS] and cap the list length. */
    fun prune(samples: List<Sample>, nowMs: Long): List<Sample> =
        samples
            .filter { nowMs - it.timeMs <= MAX_AGE_MS }
            .sortedBy { it.timeMs }
            .takeLast(MAX_SAMPLES)

    /** Compact persistence form: "timeMs:level:charging01,..." */
    fun encode(samples: List<Sample>): String =
        samples.joinToString(",") {
            "${it.timeMs}:${it.levelPercent}:${if (it.charging) 1 else 0}"
        }

    /** Parse the persistence form; malformed entries are skipped. */
    fun decode(raw: String?): List<Sample> {
        if (raw.isNullOrEmpty()) return emptyList()
        return raw.split(",").mapNotNull { part ->
            val f = part.split(":")
            if (f.size != 3) return@mapNotNull null
            val t = f[0].toLongOrNull() ?: return@mapNotNull null
            val l = f[1].toIntOrNull() ?: return@mapNotNull null
            if (l !in 0..100) return@mapNotNull null
            Sample(t, l, f[2] == "1")
        }
    }

    /** 「約X時間Y分」 style duration for the estimate lines. */
    fun formatDuration(totalMinutes: Long): String {
        val m = totalMinutes.coerceAtLeast(0)
        val h = m / 60
        val rest = m % 60
        return when {
            h <= 0L -> "約${rest}分"
            rest == 0L -> "約${h}時間"
            else -> "約${h}時間${rest}分"
        }
    }
}
