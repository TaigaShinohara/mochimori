package com.taigashinohara.mochimori

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v0.3.25: the pre-fix explanation sheet's data. The user asked
 * that tapping a checklist row no longer jumps straight to a
 * settings screen; these tests pin the coverage the sheet relies
 * on — every one of the 13 items carries a "what saves battery"
 * and an honest tradeoff, and the on-screen steps exist exactly
 * for the items whose fix lives on a settings screen.
 */
class ChecklistGuideTest {

    private fun guide(item: SavingChecklist.Item) = ChecklistGuide.forItem(item)

    @Test fun everyItem_hasAGuide() {
        // forItem throws on a missing entry, so reaching the end of
        // this loop proves all 13 items are covered.
        assertEquals(13, SavingChecklist.Item.values().size)
        SavingChecklist.Item.values().forEach { guide(it) }
    }

    @Test fun everyItem_hasWhatAndTradeoff() {
        SavingChecklist.Item.values().forEach { item ->
            val g = guide(item)
            assertTrue("$item の what が空", g.what.isNotBlank())
            assertTrue("$item の tradeoff が空", g.tradeoff.isNotBlank())
            assertTrue("$item の actionLabel が空", g.actionLabel.isNotBlank())
        }
    }

    @Test fun settingsItems_haveSteps_nonSettingsHaveNone() {
        SavingChecklist.Item.values().forEach { item ->
            val g = guide(item)
            if (g.kind == ChecklistGuide.Kind.SETTINGS) {
                assertTrue("$item は設定項目なのに手順が無い", g.steps.isNotEmpty())
                g.steps.forEach { assertTrue("$item の手順に空がある", it.isNotBlank()) }
            } else {
                assertTrue("$item は設定項目でないのに手順がある", g.steps.isEmpty())
            }
        }
    }

    @Test fun kindClassification_matchesTheRealDestinations() {
        // IN_APP: values the app writes itself via WRITE_SETTINGS
        // (the same writes ProfileManager / fixBrightness perform).
        assertEquals(
            setOf(SavingChecklist.Item.BRIGHTNESS, SavingChecklist.Item.SCREEN_TIMEOUT),
            SavingChecklist.Item.values()
                .filter { guide(it).kind == ChecklistGuide.Kind.IN_APP }.toSet(),
        )
        // SETTINGS: the nine rows whose tap opens an Android
        // settings screen (DESIGN §12.3).
        assertEquals(
            setOf(
                SavingChecklist.Item.POWER_SAVER,
                SavingChecklist.Item.WIFI,
                SavingChecklist.Item.MOBILE_DATA,
                SavingChecklist.Item.LOCATION,
                SavingChecklist.Item.NFC,
                SavingChecklist.Item.BLUETOOTH,
                SavingChecklist.Item.DARK_THEME,
                SavingChecklist.Item.HAPTIC_FEEDBACK,
                SavingChecklist.Item.DATA_SAVER,
            ),
            SavingChecklist.Item.values()
                .filter { guide(it).kind == ChecklistGuide.Kind.SETTINGS }.toSet(),
        )
        // APP_SCREEN: the auto-saving rule lives on the rules screen.
        assertEquals(
            ChecklistGuide.Kind.APP_SCREEN,
            guide(SavingChecklist.Item.AUTO_SAVING).kind,
        )
        // INFO: temperature has no switch anywhere; advice only.
        assertEquals(
            ChecklistGuide.Kind.INFO,
            guide(SavingChecklist.Item.TEMPERATURE).kind,
        )
    }

    @Test fun actionLabels_matchTheKind() {
        SavingChecklist.Item.values().forEach { item ->
            val g = guide(item)
            when (g.kind) {
                ChecklistGuide.Kind.IN_APP ->
                    assertEquals("$item のボタン文言", "この内容で直す", g.actionLabel)
                ChecklistGuide.Kind.SETTINGS ->
                    assertEquals("$item のボタン文言", "設定を開く", g.actionLabel)
                else -> Unit
            }
        }
    }
}
