package com.taigashinohara.mochimori

import kotlin.math.roundToInt

/**
 * Pure decision for the face's "burning → cooling" expression
 * (v0.3.27, the user's proposal, approved from the generated
 * mock-up board v4: 「節電できてないときは燃えている。チェック
 * リストを埋めるほどバッテリーを冷ますオブジェクトが増えていき、
 * バッテリーが冷めていく」).
 *
 * The axis is the NORMAL checklist tier (7 items): the share of
 * judged items that are still NG is the face's "heat". Everything
 * the drawing code needs is decided here, so the view and the
 * widget renderer only paint:
 *  - heat: NG ÷ (OK + NG) over the normal tier. UNKNOWN rows are
 *    excluded from the denominator — the same honest counting as
 *    SavingChecklist.percent. Null when nothing could be judged:
 *    no verdict, no heat expression (the callers fall back to the
 *    plain v0.3.23 face).
 *  - flameLevel: 0 (none) … 3 (largest), from heat. A measured
 *    fever (the temperature row is NG) keeps at least a small
 *    flame even when everything else is filled in — the picture
 *    never claims the battery is cool while it is measurably hot.
 *  - mouth: the simple face's mouth follows the heat (wavy
 *    trembling line when hot, a flat line on the way, a calm
 *    smile arc when fully cool). Charging always smiles.
 *  - objects: one cooling object per OK item, from the fixed
 *    mapping below. The limit tier never earns objects.
 *  - particleActivity: 0…1, the liquid's liveliness (bubbles are
 *    faster and more numerous the hotter it is).
 *
 * Charging overrides the heat expression (flame 0, smile): the
 * charging face stays the calm one it has always been, with the
 * bolt as its marker.
 *
 * The liquid's colour is a character-expression colour, NOT a
 * theme state role: it is interpolated here between three anchors
 * recorded in docs/ART_DIRECTION.md (cool pale blue → pale peach
 * → deep red). The heat fed into that interpolation passes
 * through an easing curve first (v0.3.29, the user's call after
 * seeing a real-device screenshot: at low heat the linear mix
 * already tinted the liquid peach-grey): the colour holds the
 * pale blue until heat 0.35, crosses the pale peach at 0.75, and
 * lands on the deep red at 1.0. The interpolation is plain
 * integer arithmetic so this object stays free of Android
 * classes and unit-testable.
 */
object HeatScene {

    enum class Mouth { SMILE, FLAT, WAVY }

    /** The cooling objects that gather around the battery. */
    enum class CoolObject { PARASOL, WINDBELL, ICE, AIRCON, FAN, SHAVED_ICE, SNOWMAN }

    data class Scene(
        /** NG share of the judged normal items, 0…1; null when
         *  nothing could be judged. */
        val heat: Float?,
        /** 0 = no flames … 3 = the largest flames. */
        val flameLevel: Int,
        val mouth: Mouth,
        /** One object per OK normal item (fixed mapping). */
        val objects: Set<CoolObject>,
        /** 0…1: how lively the liquid's particles are. */
        val particleActivity: Float,
        /** True when the battery temperature row is measurably NG (>= 40℃). */
        val fever: Boolean = false,
    ) {
        /**
         * Effective heat fed into [liquidColor]: a measurably hot battery
         * ([fever]) never displays cool pale-blue liquid even when every
         * other checklist item is OK (honest warmth floor at [FEVER_LIQUID_HEAT_FLOOR]).
         */
        val liquidHeat: Float?
            get() = when {
                heat == null -> null
                fever -> maxOf(heat, FEVER_LIQUID_HEAT_FLOOR)
                else -> heat
            }
    }

    /** The fixed item → cooling-object mapping (v0.3.27 spec). */
    val OBJECT_FOR: Map<SavingChecklist.Item, CoolObject> = mapOf(
        SavingChecklist.Item.BRIGHTNESS to CoolObject.PARASOL,
        SavingChecklist.Item.SCREEN_TIMEOUT to CoolObject.WINDBELL,
        SavingChecklist.Item.TEMPERATURE to CoolObject.ICE,
        SavingChecklist.Item.POWER_SAVER to CoolObject.AIRCON,
        SavingChecklist.Item.AUTO_SAVING to CoolObject.FAN,
        SavingChecklist.Item.WIFI to CoolObject.SHAVED_ICE,
        SavingChecklist.Item.DARK_THEME to CoolObject.SNOWMAN,
    )

    // Liquid colour anchors (character-expression colours, the
    // values recorded in docs/ART_DIRECTION.md): pale blue when
    // fully cool, pale peach halfway, deep red when fully hot.
    private const val COOL_RGB = 0x5EA9DD
    private const val MID_RGB = 0xF0A868
    private const val HOT_RGB = 0xD42A1E

    /**
     * Build the scene from the NORMAL tier's entries (entries from
     * any other tier are ignored — they never earn objects and do
     * not feed the heat).
     */
    fun of(entries: List<SavingChecklist.Entry>, charging: Boolean): Scene {
        val normal = entries.filter { it.item in OBJECT_FOR }
        val judged = normal.filter {
            it.countsTowardScore &&
                (it.status == SavingChecklist.Status.OK || it.status == SavingChecklist.Status.NG)
        }
        val ng = judged.count { it.status == SavingChecklist.Status.NG }
        val heat: Float? = if (judged.isEmpty()) null else ng.toFloat() / judged.size
        val fever = normal.any {
            it.item == SavingChecklist.Item.TEMPERATURE &&
                it.status == SavingChecklist.Status.NG
        }
        var flame = when {
            heat == null -> 0
            heat <= 0f -> 0
            heat <= 0.34f -> 1
            heat <= 0.67f -> 2
            else -> 3
        }
        // Honest floor: a measurably hot battery keeps a small
        // flame even when the rest of the list is filled in.
        if (fever && flame < 1) flame = 1
        if (charging) flame = 0
        val mouth = when {
            charging -> Mouth.SMILE
            heat == null -> Mouth.FLAT
            heat <= 0f -> Mouth.SMILE
            heat >= 0.6f -> Mouth.WAVY
            else -> Mouth.FLAT
        }
        val objects = normal
            .filter { it.status == SavingChecklist.Status.OK }
            .mapNotNull { OBJECT_FOR[it.item] }
            .toSet()
        return Scene(
            heat = heat,
            flameLevel = flame,
            mouth = mouth,
            objects = objects,
            particleActivity = heat ?: 0f,
            fever = fever,
        )
    }

    // Liquid colour curve zones (v0.3.29, recorded in
    // docs/ART_DIRECTION.md): the colour holds the cool anchor
    // until HEAT_HOLD_END, reaches the peach anchor at
    // HEAT_PEACH, and the hot anchor at 1.0.
    private const val HEAT_HOLD_END = 0.35f
    private const val HEAT_PEACH = 0.75f

    /** Minimum effective heat for liquid colour when [Scene.fever] is true
     *  (lands on the warm peach anchor #F0A868 instead of cool blue). */
    const val FEVER_LIQUID_HEAT_FLOOR = HEAT_PEACH

    /**
     * The liquid's colour at a heat (0…1), as a packed ARGB int.
     * The heat is eased by [colorCurve] first, then run through
     * the two-segment interpolation cool → mid (0…0.5) → hot
     * (0.5…1) between the anchors above.
     */
    fun liquidColor(heat: Float): Int {
        val t = colorCurve(heat)
        return if (t <= 0.5f) {
            lerpRgb(COOL_RGB, MID_RGB, t * 2f)
        } else {
            lerpRgb(MID_RGB, HOT_RGB, (t - 0.5f) * 2f)
        }
    }

    /**
     * Ease a heat into the colour-interpolation parameter:
     *  - heat 0…[HEAT_HOLD_END]: 0 — the liquid stays the cool
     *    pale blue while the heat is low (at heat ≈ 0.17, one NG
     *    out of six judged, the old linear mix already read
     *    grey; the user approved the comparison board that
     *    keeps the blue here).
     *  - heat [HEAT_HOLD_END]…[HEAT_PEACH]: the cool → peach
     *    half of the interpolation, via smoothstep, reaching
     *    exactly the peach anchor at [HEAT_PEACH].
     *  - heat [HEAT_PEACH]…1: the peach → hot half, via
     *    smoothstep, landing exactly on the deep red at 1.0.
     */
    private fun colorCurve(heat: Float): Float {
        val h = heat.coerceIn(0f, 1f)
        return when {
            h <= HEAT_HOLD_END -> 0f
            h >= HEAT_PEACH ->
                0.5f + 0.5f * smoothstep((h - HEAT_PEACH) / (1f - HEAT_PEACH))
            else ->
                0.5f * smoothstep((h - HEAT_HOLD_END) / (HEAT_PEACH - HEAT_HOLD_END))
        }
    }

    private fun smoothstep(x: Float): Float {
        val t = x.coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    private fun lerpRgb(a: Int, b: Int, t: Float): Int {
        fun ch(shift: Int): Int {
            val ca = (a shr shift) and 0xFF
            val cb = (b shr shift) and 0xFF
            return (ca + (cb - ca) * t).roundToInt().coerceIn(0, 255)
        }
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }
}
