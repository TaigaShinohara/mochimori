package com.taigashinohara.mochimori

import android.content.Context
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import com.google.android.material.bottomnavigation.BottomNavigationView
import java.io.File
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Pre-delivery UI verification on the JVM (docs/UI_VERIFICATION.md).
 *
 * The build VM has no emulator/KVM, so screens are verified here instead:
 * Robolectric launches the real activity with the real layout pass, the
 * structural assertions catch the blank-screen failure shape (a content
 * area — above all the weight-driven face slot — measuring 0 high), and
 * Roborazzi goldens catch visual regressions.
 *
 * Determinism: the Robolectric sandbox clock is fixed and the test JVM
 * timezone is pinned to Asia/Tokyo (see app/build.gradle.kts). The face's
 * liquid wave runs on an infinite animator; after the fixed settle
 * advance its phase is scheduler-pinned, and the small remaining shimmer
 * is absorbed by the compare threshold (a blank screen is a ~100% diff,
 * far above it).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h915dp")
class MainActivityScreenTest {

    // NOTE: RoborazziRule は使わない。1.40 のルール方式は CaptureType
    // (LastImage/AllImage/Gif) の引数として Espresso の ViewInteraction を
    // 包んだ CaptureRoot の事前登録が必須で、素の Robolectric + 実 Activity
    // では成立しない(実測: "captureRoot is required for LastImage /
    // AllImage and Gif" で全件失敗)。代わりに core の
    // captureRoboImage(view, file, options) を直接使い、ゴールデンの
    // 置き場所と名前を明示する。record/verify の切替は system property
    // (roborazzi.test.record / verify) で、build.gradle.kts が
    // ROBORAZZI_RECORD 環境変数から設定する。
    private fun capture(view: View, name: String) {
        val file = File("src/test/snapshots/images/MainActivityScreenTest_$name.png")
        view.captureRoboImage(
            file,
            RoborazziOptions(
                compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.02f),
            ),
        )
    }

    private fun seedPrefs(onboardingDone: Boolean) {
        RuntimeEnvironment.getApplication()
            .getSharedPreferences("rules", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("onboarding_done", onboardingDone)
            .apply()
    }

    private fun launch(onboardingDone: Boolean): MainActivity {
        seedPrefs(onboardingDone)
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        settle(activity.window.decorView)
        return activity
    }

    /** Run posted layout/animation work so the capture is a settled frame. */
    private fun settle(root: View) {
        Robolectric.getForegroundThreadScheduler().advanceBy(3, TimeUnit.SECONDS)
        shadowOf(Looper.getMainLooper()).idle()
        fun pin(v: View) {
            v.alpha = 1f
            if (v is ViewGroup) for (i in 0 until v.childCount) pin(v.getChildAt(i))
        }
        pin(root)
    }

    private fun allViews(root: View, out: MutableList<View> = mutableListOf()): List<View> {
        out.add(root)
        if (root is ViewGroup) for (i in 0 until root.childCount) allViews(root.getChildAt(i), out)
        return out
    }

    private fun texts(root: View): List<String> =
        allViews(root).filterIsInstance<TextView>().mapNotNull { it.text?.toString() }

    /** The blank-screen guard: something must actually be laid out. */
    private fun assertNonBlank(activity: MainActivity, vararg expected: String) {
        val root = activity.window.decorView
        assertTrue("decor view has no height", root.height > 0)
        val content = allViews(root).filterIsInstance<android.widget.FrameLayout>()
            .firstOrNull { it.childCount > 0 }
        assertTrue("no populated content frame", content != null)
        assertTrue("content measured 0 high (blank screen shape)", content!!.height > 0)
        val found = texts(root)
        assertTrue("no text rendered at all", found.isNotEmpty())
        for (e in expected) {
            assertTrue("missing text '$e' (found: $found)", found.any { it.contains(e) })
        }
    }

    private fun clickButton(activity: MainActivity, text: String) {
        val root = activity.window.decorView
        val btn = allViews(root).filterIsInstance<Button>().firstOrNull { it.text.toString() == text }
        assertTrue("button '$text' not found", btn != null)
        btn!!.performClick()
        settle(root)
    }

    private fun selectTab(activity: MainActivity, itemId: Int) {
        val root = activity.window.decorView
        val nav = allViews(root).filterIsInstance<BottomNavigationView>().firstOrNull()
        assertTrue("bottom navigation not found", nav != null)
        nav!!.selectedItemId = itemId
        settle(root)
    }

    // ---------- Screenshot tests (Roborazzi goldens) ----------

    @Test
    fun onboardingEntry_rendersNonBlank() {
        val activity = launch(onboardingDone = false)
        assertNonBlank(activity, "何もしなくても、電池が持つ。", "自動でできること")
        val nav = allViews(activity.window.decorView)
            .filterIsInstance<BottomNavigationView>().firstOrNull()
        assertTrue("bottom nav must be hidden during onboarding", nav != null && nav.visibility == View.GONE)
        capture(activity.window.decorView, "onboardingEntry_rendersNonBlank")
    }

    @Test
    fun homeScreen_rendersNonBlank() {
        val activity = launch(onboardingDone = true)
        assertNonBlank(activity, "残量", "数値を見る")
        // The v0.3.19/20 failure shape was exactly this slot collapsing:
        // the weight-driven face view must come out with real size.
        val face = allViews(activity.window.decorView)
            .filterIsInstance<BatteryFaceView>().firstOrNull()
        assertTrue("face view missing on home", face != null)
        assertTrue("face view measured 0 high (blank face shape)", face!!.height > 0)
        assertTrue("face view measured 0 wide", face.width > 0)
        capture(activity.window.decorView, "homeScreen_rendersNonBlank")
    }

    @Test
    fun rulesScreen_rendersNonBlank() {
        val activity = launch(onboardingDone = true)
        selectTab(activity, R.id.nav_rules)
        assertNonBlank(activity, "残量で自動で変わる")
        capture(activity.window.decorView, "rulesScreen_rendersNonBlank")
    }

    @Test
    fun tipsScreen_rendersNonBlank() {
        val activity = launch(onboardingDone = true)
        selectTab(activity, R.id.nav_tips)
        assertNonBlank(activity, "充電のコツ")
        capture(activity.window.decorView, "tipsScreen_rendersNonBlank")
    }

    // ---------- Transition / logic smoke tests (Robolectric) ----------

    @Test
    fun tabSwitching_showsEachScreen() {
        val activity = launch(onboardingDone = true)
        assertNonBlank(activity, "残量")
        selectTab(activity, R.id.nav_rules)
        assertNonBlank(activity, "残量で自動で変わる")
        selectTab(activity, R.id.nav_tips)
        assertNonBlank(activity, "充電のコツ")
        selectTab(activity, R.id.nav_home)
        assertNonBlank(activity, "残量", "数値を見る")
    }

    @Test
    fun onboardingFlow_pagesAdvanceAndFinish() {
        val activity = launch(onboardingDone = false)
        assertNonBlank(activity, "何もしなくても、電池が持つ。")
        clickButton(activity, "次へ")
        assertNonBlank(activity, "正直な線引き")
        clickButton(activity, "次へ")
        assertNonBlank(activity, "許可のお願い")
        clickButton(activity, "あとで")
        // Finishing onboarding lands on home and persists the flag.
        assertNonBlank(activity, "残量", "数値を見る")
        val done = RuntimeEnvironment.getApplication()
            .getSharedPreferences("rules", Context.MODE_PRIVATE)
            .getBoolean("onboarding_done", false)
        assertEquals("onboarding_done must be persisted", true, done)
        val nav = allViews(activity.window.decorView)
            .filterIsInstance<BottomNavigationView>().firstOrNull()
        assertTrue("bottom nav must return after onboarding", nav != null && nav.visibility == View.VISIBLE)
    }
}
