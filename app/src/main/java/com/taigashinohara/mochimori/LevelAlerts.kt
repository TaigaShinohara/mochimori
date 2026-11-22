package com.taigashinohara.mochimori

/**
 * User-chosen low-battery level alerts (v0.3.22): the user picks
 * one or more remaining-level thresholds on the rules screen, and
 * the periodic checker notifies once when the discharging level
 * reaches each of them.
 *
 * Pure state machine, no Android dependencies: the caller feeds
 * the selected thresholds, the thresholds that have already fired
 * in this discharge run, the current level and whether the device
 * is charging, and persists [Decision.firedAfter] for the next
 * check. The rules:
 *
 * - Firing happens only while discharging. Charging never fires
 *   (the level is going the other way) and never marks a
 *   threshold as fired, so unplugging below a threshold still
 *   notifies at the next check.
 * - A threshold fires at most once until it re-arms: while the
 *   level stays at or below it, later checks stay silent.
 * - Re-arm: as soon as the level is back ABOVE a threshold (in
 *   practice: after charging past it), the threshold leaves the
 *   fired set and can fire again on the next discharge.
 * - Thresholds the user removed from the selection drop out of
 *   the fired set as well, so re-selecting one starts clean.
 *
 * Evaluation runs at the periodic check's cadence (~15 min), so a
 * notification can lag the actual crossing by that much; the
 * rules screen says so next to the setting.
 */
object LevelAlerts {

    data class Decision(
        /** Thresholds to notify now, highest first. */
        val toNotify: List<Int>,
        /** The fired set to persist for the next check. */
        val firedAfter: Set<Int>,
    )

    fun evaluate(
        thresholds: Set<Int>,
        firedBefore: Set<Int>,
        levelPercent: Int,
        charging: Boolean,
    ): Decision {
        // Not a real reading (the snapshot's unknown sentinel is
        // -1): never fire, and keep the fired set untouched rather
        // than re-arming on a value that is not a level.
        if (levelPercent !in 0..100) return Decision(emptyList(), firedBefore)
        // Only thresholds still selected can be (or stay) fired.
        val fired = firedBefore.intersect(thresholds).toMutableSet()
        // Re-arm every threshold the level has risen above again.
        fired.removeAll { levelPercent > it }
        if (charging) return Decision(emptyList(), fired)
        val due = thresholds
            .filter { levelPercent <= it && it !in fired }
            .sortedDescending()
        fired.addAll(due)
        return Decision(due, fired)
    }
}
