package com.taigashinohara.mochimori

/**
 * Pure decision for the diorama composition (v0.4.0, the design
 * the user approved in proposals/diorama-integration-design.md:
 * 「この設計で実装して」).
 *
 * Until v0.3.29 the face was drawn in code; from v0.4.0 the home
 * face is a COMPOSITION of generated miniature-diorama parts
 * (backdrop board / ground / body with a real window hole / face
 * parts / flames / cooling objects / decorations). Which parts
 * appear is decided here, from the same inputs the old drawing
 * used — the checklist heat decided by [HeatScene] (whose own
 * judgement is unchanged: heat = NG share of the judged normal
 * items, the fever floor, the liquid's eased colour curve) plus
 * the judge's charging / saving / unknown state:
 *
 *  - backdrop and ground follow the heat in four stages
 *    (cool sky + green hills → mild daytime + green → hot orange
 *    + dry grass → desert sun + cracked clay). With no heat
 *    verdict the neutral mild board and green ground stand in —
 *    the composition never guesses a hotter world than measured.
 *  - the mouth image follows HeatScene's mouth (smile / flat /
 *    wavy→zigzag). Charging always smiles; the unknown face
 *    keeps its flat line; with no heat verdict the v0.3.23
 *    mouths apply (smile while saving, the small open oval
 *    while not) — now as images (mouth-open).
 *  - the flame image follows HeatScene's flame level
 *    (0 none / 1 small / 2 medium / 3 large). HeatScene already
 *    suppresses flames while charging and keeps the fever
 *    floor, so the picture stays as honest as it was.
 *  - sweat (big + small) and steam appear only at the top
 *    stage (flame level 3) and never while charging.
 *  - the decorations (penguin / watermelon / sparkle /
 *    snowflake) are the reward for a fully cool battery:
 *    heat exactly 0 only (design §4 — the middle stages stay
 *    bare, so "cool = lively, hot = empty" reads at a glance).
 *
 * This object is free of Android classes and unit-tested
 * (DioramaSceneTest); the view and the widget renderer only
 * paint what it decides.
 */
object DioramaScene {

    enum class Backdrop { COOL, MILD, HOT, DESERT }
    enum class Ground { GREEN, DRY, DESERT }
    enum class Mouth { SMILE, FLAT, OPEN, ZIGZAG }
    enum class Flame { NONE, SMALL, MEDIUM, LARGE }

    data class Decision(
        val backdrop: Backdrop,
        val ground: Ground,
        val mouth: Mouth,
        val flame: Flame,
        /** Sweat drops (big + small) — top heat stage only. */
        val sweat: Boolean,
        /** Steam over the terminal cap — top heat stage only. */
        val steam: Boolean,
        /** The heat-0 reward decorations (design §4). */
        val decorations: Boolean,
    )

    /**
     * Decide the composition. [scene] is the checklist scene
     * from [HeatScene.of] (null when the caller has none);
     * [charging] / [saving] / [unknown] are the judge's state
     * for the same moment.
     */
    fun of(
        scene: HeatScene.Scene?,
        charging: Boolean,
        saving: Boolean,
        unknown: Boolean,
    ): Decision {
        val heat = scene?.heat
        val backdrop = when {
            heat == null -> Backdrop.MILD
            heat <= 0f -> Backdrop.COOL
            heat <= 0.34f -> Backdrop.MILD
            heat <= 0.67f -> Backdrop.HOT
            else -> Backdrop.DESERT
        }
        val ground = when {
            heat == null -> Ground.GREEN
            heat <= 0.34f -> Ground.GREEN
            heat <= 0.67f -> Ground.DRY
            else -> Ground.DESERT
        }
        val mouth = when {
            unknown -> Mouth.FLAT
            charging -> Mouth.SMILE
            heat == null -> if (saving) Mouth.SMILE else Mouth.OPEN
            else -> when (scene.mouth) {
                HeatScene.Mouth.SMILE -> Mouth.SMILE
                HeatScene.Mouth.WAVY -> Mouth.ZIGZAG
                else -> Mouth.FLAT
            }
        }
        val flame = when {
            charging -> Flame.NONE
            else -> when (scene?.flameLevel ?: 0) {
                0 -> Flame.NONE
                1 -> Flame.SMALL
                2 -> Flame.MEDIUM
                else -> Flame.LARGE
            }
        }
        // Sweat, steam and the decorations are expressions OF a
        // verdict: the unknown face (no verdict yet) never wears
        // them, whatever a stale scene might say.
        val topStage = !charging && !unknown && (scene?.flameLevel ?: 0) >= 3
        return Decision(
            backdrop = backdrop,
            ground = ground,
            mouth = mouth,
            flame = flame,
            sweat = topStage,
            steam = topStage,
            decorations = !unknown && heat != null && heat <= 0f,
        )
    }
}
