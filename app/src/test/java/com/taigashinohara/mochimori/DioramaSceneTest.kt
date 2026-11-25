package com.taigashinohara.mochimori

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The diorama state mapping (v0.4.0): heat + charging/saving →
 * which generated parts the face composes. The design's §2 table
 * (proposals/diorama-integration-design.md) is the contract:
 * four heat stages switch the backdrop board, the ground, the
 * mouth and the flame size together; sweat and steam exist only
 * at the top stage; the reward decorations only at heat exactly
 * 0; charging always wins the smile and kills the flames.
 */
class DioramaSceneTest {

    private fun scene(
        heat: Float?,
        flameLevel: Int = 0,
        mouth: HeatScene.Mouth = HeatScene.Mouth.FLAT,
    ): HeatScene.Scene = HeatScene.Scene(
        heat = heat,
        flameLevel = flameLevel,
        mouth = mouth,
        objects = emptySet(),
        particleActivity = heat ?: 0f,
    )

    // ---- The four stages (design §2) ----

    @Test
    fun heatZeroIsCoolGreenSmileAndDecorated() {
        val d = DioramaScene.of(scene(0f, mouth = HeatScene.Mouth.SMILE), charging = false, saving = true, unknown = false)
        assertEquals(DioramaScene.Backdrop.COOL, d.backdrop)
        assertEquals(DioramaScene.Ground.GREEN, d.ground)
        assertEquals(DioramaScene.Mouth.SMILE, d.mouth)
        assertEquals(DioramaScene.Flame.NONE, d.flame)
        assertFalse(d.sweat)
        assertFalse(d.steam)
        assertTrue(d.decorations)
    }

    @Test
    fun mildHeatKeepsTheCoolBackdrop() {
        val d = DioramaScene.of(scene(0.2f, flameLevel = 1), charging = false, saving = false, unknown = false)
        assertEquals(DioramaScene.Backdrop.MILD, d.backdrop)
        assertEquals(DioramaScene.Ground.GREEN, d.ground)
        assertEquals(DioramaScene.Flame.SMALL, d.flame)
        assertFalse(d.decorations)
    }

    @Test
    fun hotStageIsHotBackdropDryGroundMediumFlame() {
        val d = DioramaScene.of(scene(0.5f, flameLevel = 2), charging = false, saving = false, unknown = false)
        assertEquals(DioramaScene.Backdrop.HOT, d.backdrop)
        assertEquals(DioramaScene.Ground.DRY, d.ground)
        assertEquals(DioramaScene.Flame.MEDIUM, d.flame)
        assertFalse(d.sweat)
        assertFalse(d.steam)
    }

    @Test
    fun topStageIsDesertLargeFlameZigzagSweatAndSteam() {
        val d = DioramaScene.of(
            scene(1f, flameLevel = 3, mouth = HeatScene.Mouth.WAVY),
            charging = false, saving = false, unknown = false,
        )
        assertEquals(DioramaScene.Backdrop.DESERT, d.backdrop)
        assertEquals(DioramaScene.Ground.DESERT, d.ground)
        assertEquals(DioramaScene.Mouth.ZIGZAG, d.mouth)
        assertEquals(DioramaScene.Flame.LARGE, d.flame)
        assertTrue(d.sweat)
        assertTrue(d.steam)
        assertFalse(d.decorations)
    }

    // ---- Stage boundaries ----

    @Test
    fun backdropBoundariesFollowTheDesign() {
        assertEquals(DioramaScene.Backdrop.MILD, DioramaScene.of(scene(0.34f), false, false, false).backdrop)
        assertEquals(DioramaScene.Backdrop.HOT, DioramaScene.of(scene(0.67f), false, false, false).backdrop)
        assertEquals(DioramaScene.Backdrop.DESERT, DioramaScene.of(scene(0.68f), false, false, false).backdrop)
        assertEquals(DioramaScene.Ground.GREEN, DioramaScene.of(scene(0.34f), false, false, false).ground)
        assertEquals(DioramaScene.Ground.DRY, DioramaScene.of(scene(0.67f), false, false, false).ground)
        assertEquals(DioramaScene.Ground.DESERT, DioramaScene.of(scene(0.68f), false, false, false).ground)
    }

    // ---- Charging wins (design §2: lightning + smile, no flames) ----

    @Test
    fun chargingSmilesAndKillsFlamesEvenAtFullHeat() {
        val d = DioramaScene.of(
            scene(1f, flameLevel = 3, mouth = HeatScene.Mouth.WAVY),
            charging = true, saving = false, unknown = false,
        )
        assertEquals(DioramaScene.Mouth.SMILE, d.mouth)
        assertEquals(DioramaScene.Flame.NONE, d.flame)
        assertFalse(d.sweat)
        assertFalse(d.steam)
        // The backdrop still reports the heat honestly.
        assertEquals(DioramaScene.Backdrop.DESERT, d.backdrop)
        assertFalse(d.decorations)
    }

    // ---- No verdict yet / no heat axis ----

    @Test
    fun noSceneFallsBackToThePlainFaces() {
        assertEquals(
            DioramaScene.Mouth.SMILE,
            DioramaScene.of(null, charging = false, saving = true, unknown = false).mouth,
        )
        assertEquals(
            DioramaScene.Mouth.OPEN,
            DioramaScene.of(null, charging = false, saving = false, unknown = false).mouth,
        )
        assertEquals(
            DioramaScene.Mouth.FLAT,
            DioramaScene.of(null, charging = false, saving = false, unknown = true).mouth,
        )
        val d = DioramaScene.of(null, charging = false, saving = true, unknown = false)
        assertEquals(DioramaScene.Backdrop.MILD, d.backdrop)
        assertEquals(DioramaScene.Ground.GREEN, d.ground)
        assertEquals(DioramaScene.Flame.NONE, d.flame)
        assertFalse(d.decorations)
    }

    @Test
    fun sceneWithoutHeatKeepsTheFallbackMouths() {
        val s = scene(null)
        assertEquals(
            DioramaScene.Mouth.SMILE,
            DioramaScene.of(s, charging = false, saving = true, unknown = false).mouth,
        )
        assertEquals(
            DioramaScene.Mouth.OPEN,
            DioramaScene.of(s, charging = false, saving = false, unknown = false).mouth,
        )
        assertFalse(DioramaScene.of(s, charging = false, saving = true, unknown = false).decorations)
    }

    @Test
    fun unknownFaceIsFlatWhateverTheHeatSays() {
        val d = DioramaScene.of(
            scene(1f, flameLevel = 3, mouth = HeatScene.Mouth.WAVY),
            charging = false, saving = false, unknown = true,
        )
        assertEquals(DioramaScene.Mouth.FLAT, d.mouth)
        // No verdict yet: no heat expressions either.
        assertFalse(d.sweat)
        assertFalse(d.steam)
        assertFalse(d.decorations)
    }

    // ---- The HeatScene mouth mapping ----

    @Test
    fun heatMouthsMapToTheDioramaMouths() {
        assertEquals(
            DioramaScene.Mouth.SMILE,
            DioramaScene.of(scene(0.1f, mouth = HeatScene.Mouth.SMILE), false, false, false).mouth,
        )
        assertEquals(
            DioramaScene.Mouth.FLAT,
            DioramaScene.of(scene(0.4f, mouth = HeatScene.Mouth.FLAT), false, false, false).mouth,
        )
        assertEquals(
            DioramaScene.Mouth.ZIGZAG,
            DioramaScene.of(scene(0.9f, mouth = HeatScene.Mouth.WAVY), false, false, false).mouth,
        )
    }

    // ---- Two-base integrated stage (1A) & unknown honesty ----

    @Test
    fun stageBaseSwitchesAtHalfHeat() {
        assertEquals(
            DioramaScene.StageBase.GRASS,
            DioramaScene.of(scene(0f), charging = false, saving = true, unknown = false).stageBase,
        )
        assertEquals(
            DioramaScene.StageBase.GRASS,
            DioramaScene.of(scene(0.49f), charging = false, saving = false, unknown = false).stageBase,
        )
        assertEquals(
            DioramaScene.StageBase.DESERT,
            DioramaScene.of(scene(0.50f), charging = false, saving = false, unknown = false).stageBase,
        )
        assertEquals(
            DioramaScene.StageBase.DESERT,
            DioramaScene.of(scene(1f), charging = false, saving = false, unknown = false).stageBase,
        )
        assertEquals(
            DioramaScene.StageBase.GRASS,
            DioramaScene.of(null, charging = false, saving = true, unknown = false).stageBase,
        )
    }

    @Test
    fun unknownNeverShowsDesertBaseOrFlames() {
        val d = DioramaScene.of(
            scene(1f, flameLevel = 3, mouth = HeatScene.Mouth.WAVY),
            charging = false, saving = false, unknown = true,
        )
        assertEquals(DioramaScene.StageBase.GRASS, d.stageBase)
        assertEquals(DioramaScene.Flame.NONE, d.flame)
    }

    // ---- Integrated stage layout contracts (1A / 2A / 3A) ----

    @Test
    fun stageLayoutCoversAllCoolObjectsAndGroundedRewardsOnly() {
        // 2A: all 7 CoolObjects (including AIRCON) have fixed integrated-stage seats.
        assertEquals(HeatScene.CoolObject.values().toSet(), DioramaArt.COOL_PROP_SEATS.keys)
        // 3A: only grounded penguin & watermelon (no floating sparkle/snowflake).
        assertEquals(
            listOf(DioramaArt.Part.PENGUIN, DioramaArt.Part.WATERMELON),
            DioramaArt.DECOR_SEATS.map { it.first },
        )
        assertTrue(DioramaArt.DECOR_SEATS.all { it.second.layer == DioramaArt.StageLayer.FRONT })
        // Flames: back flames start at level 1, front foot flames appear only at level 3.
        assertTrue(DioramaArt.STAGE_FLAME_SEATS.any { it.seat.layer == DioramaArt.StageLayer.BACK && it.minLevel == 1 })
        assertTrue(DioramaArt.STAGE_FLAME_SEATS.any { it.seat.layer == DioramaArt.StageLayer.FRONT && it.minLevel == 3 })
        assertTrue(DioramaArt.STAGE_FLAME_SEATS.filter { it.seat.layer == DioramaArt.StageLayer.FRONT }.all { it.minLevel == 3 })
    }

    @Test
    fun computeStageBoundsReservesHeadroomAndSideMarginInsideSlot() {
        val spec = DioramaArt.STAGE_GRASS
        val b = DioramaArt.computeStageBounds(
            availLeft = 10f, availTop = 20f, availRight = 350f, availBottom = 320f,
            spec = spec, includePropHeadroom = true,
        )
        val topWithHeadroom = b.top - b.height * spec.headroomTopFrac
        val bottomWithShadow = b.bottom + b.height * spec.headroomBottomFrac
        val leftWithPad = b.left - b.width * spec.padSideFrac
        val rightWithPad = b.right + b.width * spec.padSideFrac
        assertTrue("top headroom clipped: $topWithHeadroom < 20", topWithHeadroom >= 19.9f)
        assertTrue("bottom shadow clipped: $bottomWithShadow > 320", bottomWithShadow <= 320.1f)
        assertTrue("left pad clipped: $leftWithPad < 10", leftWithPad >= 9.9f)
        assertTrue("right pad clipped: $rightWithPad > 350", rightWithPad <= 350.1f)
        assertEquals(spec.aspectRatio, b.width / b.height, 0.001f)
    }
}
