package com.taigashinohara.mochimori

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v0.3.26: the explanation sheet opens for EVERY row status, not
 * only NG. v0.3.25 gated the sheet on NG, and the user — whose
 * rows were mostly OK — reported 「説明が出ない」 because OK taps
 * still jumped straight to settings. These tests pin the plan
 * for all 4 statuses × 4 guide kinds: the current-state line,
 * what the primary button does, and the words on that button.
 */
class ChecklistSheetTest {

    private fun plan(status: SavingChecklist.Status, item: SavingChecklist.Item) =
        ChecklistSheet.plan(status, ChecklistGuide.forItem(item))

    // One representative item per kind.
    private val inApp = SavingChecklist.Item.BRIGHTNESS
    private val settings = SavingChecklist.Item.WIFI
    private val appScreen = SavingChecklist.Item.AUTO_SAVING
    private val info = SavingChecklist.Item.TEMPERATURE

    @Test fun statusLines_onlyForOkAndUnknown() {
        assertEquals("いまは節電できています", ChecklistSheet.statusLine(SavingChecklist.Status.OK))
        assertEquals("いまの状態は確認中です", ChecklistSheet.statusLine(SavingChecklist.Status.UNKNOWN))
        assertNull(ChecklistSheet.statusLine(SavingChecklist.Status.NG))
        assertNull(ChecklistSheet.statusLine(SavingChecklist.Status.INFO))
    }

    @Test fun ng_inApp_appliesTheFix() {
        val p = plan(SavingChecklist.Status.NG, inApp)
        assertEquals(ChecklistSheet.PrimaryAction.APPLY_FIX, p.primaryAction)
        assertEquals("この内容で直す", p.actionLabel)
        assertNull(p.statusLine)
    }

    @Test fun ng_settingsAndAppScreen_openTheirDestination() {
        val s = plan(SavingChecklist.Status.NG, settings)
        assertEquals(ChecklistSheet.PrimaryAction.OPEN_DESTINATION, s.primaryAction)
        assertEquals("設定を開く", s.actionLabel)
        val a = plan(SavingChecklist.Status.NG, appScreen)
        assertEquals(ChecklistSheet.PrimaryAction.OPEN_DESTINATION, a.primaryAction)
        assertEquals("ルール画面を開く", a.actionLabel)
    }

    @Test fun ok_neverReapplies_opensTheDestination() {
        // An already-saving in-app row must NOT offer the fix
        // again; it leads to the display settings that own the
        // value, with the settings label.
        val p = plan(SavingChecklist.Status.OK, inApp)
        assertEquals(ChecklistSheet.PrimaryAction.OPEN_DESTINATION, p.primaryAction)
        assertEquals("設定を開く", p.actionLabel)
        assertEquals("いまは節電できています", p.statusLine)
        val s = plan(SavingChecklist.Status.OK, settings)
        assertEquals(ChecklistSheet.PrimaryAction.OPEN_DESTINATION, s.primaryAction)
        assertEquals("設定を開く", s.actionLabel)
        val a = plan(SavingChecklist.Status.OK, appScreen)
        assertEquals(ChecklistSheet.PrimaryAction.OPEN_DESTINATION, a.primaryAction)
        assertEquals("ルール画面を開く", a.actionLabel)
    }

    @Test fun unknown_opensTheDestination_withTheCheckingLine() {
        SavingChecklist.Item.values().filter { it != info }.forEach { item ->
            val p = plan(SavingChecklist.Status.UNKNOWN, item)
            assertEquals("$item", ChecklistSheet.PrimaryAction.OPEN_DESTINATION, p.primaryAction)
            assertEquals("$item", "いまの状態は確認中です", p.statusLine)
        }
        // The auto-saving row's UNKNOWN is the missing permission:
        // its destination is the permission screen, and the label
        // must say so instead of naming the rules screen.
        assertEquals(
            "許可の画面を開く",
            plan(SavingChecklist.Status.UNKNOWN, appScreen).actionLabel,
        )
        // An unreadable in-app row leads to its settings screen.
        assertEquals(
            "設定を開く",
            plan(SavingChecklist.Status.UNKNOWN, inApp).actionLabel,
        )
    }

    @Test fun info_closesOnly_forEveryStatus() {
        SavingChecklist.Status.values().forEach { status ->
            val p = plan(status, info)
            assertEquals("$status", ChecklistSheet.PrimaryAction.CLOSE_ONLY, p.primaryAction)
            assertEquals("$status", "わかった", p.actionLabel)
        }
    }

    @Test fun everyItem_everyStatus_hasAPlanWithALabel() {
        SavingChecklist.Item.values().forEach { item ->
            SavingChecklist.Status.values().forEach { status ->
                val p = plan(status, item)
                assertTrue("$item/$status のボタン文言が空", p.actionLabel.isNotBlank())
            }
        }
    }
}
