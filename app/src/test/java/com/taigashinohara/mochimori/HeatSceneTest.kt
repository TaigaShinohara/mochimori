package com.taigashinohara.mochimori

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * The pure burning → cooling decision (v0.3.27): heat from the
 * normal checklist tier, flame level, mouth, cooling objects,
 * particle activity, and the liquid's heat colour.
 */
class HeatSceneTest {

    private fun entry(
        item: SavingChecklist.Item,
        status: SavingChecklist.Status,
    ) = SavingChecklist.Entry(item, item.name, status, "")

    private fun normal(vararg pairs: Pair<SavingChecklist.Item, SavingChecklist.Status>) =
        pairs.map { (item, status) -> entry(item, status) }

    private val allItems = listOf(
        SavingChecklist.Item.BRIGHTNESS,
        SavingChecklist.Item.SCREEN_TIMEOUT,
        SavingChecklist.Item.TEMPERATURE,
        SavingChecklist.Item.POWER_SAVER,
        SavingChecklist.Item.AUTO_SAVING,
        SavingChecklist.Item.WIFI,
        SavingChecklist.Item.DARK_THEME,
    )

    @Test fun allOk_zeroHeat_noFlames_smile_everyObject() {
        val scene = HeatScene.of(
            allItems.map { entry(it, SavingChecklist.Status.OK) },
            charging = false,
        )
        assertEquals(0f, scene.heat!!, 0.0001f)
        assertEquals(0, scene.flameLevel)
        assertEquals(HeatScene.Mouth.SMILE, scene.mouth)
        assertEquals(HeatScene.CoolObject.values().toSet(), scene.objects)
        assertEquals(0f, scene.particleActivity, 0.0001f)
    }

    @Test fun allNg_fullHeat_largestFlames_wavyMouth_noObjects() {
        val scene = HeatScene.of(
            allItems.map { entry(it, SavingChecklist.Status.NG) },
            charging = false,
        )
        assertEquals(1f, scene.heat!!, 0.0001f)
        assertEquals(3, scene.flameLevel)
        assertEquals(HeatScene.Mouth.WAVY, scene.mouth)
        assertTrue(scene.objects.isEmpty())
        assertEquals(1f, scene.particleActivity, 0.0001f)
    }

    @Test fun unknownAndInfoRowsStayOutOfTheDenominator() {
        val scene = HeatScene.of(
            normal(
                SavingChecklist.Item.BRIGHTNESS to SavingChecklist.Status.NG,
                SavingChecklist.Item.TEMPERATURE to SavingChecklist.Status.OK,
                SavingChecklist.Item.POWER_SAVER to SavingChecklist.Status.OK,
                SavingChecklist.Item.SCREEN_TIMEOUT to SavingChecklist.Status.UNKNOWN,
                SavingChecklist.Item.AUTO_SAVING to SavingChecklist.Status.INFO,
                SavingChecklist.Item.WIFI to SavingChecklist.Status.UNKNOWN,
                SavingChecklist.Item.DARK_THEME to SavingChecklist.Status.UNKNOWN,
            ),
            charging = false,
        )
        // Judged: 3 (1 NG, 2 OK) — UNKNOWN and INFO never count.
        assertEquals(1f / 3f, scene.heat!!, 0.0001f)
        assertEquals(1, scene.flameLevel)
        assertEquals(HeatScene.Mouth.FLAT, scene.mouth)
        assertEquals(
            setOf(HeatScene.CoolObject.ICE, HeatScene.CoolObject.AIRCON),
            scene.objects,
        )
    }

    @Test fun nothingJudged_nullHeat_plainScene() {
        val scene = HeatScene.of(
            allItems.map { entry(it, SavingChecklist.Status.UNKNOWN) },
            charging = false,
        )
        assertNull(scene.heat)
        assertEquals(0, scene.flameLevel)
        assertEquals(HeatScene.Mouth.FLAT, scene.mouth)
        assertTrue(scene.objects.isEmpty())
        assertEquals(0f, scene.particleActivity, 0.0001f)
    }

    @Test fun emptyEntries_nullHeat() {
        val scene = HeatScene.of(emptyList(), charging = false)
        assertNull(scene.heat)
        assertEquals(0, scene.flameLevel)
    }

    @Test fun feverKeepsASmallFlameWhenEverythingElseIsFilled() {
        val entries = allItems.map {
            entry(
                it,
                if (it == SavingChecklist.Item.TEMPERATURE) {
                    SavingChecklist.Status.NG
                } else {
                    SavingChecklist.Status.OK
                },
            )
        }
        val scene = HeatScene.of(entries, charging = false)
        assertEquals(1f / 7f, scene.heat!!, 0.0001f)
        // A measurably hot battery is never drawn flame-free.
        assertTrue(scene.flameLevel >= 1)
        // The temperature row is NG, so no ice — the other six
        // objects are earned.
        assertEquals(6, scene.objects.size)
        assertTrue(HeatScene.CoolObject.ICE !in scene.objects)
    }

    @Test fun chargingSuppressesFlamesAndSmiles() {
        val scene = HeatScene.of(
            allItems.map { entry(it, SavingChecklist.Status.NG) },
            charging = true,
        )
        // The heat itself is still reported honestly…
        assertEquals(1f, scene.heat!!, 0.0001f)
        // …but the charging face stays calm: no flames, a smile.
        assertEquals(0, scene.flameLevel)
        assertEquals(HeatScene.Mouth.SMILE, scene.mouth)
    }

    @Test fun mouthTurnsWavyAtSixtyPercent() {
        val scene = HeatScene.of(
            normal(
                SavingChecklist.Item.BRIGHTNESS to SavingChecklist.Status.NG,
                SavingChecklist.Item.SCREEN_TIMEOUT to SavingChecklist.Status.NG,
                SavingChecklist.Item.TEMPERATURE to SavingChecklist.Status.NG,
                SavingChecklist.Item.POWER_SAVER to SavingChecklist.Status.OK,
                SavingChecklist.Item.AUTO_SAVING to SavingChecklist.Status.OK,
                SavingChecklist.Item.WIFI to SavingChecklist.Status.UNKNOWN,
                SavingChecklist.Item.DARK_THEME to SavingChecklist.Status.UNKNOWN,
            ),
            charging = false,
        )
        assertEquals(0.6f, scene.heat!!, 0.0001f)
        assertEquals(HeatScene.Mouth.WAVY, scene.mouth)
        assertEquals(2, scene.flameLevel)
    }

    @Test fun flameLevelBoundaries() {
        fun flameFor(ng: Int, ok: Int): Int {
            val entries = mutableListOf<SavingChecklist.Entry>()
            val items = allItems
            repeat(ng) { entries += entry(items[it], SavingChecklist.Status.NG) }
            repeat(ok) { entries += entry(items[ng + it], SavingChecklist.Status.OK) }
            repeat(7 - ng - ok) {
                entries += entry(items[ng + ok + it], SavingChecklist.Status.UNKNOWN)
            }
            return HeatScene.of(entries, charging = false).flameLevel
        }
        assertEquals(1, flameFor(ng = 1, ok = 2)) // heat 1/3 ≈ 0.33
        assertEquals(2, flameFor(ng = 2, ok = 3)) // heat 0.40
        assertEquals(2, flameFor(ng = 2, ok = 1)) // heat 2/3 ≈ 0.67
        assertEquals(3, flameFor(ng = 5, ok = 2)) // heat 5/7 ≈ 0.71
    }

    @Test fun limitTierItemsNeverEarnObjectsNorHeat() {
        val limitItems = listOf(
            SavingChecklist.Item.MOBILE_DATA,
            SavingChecklist.Item.LOCATION,
            SavingChecklist.Item.NFC,
            SavingChecklist.Item.BLUETOOTH,
            SavingChecklist.Item.HAPTIC_FEEDBACK,
            SavingChecklist.Item.DATA_SAVER,
        )
        val scene = HeatScene.of(
            limitItems.map {
                entry(it, if (it == SavingChecklist.Item.NFC) {
                    SavingChecklist.Status.NG
                } else {
                    SavingChecklist.Status.OK
                })
            },
            charging = false,
        )
        assertNull(scene.heat)
        assertEquals(0, scene.flameLevel)
        assertTrue(scene.objects.isEmpty())
    }

    @Test fun objectMappingIsTheFixedOne() {
        val expected = mapOf(
            SavingChecklist.Item.BRIGHTNESS to HeatScene.CoolObject.PARASOL,
            SavingChecklist.Item.SCREEN_TIMEOUT to HeatScene.CoolObject.WINDBELL,
            SavingChecklist.Item.TEMPERATURE to HeatScene.CoolObject.ICE,
            SavingChecklist.Item.POWER_SAVER to HeatScene.CoolObject.AIRCON,
            SavingChecklist.Item.AUTO_SAVING to HeatScene.CoolObject.FAN,
            SavingChecklist.Item.WIFI to HeatScene.CoolObject.SHAVED_ICE,
            SavingChecklist.Item.DARK_THEME to HeatScene.CoolObject.SNOWMAN,
        )
        for ((item, obj) in expected) {
            val scene = HeatScene.of(
                listOf(entry(item, SavingChecklist.Status.OK)),
                charging = false,
            )
            assertEquals("item $item", setOf(obj), scene.objects)
        }
        assertEquals(expected, HeatScene.OBJECT_FOR)
    }

    @Test fun liquidColorHitsTheAnchors() {
        // v0.3.29 eased curve: the anchors sit at heat 0 (cool),
        // 0.75 (peach) and 1 (hot).
        assertEquals(0xFF5EA9DD.toInt(), HeatScene.liquidColor(0f))
        assertEquals(0xFFF0A868.toInt(), HeatScene.liquidColor(0.75f))
        assertEquals(0xFFD42A1E.toInt(), HeatScene.liquidColor(1f))
        // Out-of-range heats clamp to the anchors.
        assertEquals(0xFF5EA9DD.toInt(), HeatScene.liquidColor(-0.5f))
        assertEquals(0xFFD42A1E.toInt(), HeatScene.liquidColor(1.5f))
    }

    @Test fun liquidColorStaysCoolWhileHeatIsLow() {
        // The v0.3.29 fix: at heat ≈ 0.17 (one NG out of six
        // judged — the case on the user's real-device screenshot)
        // the old linear mix already tinted the liquid peach and
        // it read grey. The eased curve holds the cool anchor for
        // the whole low-heat zone, up to heat 0.35.
        assertEquals(0xFF5EA9DD.toInt(), HeatScene.liquidColor(0.17f))
        assertEquals(0xFF5EA9DD.toInt(), HeatScene.liquidColor(0.35f))
    }

    @Test fun liquidColorMidpointsSitBetweenTheAnchors() {
        fun channels(argb: Int) = intArrayOf(
            (argb shr 16) and 0xFF, (argb shr 8) and 0xFF, argb and 0xFF,
        )
        // Heat 0.55 is the smoothstep midpoint of the cool →
        // peach zone (0.35…0.75): halfway between the anchors.
        val midCoolPeach = channels(HeatScene.liquidColor(0.55f))
        val coolMidMean = intArrayOf((0x5E + 0xF0) / 2, (0xA9 + 0xA8) / 2, (0xDD + 0x68) / 2)
        // Heat 0.875 is the smoothstep midpoint of the peach →
        // hot zone (0.75…1): halfway between mid and hot.
        val midPeachHot = channels(HeatScene.liquidColor(0.875f))
        val midHotMean = intArrayOf((0xF0 + 0xD4) / 2, (0xA8 + 0x2A) / 2, (0x68 + 0x1E) / 2)
        for (i in 0..2) {
            assertTrue(abs(midCoolPeach[i] - coolMidMean[i]) <= 2)
            assertTrue(abs(midPeachHot[i] - midHotMean[i]) <= 2)
        }
        // Half heat is past the hold zone but still clearly on
        // the blue side of the cool–peach midpoint.
        val half = channels(HeatScene.liquidColor(0.5f))
        assertTrue(half[0] < coolMidMean[0])
        assertTrue(half[2] > coolMidMean[2])
        // Alpha is always opaque.
        assertEquals(0xFF, HeatScene.liquidColor(0.25f) ushr 24)
    }

    @Test fun feverRaisesLiquidHeatToWarmPeachFloorEvenWhenOthersAreOk() {
        val entries = allItems.map {
            entry(
                it,
                if (it == SavingChecklist.Item.TEMPERATURE) {
                    SavingChecklist.Status.NG
                } else {
                    SavingChecklist.Status.OK
                },
            )
        }
        val scene = HeatScene.of(entries, charging = false)
        assertTrue(scene.fever)
        // Checklist heat remains the honest 1/7 NG share…
        assertEquals(1f / 7f, scene.heat!!, 0.0001f)
        // …while liquidHeat lifts to the warm peach floor so the water
        // never looks cool blue while the battery is measurably hot.
        assertEquals(HeatScene.FEVER_LIQUID_HEAT_FLOOR, scene.liquidHeat!!, 0.0001f)
        assertEquals(0xFFF0A868.toInt(), HeatScene.liquidColor(scene.liquidHeat!!))
    }

    @Test fun nonFeverAndNullHeatKeepLiquidHeatUnchanged() {
        val mild = HeatScene.of(
            normal(
                SavingChecklist.Item.BRIGHTNESS to SavingChecklist.Status.NG,
                SavingChecklist.Item.TEMPERATURE to SavingChecklist.Status.OK,
                SavingChecklist.Item.POWER_SAVER to SavingChecklist.Status.OK,
                SavingChecklist.Item.WIFI to SavingChecklist.Status.OK,
            ),
            charging = false,
        )
        assertEquals(false, mild.fever)
        assertEquals(0.25f, mild.liquidHeat!!, 0.0001f)
        assertEquals(0xFF5EA9DD.toInt(), HeatScene.liquidColor(mild.liquidHeat!!))

        val empty = HeatScene.of(emptyList(), charging = false)
        assertNull(empty.liquidHeat)
    }
}
