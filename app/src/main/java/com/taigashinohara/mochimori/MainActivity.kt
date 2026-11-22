package com.taigashinohara.mochimori

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.Uri
import android.net.wifi.SupplicantState
import android.net.wifi.WifiManager
import android.nfc.NfcAdapter
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.navigation.NavigationBarView
import kotlin.math.roundToLong

/**
 * M3 shell (v0.3.0): Top App Bar + content + Navigation Bar, all colours
 * resolved from theme roles — no colour literals in screen code.
 * State colours use the fixed custom roles (see ART_DIRECTION.md):
 *   cool/ charging -> colorMochiStateCool family
 *   not saving     -> colorMochiWarning family (custom warning role)
 *   emergency      -> colorError family
 *   structure      -> surface roles (uncoloured since v0.3.21)
 * All judgement and behaviour is unchanged from v0.2.0.
 * v0.3.1: design-consistency pass only (spacing rhythm tokens, type-token
 * compliance, onboarding heading hierarchy, state-colour discipline) — see
 * docs/DESIGN_REVIEW_v0.3.0.md.
 * v0.3.3: information-minimisation fixes (audit docs/INFO_MIN_REVIEW_v0.3.2.md,
 * M-1/M-2 + R-1..R-10 all applied): HEAT explanation moved to onSurface for
 * contrast, details toggle renamed to "数値を見る", the rough estimate moved
 * into the folded details, "やることなし" and other duplicated lines removed,
 * insights sample card retired, good section folded, rules notes deferred.
 * v0.3.4: home + face redesign from the user's real-device screenshot: the
 * face is wide (1.7:1) and large, the bolt means charging only, the home
 * cluster is distributed over the full viewport height with weighted
 * spacers (no more dead lower half), the reason line uses onSurface at
 * BodyLarge so it stays readable on the dark theme, and the "数値を見る"
 * toggle guarantees a 48dp touch target.
 * v0.3.5: face polish from the user's screenshot of the hot face (flame
 * band, neutral cavity, quieter glass band, stronger aura — all inside
 * BatteryFaceView) plus motion-synced haptic feedback at the user's
 * request: the "haptics" rule switch (default ON) feeds
 * BatteryFaceView.hapticsEnabled, and the manifest gains VIBRATE (a normal
 * permission, no dialog) for it.
 * v0.3.6: the face is upright again at the user's direct instruction,
 * with a liquid interior and tilt-sloshing (accelerometer, no permission
 * needed — all inside BatteryFaceView). The home face slot is taller
 * (236dp -> 320dp) to give the upright body hero size.
 * v0.3.7: design-consistency pass only (see docs/DESIGN_REVIEW_v0.3.6.md):
 * the onboarding cluster gets the same full-height distribution as home
 * (fillViewport + weighted spacers) instead of huddling at the top, and
 * the face slot height becomes the FACE_H token. No behaviour changes.
 * v0.3.8: Simple Glass adopted for the whole app at the user's decision
 * (audit docs/GLASS_REVIEW_v0.3.7.md): the root gets the shared
 * gradient-and-light foundation (AppBackgroundDrawable), cards become
 * reading-glass / role-tint glass with a specular edge (GlassDrawable,
 * radius moves to the Large token 20dp), the toolbar and navigation bar
 * become glass bands, and off-scale spacing leftovers (3/6/10dp) are
 * folded into the 4/8/12 scale. State colours and their roles are
 * unchanged — glass is texture only. A meaningful-design pass followed
 * (docs/DESIGN_REVIEW_v0.3.7.md): the onboarding dots now carry the
 * current position in the tertiary role colour as well as shape.
 * v0.3.10: the UI art is unified as Simple Glass at the user's decision
 * (audit docs/GLASS_REVIEW_v0.3.9.md) — the face becomes a glass vessel
 * (inside BatteryFaceView), the dark reading glass gains the skill's
 * paper veil, the light band's bottom edge thickens to 94%, text-button
 * labels move to the secondary role, and the filled buttons' horizontal
 * padding joins the spacing scale (28dp -> 24dp). All three text fixes
 * are contrast-gate driven; no behaviour changes.
 * v0.3.11: home layout plan A, chosen by the user from generated
 * mock-ups: a thin decorative-glass plate now sits behind the face so
 * face + plate read as one cluster, the cluster rides a little higher
 * (spacer weights 3:4 instead of 1:1), the state label and reason pull
 * in tighter under the face, and the "数値を見る" toggle becomes a
 * small glass pill chip (GlassDrawable.chip; same fold behaviour, same
 * secondary label — worst-case contrast ≈5.8:1 light / ≈5.2:1 dark).
 * The face itself, the judgement and every other screen are unchanged.
 * v0.3.13: the numbers fold gains the remaining-time estimate and the
 * battery-health lines (DESIGN §10), at the user's request. The drain
 * pace is measured from a persistent (time, level, charging) history
 * the periodic checker records (BatteryHistory / BatteryPace); until
 * the history supports a number the line says 計測中. Charging shows
 * the platform's computeChargeTimeRemaining() only when it returns a
 * value. Health shows only what the device reports — health state,
 * cycle count (API 34+, when filled), an estimated full-charge
 * capacity labelled 推定 — and never a degradation percentage (the
 * design capacity is not readable). The old level × 18h estimate is
 * retired: BatteryJudge no longer carries it.
 * v0.3.14: the insights tab is retired at the user's decision (they
 * chose "remove the tab, two tabs only" from presented options): the
 * Navigation Bar is now home / rules, and the insights screen — its
 * cards, its fix actions, its icon — is deleted. Nothing moves to
 * home: the face + reason + fix button there already carry "the
 * current problem and how to fix it". The history the remaining-time
 * estimate depends on (BatteryHistory / BatteryPace) is untouched,
 * as are the periodic check and the widget. Screen indices shift:
 * rules is now screen 1.
 * v0.3.15: a saving checklist joins the home screen, at the user's
 * request ("節電できてるかどうかはチェックリストで見れるように").
 * A reading-glass card under the numbers fold lists five measured
 * items — brightness, screen-off timeout, temperature, power saver,
 * this app's auto-saving — each judged by SavingChecklist against the
 * same thresholds the face uses, with a one-line "Nつ中Mつ できてる"
 * summary at its head. Values that cannot be read stay 「確認中」.
 * Tapping an item goes somewhere useful: display settings for
 * brightness / timeout, the battery-saver settings for the power
 * saver, the rules screen for auto-saving, the permission flow when
 * the auto-saving row is waiting on it, and a short cooling-how-to
 * dialog for temperature (no settings screen exists for heat).
 * v0.3.16: the checklist grows, at the user's request ("もっと節電
 * するためのチェックを増やそう。可能な限り節電したい"), and its head
 * becomes a donut chart with a big percent (DonutChartView) over the
 * same honest counting (UNKNOWN and advice rows stay out of the
 * denominator). New scored rows, each read with NO new permission:
 * Wi-Fi (radio state + supplicant state, which survives WifiInfo's
 * location redaction — ConnectivityManager is not used because its
 * reads require ACCESS_NETWORK_STATE), mobile data (Settings.Global,
 * row omitted when unreadable), location, NFC (row omitted without
 * hardware), dark theme (OLED premise named in the item), haptic
 * feedback. Bluetooth is advice-only: connected-or-not cannot be
 * read without BLUETOOTH_CONNECT, so an ON row informs and never
 * scores. Master auto-sync was NOT adopted: its getter is enforced
 * behind READ_SYNC_SETTINGS in the system server.
 * v0.3.17: the checklist splits into two tiers at the user's
 * request ("通常の節電と、限界までやる節電でチェックリストを分けて").
 * The normal card keeps the seven no-trade-off rows; the rows that
 * cost everyday convenience (mobile data, location, NFC, Bluetooth,
 * haptics, and the new data saver row) move to a second card,
 * 「限界まで節電」, which exists on the home screen ONLY while the
 * limit mode is ON — the toggle lives on the rules screen and at
 * the foot of the normal card, both writing the same "limit_mode"
 * preference. On the limit tier Bluetooth is judged outright (ON
 * is NG and scores): the tier's premise is sacrificing convenience,
 * so the advice-only treatment is retired with the split. The data
 * saver row reads ConnectivityManager.getRestrictBackgroundStatus()
 * — the one ConnectivityManager read that carries no permission
 * annotation in the platform — guarded so a failed read omits the
 * row. Each card keeps its own donut and percent.
 * v0.3.18: information reorganisation of the home and rules
 * screens, at the user's word ("どこに何があるか分かりづらい。整理
 * しよう"); the audit is docs/INFO_REORG_REVIEW_v0.3.17.md. No
 * function, judgement or item is removed — elements move and gain
 * group headings only. Home is now strictly "look and fix" in
 * importance order: face → state label and reason → the state's
 * fix → the checklist cards → the numbers fold LAST (it sat above
 * the checklist until now, breaking the look → fix flow, F-1).
 * Inside the fold, two headed groups separate "what is ahead and
 * the battery's health" from "the raw measured values" (F-2), so
 * the checklist (verdicts) and the numbers (detail values) no
 * longer blur into each other (F-4). The limit-mode switch lives
 * on the rules screen ONLY: home's card foot carries a guide row
 * naming where the switch lives, tapping through to rules (F-3).
 * Rules gains group headings — the stages keep their title and
 * their master switch joins them, then 通知 / 触覚 / チェック
 * リスト / 許可 — and a permission group showing the
 * WRITE_SETTINGS state with the existing request flow as its
 * entrance (F-5, F-6).
 * v0.3.19: the home screen fits without scrolling, at the user's
 * instruction ("ホーム画面はスクロール無しでコンテンツが見れる
 * ようにしたい"). The checklist compacted to fit: 36dp dense rows
 * (type sizes unchanged), a 52dp donut with a size-proportional
 * stroke, 8dp card padding; the guide row lives only in OFF mode,
 * where the limit card's absence is what it explains.
 * v0.3.20: the checklist shows only its head by default — donut,
 * percent, title and summary — and each card's rows open and close
 * independently under their own head tap, at the user's
 * instruction ("節電チェックはパーセンテージの部分だけ見せ、詳細な
 * チェックリスト部分はタッチした時に展開するようにしよう"). The
 * v0.3.19 rows-area swap in limit mode is replaced by this
 * per-card accordion. The fit mechanism is ALSO replaced: v0.3.19
 * measured the laid-out children's heights after layout and gave
 * the face the remainder (installHomeFit), but on the user's real
 * device the last row was still cut and the chip pushed off
 * screen — an EXACTLY-sized LinearLayout measures its later
 * children with AT_MOST specs bounded by the space the earlier
 * children (the face first, at its 320dp starting height) leave
 * behind, so the "fixed" sum reported what had already fit, not
 * what the content needed, and the face stayed at its maximum.
 * The face slot is now a plain layout WEIGHT: LinearLayout itself
 * hands it exactly the height the fixed content leaves, so the
 * default state (both cards closed, fold closed) fits by
 * construction at any viewport and font scale, with no
 * measurement to go wrong. The cards, the chip and the open fold
 * live in a lower area under the face: wrap-height while nothing
 * is expanded; in an expanded state (either card's rows open, or
 * the numbers fold open) the face is fixed at FACE_H_MIN and the
 * lower area takes the rest by weight, scrolling internally only
 * when the expanded content needs more room than is left — the
 * v0.3.19 fold exception, generalised. BatteryFaceView's geometry
 * still scales to the slot (its body rect is the shared
 * BatteryFaceView.bodyRectDp, which the glass plate mirrors from
 * the same function). The rules screen keeps its scroll, as does
 * onboarding.
 * v0.3.21: M3 standardisation pass, at the user's instruction
 * ("Material Design スキルで全体を見直して。Androidデフォルトであり
 * そうな画面レイアウトに。ノイズと無駄な装飾を消して"); the audit
 * is docs/M3_REVIEW_v0.3.20.md. The Simple Glass art direction
 * (v0.3.8–v0.3.20) is retired: the gradient-and-light foundation
 * (AppBackgroundDrawable) and every glass surface (GlassDrawable)
 * are deleted, the ground is the flat surface role, cards are
 * standard filled cards (surfaceContainer, Medium 12dp corners),
 * the toolbar and navigation bar use their component defaults,
 * and the decorative plate behind the face is gone. Headings lose
 * their tertiary tint (onSurface; colour is for state and actions
 * only), the numbers toggle is a standard Text Button instead of
 * the glass chip, the checklist's glyph marks and "⌄" chevron
 * become Material vector icons, checklist rows return to the 48dp
 * touch target (the accordion's internal scroll absorbs the
 * height, so the no-scroll default state is unaffected), and the
 * rules stages become rows in one card with state-colour dots.
 * The face keeps its liquid, flames, crystals, bolt and tilt —
 * only its decorative layer (aura, sparkles, embers, gloss, rim,
 * shading) is removed, inside BatteryFaceView. Dynamic Color stays
 * OFF (the v0.3.9 real-device decision): a deliberate, documented
 * departure from the M3 default. No function, judgement, item or
 * permission changes.
 * v0.3.23: design plan E ("ミニマル白"), chosen by the user from a
 * second board of generated mock-ups, with the face specified by
 * the user directly ("バッテリーをキャラにするのはいい。ただし顔
 * は目を点にし、目と口だけのシンプルな顔がいい"). The M3 base of
 * v0.3.21 stays; what changes is the art direction on top of it.
 * The battery is now a character with the simplest face — two dot
 * eyes and a mouth, drawn inside BatteryFaceView; the flames and
 * crystals that carried the state around the body are retired,
 * and the state rides on the liquid's colour and motion plus the
 * mouth's shape (smile / small open mouth / flat line). The home
 * screen follows plan E's order — big face, then the remaining
 * level as a quiet standing line, then the state and reason — on
 * a plain white ground (the light surface token is now #FFFFFF),
 * with the checklist cards quieted to the M3 outlined variant
 * (surface fill, 1dp outlineVariant hairline, whitespace doing
 * the grouping). Rules and tips keep their content, order and
 * wording and take the same quiet card and white ground, so all
 * three tabs share one tone; the dark theme keeps the identical
 * structure and hierarchy on its near-black ground. The widget's
 * still face is the same simple face. No function, judgement,
 * item or permission changes — appearance and layout only.
 * v0.3.22: two additions at the user's request. (1) Level alerts:
 * the rules 通知 group gains a threshold picker — preset Filter
 * Chips (50/40/30/20/10%) plus user-added values from a slider
 * dialog, shown as removable Input Chips — and the periodic
 * checker notifies once per chosen threshold while discharging
 * (LevelAlerts owns the fire-once / re-arm state machine; the
 * ~15-min check cadence means a notification can lag the crossing,
 * and the setting says so). The 80% and heat notifications are
 * unchanged, and all alerts share the one channel. (2) A third
 * tab, コツ: a reading-only tips screen about charging — phone-to-
 * phone power sharing (USB-C cable and wireless reverse charging,
 * model-dependent), faster charging, and gentler charging — built
 * from the same standard card/row parts as the rules stages. No
 * new permission; the home no-scroll default state is untouched.
 * v0.3.24: whole-app noise removal at the user's instruction
 * ("アプリ全体のノイズを消して"); the audit is
 * docs/NOISE_REVIEW_v0.3.23.md. Display-level cuts only — no
 * function, judgement, item or permission changes. The remaining
 * level now has exactly one home on the home screen (the standing
 * line under the face): it left the numbers fold and the charging
 * / emergency reason lines, the charging-full advice line that
 * restated the reason is gone, the onboarding app-name headline
 * (duplicated by the toolbar title) is gone, the tips intro line
 * (a restatement of its title and headings) is gone, the level
 * notification's body is sent only when it differs from the
 * title, and the remaining-time lines drop their doubled
 * （目安） — the 約 already carries it.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var content: FrameLayout
    private lateinit var bottomNav: NavigationBarView
    private lateinit var toolbar: MaterialToolbar
    private val prefs by lazy { getSharedPreferences("rules", MODE_PRIVATE) }

    private var snap: BatterySnapshot? = null
    private var currentScreen = 0
    private var detailsOpen = false
    /** Checklist rows expansion (v0.3.20): each card opens and
     *  closes independently under its own head tap. Both start
     *  closed; the states live only in this activity instance, so
     *  a fresh launch returns to the closed default. */
    private var normalChecklistOpen = false
    private var limitChecklistOpen = false
    private var onboardingPage = 0
    private var syncingNav = false

    // M3 text appearances (type scale comes from the theme, not per-screen sizes)
    private val taHeadline = com.google.android.material.R.style.TextAppearance_Material3_HeadlineMedium
    private val taHeadlineLarge = com.google.android.material.R.style.TextAppearance_Material3_HeadlineLarge
    private val taTitleLarge = com.google.android.material.R.style.TextAppearance_Material3_TitleLarge
    private val taTitleMedium = com.google.android.material.R.style.TextAppearance_Material3_TitleMedium
    private val taTitleSmall = com.google.android.material.R.style.TextAppearance_Material3_TitleSmall
    private val taBodyLarge = com.google.android.material.R.style.TextAppearance_Material3_BodyLarge
    private val taBodyMedium = com.google.android.material.R.style.TextAppearance_Material3_BodyMedium
    private val taBodySmall = com.google.android.material.R.style.TextAppearance_Material3_BodySmall
    private val taLabelLarge = com.google.android.material.R.style.TextAppearance_Material3_LabelLarge

    // Spacing rhythm tokens (v0.3.1 design pass): one page margin, one card
    // gap, one card padding, one primary-action gap for every screen, so all
    // screens breathe at the same rhythm. See docs/DESIGN_REVIEW_v0.3.0.md.
    private companion object {
        const val PAGE_PAD_H = 20
        const val CARD_GAP = 12
        const val CARD_PAD_H = 16
        const val CARD_PAD_V = 12
        const val ACTION_GAP = 24

        // Home face slot height (v0.3.7: tokenised at 320; v0.3.19:
        // variable; v0.3.20: weight-driven). In the default state
        // the slot is a layout weight and takes exactly the height
        // the fixed content leaves — no token bounds that size.
        // FACE_H_MAX is the drawing's design size (the v0.3.6
        // value): BatteryFaceView.bodyRectDp caps the body there
        // and centres it in a taller slot. FACE_H_MIN is the fixed slot height of
        // an expanded state (a checklist open, or the numbers fold
        // open), where the lower area needs the room instead. The
        // face's internal geometry — body proportions, the cap
        // headroom, the bottom zone — lives in
        // BatteryFaceView.bodyRectDp and scales with the slot it
        // is given.
        const val FACE_H_MAX = 320
        const val FACE_H_MIN = 136

        // Checklist rows (v0.3.19: 36dp dense rows so two always-open
        // cards could fit; v0.3.21: back to the M3 standard 48dp
        // touch target — since v0.3.20 the rows live behind the
        // accordion head and the expanded state scrolls internally,
        // so row height no longer decides whether the default state
        // fits). The card head's donut is 52dp; checklist cards use
        // the shared CARD_PAD_V like every other card.
        const val CHECK_ROW_H = 48
        const val CHECK_DONUT = 52

        // Level-alert presets (v0.3.22): the values offered as
        // Filter Chips on the rules screen. Any other value the
        // user adds stands beside them as an Input Chip; the
        // stored selection does not distinguish the two.
        val LEVEL_ALERT_PRESETS = listOf(50, 40, 30, 20, 10)
    }

    private fun themeColor(attr: Int): Int = MaterialColors.getColor(this, attr, Color.BLACK)
    private val colorOnSurface get() = themeColor(com.google.android.material.R.attr.colorOnSurface)
    private val colorOnSurfaceVariant get() = themeColor(com.google.android.material.R.attr.colorOnSurfaceVariant)
    private val colorPrimary get() = themeColor(com.google.android.material.R.attr.colorPrimary)
    private val colorSecondary get() = themeColor(com.google.android.material.R.attr.colorSecondary)
    private val colorCool get() = themeColor(R.attr.colorMochiStateCool)
    private val colorWarning get() = themeColor(R.attr.colorMochiWarning)
    private val colorError get() = themeColor(com.google.android.material.R.attr.colorError)
    private val colorOnError get() = themeColor(com.google.android.material.R.attr.colorOnError)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Edge-to-edge (M3): content draws behind the system bars; the
        // toolbar takes the status-bar inset, the Navigation Bar handles
        // the navigation-bar inset itself.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val night = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = !night
            isAppearanceLightNavigationBars = !night
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            // Flat surface ground (v0.3.21 M3 standardisation): the
            // gradient-and-light foundation of the Simple Glass era
            // (AppBackgroundDrawable, v0.3.8–v0.3.20) is retired —
            // the standard Android ground is the surface role as a
            // solid colour, and hierarchy comes from the container
            // tones of the components on it.
            setBackgroundColor(themeColor(com.google.android.material.R.attr.colorSurface))
        }
        toolbar = MaterialToolbar(this).apply {
            title = "もち守り"
            // No custom background (v0.3.21): the Top App Bar keeps
            // its component default container colour.
        }
        root.addView(toolbar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        ViewCompat.setOnApplyWindowInsetsListener(toolbar) { v, insets ->
            val top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            v.setPadding(v.paddingLeft, top, v.paddingRight, v.paddingBottom)
            insets
        }

        content = FrameLayout(this)
        root.addView(content, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        bottomNav = com.google.android.material.bottomnavigation.BottomNavigationView(this).apply {
            // No custom background or tints (v0.3.21): the Navigation
            // Bar keeps its component defaults — container colour,
            // active indicator and label colours all come from the
            // theme's roles, as in a stock Android app. (The v0.3.9
            // explicit tints existed to survive the translucent glass
            // band and its background light; both are gone.)
            inflateMenu(R.menu.bottom_nav)
            labelVisibilityMode = NavigationBarView.LABEL_VISIBILITY_LABELED
            setOnItemSelectedListener { item ->
                if (syncingNav) return@setOnItemSelectedListener true
                showScreen(
                    when (item.itemId) {
                        R.id.nav_rules -> 1
                        R.id.nav_tips -> 2
                        else -> 0
                    },
                )
                true
            }
        }
        root.addView(bottomNav, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        setContentView(root)

        Notifier.ensureChannel(this)
        BatteryCheckReceiver.schedule(this)

        if (!prefs.getBoolean("onboarding_done", false)) {
            bottomNav.visibility = View.GONE
            showOnboarding(0)
        } else {
            showScreen(0)
        }
    }

    override fun onResume() {
        super.onResume()
        if (prefs.getBoolean("onboarding_done", false)) {
            showScreen(currentScreen)
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun refresh() {
        snap = BatterySnapshot.read(this)
        // App opens are also sampling moments for the remaining-time
        // history (v0.3.13); BatteryHistory itself drops records that
        // add no information, so frequent refreshes stay cheap.
        snap?.let { BatteryHistory.record(this, it) }
    }

    private fun judge(): BatteryJudge.Result {
        refresh()
        val s = snap!!
        return BatteryJudge.judge(
            BatteryJudge.Input(
                levelPercent = s.levelPercent,
                tempC = if (s.tempKnown) s.tempC else 0f,
                brightness = s.brightness,
                autoSavingEnabled = prefs.getBoolean("auto_saving", true),
                saverThresholdPercent = 30,
                charging = s.charging,
                profileApplied = ProfileManager.isApplied(this),
                canWriteSettings = s.canWriteSettings,
                levelKnown = s.levelKnown,
            ),
        )
    }

    private fun setContent(view: View) {
        content.removeAllViews()
        content.addView(view)
    }

    private fun showScreen(index: Int) {
        currentScreen = index
        // Keep the Navigation Bar selection in sync without re-entering the listener.
        val targetId = when (index) {
            1 -> R.id.nav_rules
            2 -> R.id.nav_tips
            else -> R.id.nav_home
        }
        if (bottomNav.selectedItemId != targetId) {
            syncingNav = true
            bottomNav.selectedItemId = targetId
            syncingNav = false
        }
        setContent(
            when (index) {
                1 -> buildRules()
                2 -> buildTips()
                else -> buildHome()
            },
        )
    }

    // ---------- Shared M3 components ----------
    private fun label(
        text: String,
        appearance: Int,
        colorAttr: Int? = null,
        literalColor: Int? = null,
        center: Boolean = true,
    ): TextView = TextView(this).apply {
        this.text = text
        setTextAppearance(appearance)
        when {
            literalColor != null -> setTextColor(literalColor)
            colorAttr != null -> setTextColor(themeColor(colorAttr))
        }
        gravity = if (center) Gravity.CENTER else Gravity.START
        setPadding(0, dp(4), 0, dp(4)) // spacing scale (4/8/12/16/24/32) — was off-scale 3dp
    }

    /** M3 Filled Button with explicit role colours (state actions). */
    private fun filledButton(text: String, bgAttr: Int, fgAttr: Int, action: () -> Unit): MaterialButton {
        val b = MaterialButton(this).apply {
            this.text = text
            isAllCaps = false
            backgroundTintList = ColorStateList.valueOf(themeColor(bgAttr))
            setTextColor(themeColor(fgAttr))
            setPadding(dp(24), dp(10), dp(24), dp(10)) // spacing scale + M3 default (was off-scale 28dp)
            setOnClickListener { action() }
        }
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.topMargin = dp(ACTION_GAP)
        b.layoutParams = lp
        return b
    }

    /** M3 Filled Button in the theme's primary role (structural actions). */
    private fun primaryButton(text: String, action: () -> Unit): MaterialButton {
        val b = MaterialButton(this).apply {
            this.text = text
            isAllCaps = false
            setPadding(dp(24), dp(10), dp(24), dp(10)) // spacing scale + M3 default (was off-scale 28dp)
            setOnClickListener { action() }
        }
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.topMargin = dp(ACTION_GAP)
        b.layoutParams = lp
        return b
    }

    /**
     * M3 Text Button — the component's own text-button style, with
     * no hand-set tint or label colour (v0.3.21 standardisation:
     * until v0.3.20 this factory forced a transparent tint and a
     * secondary label, a workaround for text sitting on the glass
     * foundation's light; on the flat surface ground the theme
     * default — primary label on a transparent container — is the
     * standard and passes contrast in both schemes). The 48dp
     * minimum height keeps the M3 touch target.
     */
    private fun textButton(text: String, action: () -> Unit): MaterialButton {
        val b = MaterialButton(
            android.view.ContextThemeWrapper(
                this,
                com.google.android.material.R.style.ThemeOverlay_Material3_Button_TextButton,
            ),
            null,
            com.google.android.material.R.attr.materialButtonStyle,
        ).apply {
            this.text = text
            isAllCaps = false
            minimumHeight = dp(48) // M3 minimum touch target
            setOnClickListener { action() }
        }
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.topMargin = dp(8)
        b.layoutParams = lp
        return b
    }

    /**
     * Small group heading inside the home numbers fold (v0.3.18,
     * audit F-2): TitleSmall, centred like everything else in the
     * home column. The fold's lines stay BodySmall; the heading is
     * what tells the reader which of the fold's two kinds of
     * information a line belongs to. Its colour is onSurface since
     * v0.3.21 (headings carried the tertiary tint until the M3
     * standardisation; colour is now reserved for state and
     * actions).
     */
    private fun foldHeading(text: String): TextView {
        val tv = label(text, taTitleSmall, literalColor = colorOnSurface)
        val lp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
        )
        lp.topMargin = dp(12)
        tv.layoutParams = lp
        return tv
    }

    /**
     * Card used only where a contained group is meaningful. Since
     * v0.3.23 (design plan E, minimal white) the card is the quiet
     * M3 outlined variant: the fill is the surface itself — the
     * same white as the ground in the light theme, the same
     * near-black in the dark — and the group is held together by a
     * 1dp outlineVariant hairline and the padding inside, not by a
     * tonal fill. The Medium shape token (12dp) and no elevation
     * are unchanged. (v0.3.21–v0.3.22 used the filled variant with
     * the surfaceContainer role; until v0.3.20 this factory dressed
     * the card in Simple Glass.)
     */
    private fun card(): MaterialCardView = MaterialCardView(this).apply {
        setCardBackgroundColor(
            ColorStateList.valueOf(
                themeColor(com.google.android.material.R.attr.colorSurface),
            ),
        )
        strokeColor = themeColor(com.google.android.material.R.attr.colorOutlineVariant)
        strokeWidth = dp(1)
        radius = dp(12).toFloat() // ShapeAppearance Medium token (M3 card standard)
        cardElevation = 0f
        useCompatPadding = false
    }

    private fun cardColumn(card: MaterialCardView, padH: Int = CARD_PAD_H, padV: Int = CARD_PAD_V): LinearLayout {
        val inner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(padH), dp(padV), dp(padH), dp(padV))
        }
        card.addView(inner, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        return inner
    }

    private fun withTopMargin(view: View, marginDp: Int, matchWidth: Boolean = true) {
        val lp = LinearLayout.LayoutParams(
            if (matchWidth) ViewGroup.LayoutParams.MATCH_PARENT else ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
        lp.topMargin = dp(marginDp)
        view.layoutParams = lp
    }

    // ---------- Onboarding (R6; M3 button hierarchy) ----------
    private fun showOnboarding(page: Int) {
        onboardingPage = page
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(PAGE_PAD_H), dp(24), dp(PAGE_PAD_H), dp(24))
        }
        // Same full-height distribution as home (v0.3.7 design pass): a
        // single short cluster should spread over the viewport between
        // weighted spacers, not huddle at the top with a dead lower half.
        // When the content outgrows the viewport the spacers collapse and
        // it scrolls — the same behaviour as the home column.
        col.addView(View(this), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        when (page) {
            0 -> {
                // v0.3.24 (noise audit): no app-name headline here —
                // the toolbar title directly above already says
                // もち守り on this very screen.
                col.addView(label("何もしなくても、電池が持つ。", taBodyLarge, literalColor = colorOnSurface))
                col.addView(label("自動でできること", taTitleMedium, literalColor = colorOnSurface))
                col.addView(label("残量が減ったら、明るさと消灯時間を\n自動で下げます", taBodyLarge, literalColor = colorOnSurface))
                col.addView(label("充電80%と発熱は、通知で知らせます", taBodyLarge, literalColor = colorOnSurface))
                col.addView(progressDots(0))
                col.addView(primaryButton("次へ") { showOnboarding(1) })
            }
            1 -> {
                col.addView(label("正直な線引き", taTitleLarge, literalColor = colorOnSurface))
                col.addView(label("1タップでできること", taTitleMedium, literalColor = colorOnSurface))
                col.addView(label("省電力モードはボタンから\n設定画面を開く", taBodyLarge, literalColor = colorOnSurface))
                col.addView(label("できないこと", taTitleMedium, literalColor = colorOnSurface))
                col.addView(label("他のアプリを止めることはできません", taBodyLarge, literalColor = colorOnSurface))
                col.addView(label("Wi-Fiを勝手に切ることもできません", taBodyLarge, literalColor = colorOnSurface))
                col.addView(progressDots(1))
                col.addView(primaryButton("次へ") { showOnboarding(2) })
            }
            else -> {
                col.addView(label("許可のお願い", taTitleLarge, literalColor = colorOnSurface))
                col.addView(label("「設定の変更」許可があると、\n明るさと消灯時間を自動で調整できます", taBodyLarge, literalColor = colorOnSurface))
                col.addView(label("許可しなくても、見るだけなら使えます", taBodySmall, literalColor = colorOnSurfaceVariant))
                col.addView(progressDots(2))
                col.addView(primaryButton("許可する") {
                    finishOnboarding()
                    requestWriteSettings()
                })
                col.addView(textButton("あとで") { finishOnboarding() })
            }
        }
        col.addView(View(this), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContent(
            ScrollView(this).apply {
                isFillViewport = true
                addView(col, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            },
        )
    }

    /**
     * Progress as a figure, not words: 3 dots, current page filled
     * (R-10). The current position is carried by shape AND colour
     * (the primary role since v0.3.21; it was the tertiary role
     * until the M3 standardisation took colour off structure);
     * the spoken step count stays in the contentDescription.
     */
    private fun progressDots(page: Int): TextView {
        val text = (0..2).joinToString(" ") { if (it == page) "●" else "○" }
        val span = android.text.SpannableString(text)
        val start = page * 2 // each dot + one separating space
        span.setSpan(
            android.text.style.ForegroundColorSpan(colorPrimary),
            start, start + 1,
            android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
        return label("", taBodySmall, literalColor = colorOnSurfaceVariant).apply {
            this.text = span
            contentDescription = "ステップ ${page + 1} / 3"
        }
    }

    private fun finishOnboarding() {
        prefs.edit().putBoolean("onboarding_done", true).apply()
        requestNotificationPermission()
        bottomNav.visibility = View.VISIBLE
        // Auto-saving starts ON, so the ~15-min interval note is due now (R-8).
        if (prefs.getBoolean("auto_saving", true)) showIntervalNoteOnce()
        showScreen(0)
    }

    /**
     * The ~15-minute check interval is no longer a permanent line on the
     * rules screen (audit R-8): it is told once, at the moment it becomes
     * relevant — when auto-saving is (first) ON — and never repeated.
     */
    private fun showIntervalNoteOnce() {
        if (!prefs.getBoolean("note_interval_shown", false)) {
            prefs.edit().putBoolean("note_interval_shown", true).apply()
            Toast.makeText(this, "自動チェックは約15分ごとです。少し遅れることがあります", Toast.LENGTH_LONG).show()
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 42)
        }
    }

    private fun requestWriteSettings() {
        val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:$packageName"))
        startActivity(intent)
    }

    // ---------- Home (R3, R7, R8, R10) ----------
    private fun buildHome(): View {
        val result = judge()
        val s = snap!!
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(PAGE_PAD_H), dp(10), dp(PAGE_PAD_H), dp(10))
        }
        // No-scroll home (v0.3.19; mechanism replaced in v0.3.20).
        // The face slot is the flexible element, but its height is
        // now decided by a layout WEIGHT, not by measuring the
        // laid-out children afterwards: v0.3.19's installHomeFit
        // summed child heights from a pass in which this very
        // column — EXACTLY as tall as the viewport, with the face
        // first in line at its 320dp starting height — had already
        // measured the card and the chip with AT_MOST specs clipped
        // to the leftover space, so the sum understated the fixed
        // content and the face stayed at its maximum on the user's
        // real device (the last row cut, the chip off screen). A
        // weight cannot mis-measure: LinearLayout measures the
        // fixed children at their natural heights and hands the
        // face exactly what remains, so the default state fits by
        // construction. The slot's weight (20) sits between two
        // distribution spacers in the layout-plan-A ratio (3:4), so
        // the leftover becomes mostly face and the rest air around
        // the content block — the block never hugs the top with a
        // dead gap under the chip. In an expanded state the face
        // instead takes the fixed FACE_H_MIN and the lower area
        // (below) becomes the weighted, internally scrolling
        // element; the spacers exist only in the default state.
        val limitMode = prefs.getBoolean("limit_mode", false)
        val anyExpanded = detailsOpen || normalChecklistOpen || (limitMode && limitChecklistOpen)
        val spacerTop = View(this)
        val spacerBottom = View(this)
        val face = BatteryFaceView(this).apply {
            saving = result.saving
            charging = result.state == BatteryJudge.State.CHARGING
            unknownState = result.state == BatteryJudge.State.UNKNOWN
            levelPercent = if (s.levelKnown) s.levelPercent else 0
            // Motion-synced haptics (v0.3.5): on unless the user turned the
            // rule switch off. The face only exists on this screen, so the
            // pulses live and die with the home tab.
            hapticsEnabled = prefs.getBoolean("haptics", true)
        }
        // The face stands on the flat surface alone (v0.3.21): the
        // decorative glass plate that sat behind it (layout plan A,
        // v0.3.11) was pure decoration and is removed, with its
        // layout-tracking code — the slot mechanism below is
        // unchanged.
        if (anyExpanded) {
            // Expanded state: the face yields its room to the lower
            // area and sits at the design floor.
            col.addView(face, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(FACE_H_MIN)))
        } else {
            // Default state: the weighted trio (spacers 3:4 around
            // the face's 20) shares exactly the height the fixed
            // content leaves — the fit itself, with no measurement.
            col.addView(spacerTop, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 3f))
            col.addView(face, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 20f))
        }

        // The remaining level, as plan E lays it out (v0.3.23):
        // face → level → state and reason, each on its own quiet
        // line. The level is the same measured value the numbers
        // fold carries — plan E promotes it to a standing line under the
        // face, in the quiet onSurfaceVariant at HeadlineLarge, so
        // it reads as part of the face's block without shouting
        // over the state label below it. An unreadable level shows
        // a dash, never a guessed number.
        val percentLabel = label(
            if (s.levelKnown) "残量 ${s.levelPercent}%" else "残量 —",
            taHeadlineLarge, literalColor = colorOnSurfaceVariant,
        )
        percentLabel.setPadding(0, 0, 0, 0)
        col.addView(
            percentLabel,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                // Pulled 8dp up into the face slot's empty bottom
                // zone (the body's shadow zone in bodyRectDp;
                // nothing is drawn there), the move the state label
                // used until v0.3.22 (layout plan A, v0.3.11).
                topMargin = dp(-8)
            },
        )
        val (labelText, accent) = when (result.state) {
            BatteryJudge.State.SAVING -> "節電できてる" to colorCool
            BatteryJudge.State.CHARGING -> "充電中" to colorCool
            BatteryJudge.State.EMERGENCY -> "残量わずか" to colorError
            BatteryJudge.State.UNKNOWN -> "確認中" to colorOnSurfaceVariant
            BatteryJudge.State.NOT_SAVING -> "節電できてない" to colorWarning
        }
        // Label + reason stay tight under the level line so face,
        // level, state and reason read as one block — the
        // label→reason gap is 4dp, inside the spacing scale.
        val stateLabel = label(labelText, taHeadline, literalColor = accent)
        stateLabel.setPadding(0, dp(4), 0, 0)
        col.addView(stateLabel)
        // Reason: BodyLarge on onSurface. onSurfaceVariant at this size was
        // too dim to read on the dark theme (user screenshot, v0.3.4).
        // Its bottom padding is dropped (v0.3.19 density): the gap to
        // whatever follows is carried by that element's own top margin
        // (the action button's ACTION_GAP, the card's CARD_GAP).
        val reasonLabel = label(result.reason, taBodyLarge, literalColor = colorOnSurface)
        reasonLabel.setPadding(0, dp(4), 0, 0)
        col.addView(reasonLabel)
        // The rough estimate moved into the folded details (audit R-7): the
        // home surface is face + label + reason + action only.

        // Action area: exactly what the state needs, nothing more.
        when (result.fixKind) {
            BatteryJudge.FixKind.EMERGENCY_SAVER -> {
                col.addView(
                    filledButton(
                        "省電力モードをONに",
                        com.google.android.material.R.attr.colorError,
                        com.google.android.material.R.attr.colorOnError,
                    ) { openBatterySaverSettings() },
                )
            }
            BatteryJudge.FixKind.BRIGHTNESS -> {
                col.addView(filledButton("1タップで直す", R.attr.colorMochiWarning, R.attr.colorOnMochiWarning) { fixBrightness() })
            }
            BatteryJudge.FixKind.HEAT -> {
                // The app cannot fix heat; say so, and how to cool it (R5).
                // Body text uses onSurface, not the warning colour: warning
                // #E65100 on the light surface is only ~3.6:1, below the 4.5:1
                // body-text guide (audit M-1). Warning stays on headings/bars.
                col.addView(label("アプリでは直せません。ケースを外し、涼しい場所で休ませると冷めます", taBodyMedium, literalColor = colorOnSurface))
            }
            BatteryJudge.FixKind.AUTO_OFF -> {
                col.addView(
                    filledButton("自動節電をONにする", R.attr.colorMochiWarning, R.attr.colorOnMochiWarning) {
                        prefs.edit().putBoolean("auto_saving", true).apply()
                        Toast.makeText(this, "自動節電をONにしました", Toast.LENGTH_SHORT).show()
                        showScreen(0)
                    },
                )
            }
            BatteryJudge.FixKind.NEED_PERMISSION -> {
                col.addView(filledButton("許可して節電する", R.attr.colorMochiWarning, R.attr.colorOnMochiWarning) { requestWriteSettings() })
            }
            BatteryJudge.FixKind.APPLY_NOW -> {
                col.addView(
                    filledButton("今すぐ節電する", R.attr.colorMochiWarning, R.attr.colorOnMochiWarning) {
                        if (ProfileManager.apply(this)) {
                            Toast.makeText(this, "明るさを下げ、消灯を15秒にしました", Toast.LENGTH_SHORT).show()
                        }
                        showScreen(0)
                    },
                )
            }
            BatteryJudge.FixKind.CHARGING_FULL -> {
                // No extra line (v0.3.24 noise audit): the reason
                // already carries the unplug advice ("抜くと長持ち
                // します"); a second line here ("抜くのがおすすめ
                // です") only restated it.
            }
            else -> {
                // SAVING with nothing to do shows no extra line: face + state
                // label + reason already say it (audit R-4 removed the
                // duplicated "やることなし").
            }
        }

        // Saving checklist (v0.3.15, expanded v0.3.16, split into
        // two tiers in v0.3.17): directly under the state action, so
        // the emergency button above keeps its priority and the
        // "look → fix" flow runs face → verdicts → fixes without the
        // detail fold cutting in — the numbers fold moved below the
        // checklist in v0.3.18 (audit F-1). The normal card is the
        // shared card() (the quiet outlined variant since v0.3.23)
        // and always present; its foot carries the guide row to the
        // rules screen, where the limit-mode switch now lives alone
        // (v0.3.18, audit F-3). The limit card exists ONLY while
        // that mode is ON (the user's instruction: the limit list
        // appears only in limit mode — no greyed-out placeholder
        // when it is off). Rows are measured values judged by
        // SavingChecklist, and every row taps through to the place
        // that can change it (see onChecklistTap).
        // Lower area (v0.3.20): the checklist cards, the numbers
        // chip and the open fold live in their own column inside a
        // scroller under the face block. While nothing is expanded
        // the scroller wraps its content — its height is the
        // content's natural height, the face's weight absorbs every
        // leftover pixel, and there is nothing to scroll. In an
        // expanded state the face is fixed at FACE_H_MIN (above)
        // and this scroller takes the remaining height by weight,
        // scrolling its content internally only when the expanded
        // rows or fold lines need more room than is left.
        val lowerCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        val lowerScroller = ScrollView(this).apply {
            addView(
                lowerCol,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
        }
        // v0.3.20: each card is an accordion — the head (donut +
        // percent + title + summary) is all the default state
        // shows, and the rows live under the head's own tap, each
        // card opening and closing independently. This replaces
        // v0.3.19's limit-mode rows-area swap, which existed only
        // to fit two always-open lists; with the rows folded away
        // by default there is room for the face at its full size
        // again, and no card has to yield its rows to the other.
        // The guide row also lives only in OFF mode: while the
        // limit card is on screen, a row naming where its switch
        // lives would repeat what the screen already shows
        // (minimal-information rule: don't say the same thing
        // twice).
        val checklistInput = readChecklistInput(s)
        val checklist = checklistCard(
            SavingChecklist.evaluateNormal(checklistInput),
            title = "節電チェック",
            limitStyle = false,
            footer = if (limitMode) null else limitGuideRow(),
            expanded = normalChecklistOpen,
            onHeadTap = {
                normalChecklistOpen = !normalChecklistOpen
                showScreen(0)
            },
        )
        withTopMargin(checklist, CARD_GAP)
        lowerCol.addView(checklist)
        if (limitMode) {
            val limitCard = checklistCard(
                SavingChecklist.evaluateLimit(checklistInput),
                title = "限界まで節電",
                limitStyle = true,
                footer = null,
                expanded = limitChecklistOpen,
                onHeadTap = {
                    limitChecklistOpen = !limitChecklistOpen
                    showScreen(0)
                },
            )
            withTopMargin(limitCard, CARD_GAP)
            lowerCol.addView(limitCard)
        }

        // Numbers are folded away by default, and the fold comes
        // LAST on the home screen (v0.3.18, audit F-1); the toggle
        // names what is inside (audit M-2). Since v0.3.13 the fold
        // also carries the remaining-time
        // estimate and the battery-health lines (DESIGN §10). Everything
        // here is measured or labelled 目安/推定; anything the device does
        // not report is omitted, never padded out.
        //
        // Remaining time: discharging is estimated from the recorded
        // history's drain pace (BatteryPace) — until the history can
        // support a number it honestly says 計測中. This replaces the
        // old level × 18h rule of thumb (audit R-7's line), which was a
        // guess. Charging uses the platform's own
        // computeChargeTimeRemaining() when it returns a value, and
        // shows nothing when it does not.
        val remainingText: String? = when {
            result.state == BatteryJudge.State.UNKNOWN -> null
            s.charging -> if (s.chargeTimeRemainingMs > 0) {
                // v0.3.24: formatDuration already starts with 約 —
                // a trailing （目安） said the same thing twice.
                "満充電まで${BatteryPace.formatDuration((s.chargeTimeRemainingMs / 60000.0).roundToLong())}"
            } else {
                null
            }
            else -> {
                val minutes = BatteryPace.estimateMinutesToEmpty(BatteryHistory.load(this))
                if (minutes != null) {
                    "あと${BatteryPace.formatDuration(minutes)}で0%"
                } else {
                    "0%までの時間は計測中"
                }
            }
        }
        // v0.3.24 (noise audit): the level is NOT repeated here —
        // the standing line under the face is its one home. This
        // line carries the raw values that have no other home.
        val numbersText = listOfNotNull(
            "温度 ${if (s.tempKnown) "${"%.1f".format(s.tempC)}℃" else "—"}",
            "明るさ ${if (s.brightness >= 0) "${s.brightness}/255" else "—"}",
            if (s.voltageMv > 0) "電圧 ${"%.2f".format(s.voltageMv / 1000f)}V" else null,
        ).joinToString(" ・ ")
        // Health lines: only what this device actually reported.
        // No design capacity is readable by a general app, so no
        // degradation percentage is ever derived or shown.
        val healthLines = listOfNotNull(
            BatteryHealth.healthLabel(s.health)?.let { "電池の健康 $it" },
            if (s.cycleCount >= 0) "サイクル数 約${s.cycleCount}回" else null,
            BatteryHealth.estimatedFullCapacityMah(s.chargeCounterMicroAh, s.levelPercent)
                ?.let { "満充電容量 約${"%,d".format(it)}mAh（推定）" },
        )
        lowerCol.addView(textButton(if (detailsOpen) "数値を閉じる" else "数値を見る") {
            detailsOpen = !detailsOpen
            showScreen(0)
        })
        if (detailsOpen) {
            // Two headed groups (v0.3.18, audit F-2): the remaining
            // time and the battery's health in one group, the raw
            // measured values in the other, so a line is found by its
            // group instead of by reading every line. The items are
            // unchanged; a group whose lines are all absent shows no
            // heading either. This is also what separates the fold
            // from the checklist above (audit F-4): the checklist
            // carries verdicts (✓/！ marks), the fold carries detail
            // values and outlook, and the headings say so.
            // v0.3.20: the fold's lines join the lower area under
            // the chip. Opening the fold is an expanded state like
            // an open checklist: the face sits at FACE_H_MIN and
            // the lower scroller takes the remaining height (see
            // the end of this builder), scrolling internally when
            // the lines need more room than is left. The default
            // (closed) state never scrolls; that is what the user's
            // instruction governs.
            val timeAndHealth = listOfNotNull(remainingText).plus(healthLines)
            if (timeAndHealth.isNotEmpty()) {
                lowerCol.addView(foldHeading("残り時間と健康状態"))
                timeAndHealth.forEach { line ->
                    lowerCol.addView(label(line, taBodySmall, literalColor = colorOnSurfaceVariant))
                }
            }
            lowerCol.addView(foldHeading("いまの数値"))
            lowerCol.addView(label(numbersText, taBodySmall, literalColor = colorOnSurfaceVariant))
        }
        if (anyExpanded) {
            col.addView(
                lowerScroller,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f),
            )
        } else {
            col.addView(
                lowerScroller,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
            col.addView(spacerBottom, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 4f))
        }
        return col
    }

    private fun openBatterySaverSettings() {
        try {
            startActivity(Intent("android.settings.BATTERY_SAVER_SETTINGS"))
        } catch (e: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS))
            } catch (e2: Exception) {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            }
        }
    }

    /** Lower brightness only; used solely for the brightness reason (R5). */
    private fun fixBrightness() {
        if (Settings.System.canWrite(this)) {
            Settings.System.putInt(
                contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
            )
            Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, ProfileManager.SAVER_BRIGHTNESS)
            Toast.makeText(this, "明るさを30%に下げました", Toast.LENGTH_SHORT).show()
            showScreen(currentScreen)
        } else {
            Toast.makeText(this, "最初に1回だけ、設定変更の許可が必要です", Toast.LENGTH_LONG).show()
            requestWriteSettings()
        }
    }

    /**
     * Shorten the screen-off timeout only (v0.3.25): the checklist
     * sheet's in-app fix for the timeout row, the counterpart of
     * fixBrightness. The target is the checklist's own OK threshold
     * (30s), not the auto profile's 15s — a manual one-item fix
     * should land exactly on the value the row judges by. Without
     * the WRITE_SETTINGS permission this follows the same honest
     * path as fixBrightness: say so, then open the permission flow.
     */
    private fun fixScreenTimeout() {
        if (Settings.System.canWrite(this)) {
            Settings.System.putInt(
                contentResolver, Settings.System.SCREEN_OFF_TIMEOUT,
                SavingChecklist.TIMEOUT_OK_MS,
            )
            Toast.makeText(this, "消灯までの時間を30秒にしました", Toast.LENGTH_SHORT).show()
            showScreen(currentScreen)
        } else {
            Toast.makeText(this, "最初に1回だけ、設定変更の許可が必要です", Toast.LENGTH_LONG).show()
            requestWriteSettings()
        }
    }

    // ---------- Saving checklist (v0.3.15, expanded v0.3.16) ----------

    /**
     * Assemble the checklist's measured inputs. The battery facts come
     * from the snapshot the home screen already read; the values the
     * snapshot does not carry (brightness mode, screen-off timeout,
     * power-save mode, and the v0.3.16 radio/theme/haptic reads) are
     * read here, each with its own "could not read" sentinel so
     * SavingChecklist can stay honest (UNKNOWN -> 「確認中」, or the
     * row omitted where the spec says so) instead of guessing.
     * Reading settings needs no permission beyond what the app
     * already uses.
     */
    private fun readChecklistInput(s: BatterySnapshot): SavingChecklist.Input {
        val cr = contentResolver
        val brightnessAuto: Boolean? = try {
            Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE) ==
                Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
        } catch (e: Settings.SettingNotFoundException) {
            null
        }
        val timeoutMs = try {
            Settings.System.getInt(cr, Settings.System.SCREEN_OFF_TIMEOUT)
        } catch (e: Settings.SettingNotFoundException) {
            -1
        }
        val powerSave: Boolean? = try {
            getSystemService(PowerManager::class.java)?.isPowerSaveMode
        } catch (e: Exception) {
            null
        }
        // v0.3.16: the expanded checklist's reads. Each one is a
        // plain state read that needs no permission beyond the three
        // the app already carries; each failure lands on its own
        // "could not read" sentinel (see SavingChecklist).
        val wifiManager = try {
            getSystemService(WifiManager::class.java)
        } catch (e: Exception) {
            null
        }
        val wifiEnabled: Boolean? = try {
            wifiManager?.isWifiEnabled
        } catch (e: Exception) {
            null
        }
        // The supplicant state is not among the WifiInfo fields the
        // platform redacts for apps without the location permission
        // (SSID / BSSID / network id are), so COMPLETED is a real
        // "associated" signal. Transitional states settle on null —
        // the row then says 確認中 instead of guessing either way.
        @Suppress("DEPRECATION")
        val wifiConnected: Boolean? = if (wifiEnabled == true) {
            try {
                when (wifiManager?.connectionInfo?.supplicantState) {
                    SupplicantState.COMPLETED -> true
                    SupplicantState.DISCONNECTED, SupplicantState.INACTIVE,
                    SupplicantState.UNINITIALIZED, SupplicantState.INTERFACE_DISABLED,
                    SupplicantState.INVALID -> false
                    else -> null
                }
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }
        val locationEnabled: Boolean? = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                getSystemService(LocationManager::class.java)?.isLocationEnabled
            } else {
                @Suppress("DEPRECATION")
                Settings.Secure.getInt(cr, Settings.Secure.LOCATION_MODE) !=
                    Settings.Secure.LOCATION_MODE_OFF
            }
        } catch (e: Exception) {
            null
        }
        val nfcAdapter = try {
            NfcAdapter.getDefaultAdapter(this)
        } catch (e: Exception) {
            null
        }
        val nfcEnabled: Boolean? = if (nfcAdapter != null) {
            try {
                nfcAdapter.isEnabled
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }
        // The "mobile_data" global setting is a plain read (its
        // Settings.Global constant is not public API, so the key is
        // spelled out); when the key is absent the sentinel stays
        // down and the checklist omits the row rather than guessing.
        var mobileDataReadable = false
        var mobileDataEnabled: Boolean? = null
        try {
            when (Settings.Global.getInt(cr, "mobile_data", -1)) {
                1 -> {
                    mobileDataReadable = true
                    mobileDataEnabled = true
                }
                0 -> {
                    mobileDataReadable = true
                    mobileDataEnabled = false
                }
            }
        } catch (e: Exception) {
            // Unreadable: the row is omitted (see SavingChecklist).
        }
        val darkTheme =
            resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
                Configuration.UI_MODE_NIGHT_YES
        val hapticEnabled: Boolean? = try {
            when (Settings.System.getInt(cr, Settings.System.HAPTIC_FEEDBACK_ENABLED, -1)) {
                1 -> true
                0 -> false
                else -> null
            }
        } catch (e: Exception) {
            null
        }
        // isEnabled is one of the adapter reads that does not
        // require BLUETOOTH_CONNECT; whether anything is *connected*
        // would require it. (Since v0.3.17 Bluetooth is judged on
        // the limit tier, where that distinction is not needed.)
        val bluetoothOn: Boolean? = try {
            getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled
        } catch (e: Exception) {
            null
        }
        // Data saver (v0.3.17, limit tier): getRestrictBackground
        // Status() is the one ConnectivityManager read that carries
        // no permission annotation in the platform (the network-state
        // reads beside it do, and stay unused). ENABLED and
        // WHITELISTED both mean the device's data saver is in force
        // — WHITELISTED only exempts this app — so both read as
        // "restricted". Any failure leaves the sentinel down and the
        // checklist omits the row rather than guessing.
        var dataSaverReadable = false
        var dataSaverRestricted: Boolean? = null
        try {
            when (
                getSystemService(ConnectivityManager::class.java)
                    ?.getRestrictBackgroundStatus()
            ) {
                ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED,
                ConnectivityManager.RESTRICT_BACKGROUND_STATUS_WHITELISTED -> {
                    dataSaverReadable = true
                    dataSaverRestricted = true
                }
                ConnectivityManager.RESTRICT_BACKGROUND_STATUS_DISABLED -> {
                    dataSaverReadable = true
                    dataSaverRestricted = false
                }
            }
        } catch (e: Exception) {
            // Unreadable: the row is omitted (see SavingChecklist).
        }
        return SavingChecklist.Input(
            levelPercent = s.levelPercent,
            levelKnown = s.levelKnown,
            charging = s.charging,
            tempC = s.tempC,
            tempKnown = s.tempKnown,
            brightness = s.brightness,
            brightnessAuto = brightnessAuto,
            screenOffTimeoutMs = timeoutMs,
            powerSaveMode = powerSave,
            autoSavingEnabled = prefs.getBoolean("auto_saving", true),
            canWriteSettings = s.canWriteSettings,
            saverThresholdPercent = 30,
            wifiEnabled = wifiEnabled,
            wifiConnected = wifiConnected,
            locationEnabled = locationEnabled,
            nfcPresent = nfcAdapter != null,
            nfcEnabled = nfcEnabled,
            mobileDataReadable = mobileDataReadable,
            mobileDataEnabled = mobileDataEnabled,
            darkTheme = darkTheme,
            hapticEnabled = hapticEnabled,
            bluetoothOn = bluetoothOn,
            dataSaverReadable = dataSaverReadable,
            dataSaverRestricted = dataSaverRestricted,
        )
    }

    /**
     * A checklist card (one per tier since v0.3.17): a head with the
     * donut chart and its big percent beside the title and the
     * one-line summary, then one tappable row per item. The surface
     * is the shared card() — a filled card in v0.3.21–v0.3.22, the
     * quiet outlined variant since v0.3.23.
     * The percent is SavingChecklist.percent for THIS
     * card's list only: judged items only, UNKNOWN rows out of the
     * denominator, and a dash in the middle when nothing could be
     * judged at all. The limit card is told apart from the normal
     * one inside the existing roles only: its donut arc and title
     * take the warning role (the tier is the "push harder" one);
     * the surface, marks and row language are unchanged. [footer],
     * when given, is appended under the rows (the normal card
     * carries the guide row to the rules screen there — the
     * limit-mode switch itself moved to rules alone in v0.3.18).
     * v0.3.19 compacted the card (CHECK_* density tokens: 52dp
     * donut, 8dp card padding, padding-free head texts). v0.3.20
     * makes EVERY card an accordion (the user's instruction: show
     * only the percent part, expand the detailed list on tap):
     * the head is always built and is itself the open/close
     * switch — a chevron that rotates 180° while open, a ripple
     * over the whole head, and a spoken description carrying the
     * verdict, the state and the action — and the rows are built
     * only while [expanded]. The two cards open and close
     * independently; the v0.3.19 limit-mode swap is gone.
     * v0.3.21: the chevron is the standard expand_more vector icon
     * (it was a "⌄" text glyph), and the card padding is the
     * shared CARD_PAD_V again.
     */
    private fun checklistCard(
        entries: List<SavingChecklist.Entry>,
        title: String,
        limitStyle: Boolean,
        footer: View?,
        expanded: Boolean,
        onHeadTap: () -> Unit,
    ): MaterialCardView {
        val c = card()
        val box = cardColumn(c)
        val percent = SavingChecklist.percent(entries)
        val summary = SavingChecklist.summary(entries)

        val head = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val donutSize = dp(CHECK_DONUT)
        // No description of its own since v0.3.20: the head is the
        // card's toggle and carries the full spoken text (verdict,
        // state, action), so the donut does not repeat it.
        val donutWrap = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(donutSize, donutSize)
        }
        donutWrap.addView(
            DonutChartView(this).apply {
                this.percent = percent
                progressColor = if (limitStyle) colorWarning else colorCool
                trackColor = themeColor(com.google.android.material.R.attr.colorSurfaceVariant)
            },
            FrameLayout.LayoutParams(donutSize, donutSize),
        )
        donutWrap.addView(
            TextView(this).apply {
                text = percent?.let { "$it%" } ?: "―"
                // TitleMedium (bold) since v0.3.19: the donut is 52dp
                // now, and TitleLarge's "100%" would graze the ring.
                setTextAppearance(taTitleMedium)
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(colorOnSurface)
                gravity = Gravity.CENTER
            },
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
        head.addView(donutWrap)
        val headTexts = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), 0, 0, 0)
        }
        // The head texts carry no vertical padding of their own
        // (v0.3.19 density): the card's padding and the donut set the
        // head's rhythm, so the two lines sit at their type-scale
        // heights (24 + 22) under the 52dp donut.
        val titleLabel = label(
            title, taTitleMedium,
            literalColor = if (limitStyle) colorWarning else colorOnSurface,
            center = false,
        )
        titleLabel.setPadding(0, 0, 0, 0)
        headTexts.addView(titleLabel)
        val summaryLabel = label(
            summary, taBodyMedium,
            literalColor = colorOnSurfaceVariant, center = false,
        )
        summaryLabel.setPadding(0, dp(2), 0, 0)
        headTexts.addView(summaryLabel)
        head.addView(
            headTexts,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
        )
        // The head is the card's open/close switch (v0.3.20, every
        // card): the chevron rotates 180° while the rows are open,
        // the whole head takes the selectable ripple, and its spoken
        // description says the verdict, whether the rows are open,
        // and what a tap does; the accessibility state description
        // carries the open/closed state where the platform supports
        // it. Since v0.3.21 the chevron is the standard expand_more
        // vector icon (it was a "⌄" text glyph, whose shape depended
        // on the font).
        val chevron = android.widget.ImageView(this).apply {
            setImageResource(R.drawable.ic_expand_more)
            imageTintList = ColorStateList.valueOf(colorOnSurfaceVariant)
            rotation = if (expanded) 180f else 0f
        }
        head.addView(
            chevron,
            LinearLayout.LayoutParams(dp(24), dp(24)),
        )
        val tv = TypedValue()
        if (theme.resolveAttribute(android.R.attr.selectableItemBackground, tv, true)) {
            head.setBackgroundResource(tv.resourceId)
        }
        head.isClickable = true
        head.isFocusable = true
        head.contentDescription =
            (if (percent != null) "$title、${percent}パーセント、$summary" else "$title、$summary") +
                "。項目は" + (if (expanded) "開いています" else "閉じています") +
                "。タップで" + (if (expanded) "閉じる" else "開く")
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            head.stateDescription = if (expanded) "展開中" else "折りたたみ中"
        }
        head.setOnClickListener { onHeadTap() }
        box.addView(head)
        if (expanded) {
            entries.forEach { box.addView(checklistRow(it)) }
        }
        if (footer != null) box.addView(footer)
        return c
    }

    /**
     * Guide row at the foot of the normal checklist card (v0.3.18,
     * audit F-3): until v0.3.17 this foot carried a second
     * 「限界まで節電」 switch, duplicating the rules screen's — one
     * setting in two places, and a "decide" control on the "look
     * and fix" screen. The switch now lives on the rules screen
     * only. In its place the card's foot carries a quiet guide line
     * that names where the switch lives and taps through to it:
     * the reach is the same 1 tap the switch itself had, and the
     * mode's state is still visible right here — the limit card
     * below exists exactly while the mode is ON.
     */
    private fun limitGuideRow(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(CHECK_ROW_H)
            setPadding(dp(4), dp(4), dp(4), 0)
            val tv = TypedValue()
            if (theme.resolveAttribute(android.R.attr.selectableItemBackground, tv, true)) {
                setBackgroundResource(tv.resourceId)
            }
            isClickable = true
            isFocusable = true
            contentDescription = "限界まで節電はルールで切り替え"
            setOnClickListener { showScreen(1) }
        }
        val tv = label(
            "限界まで節電はルールで切り替え", taBodyMedium,
            literalColor = colorSecondary, center = false,
        )
        row.addView(tv, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(label("›", taTitleMedium, literalColor = colorOnSurfaceVariant, center = false))
        return row
    }

    /**
     * One checklist row: status mark + item name + measured value on
     * a single line, with the theme's selectable ripple. The row was
     * a 36dp dense row in v0.3.19–v0.3.20 (CHECK_ROW_H), a fit
     * compromise from when both cards stood open at once — the
     * arithmetic is in files/DESIGN.md §14. Since v0.3.21 the row is
     * the standard 48dp again: the v0.3.20 accordion means rows only
     * exist inside the internally-scrolling expanded state, so row
     * height no longer trades against the default state's fit. The
     * type sizes are unchanged (name BodyLarge, value BodyMedium).
     * The value stays onSurfaceVariant in every state — body text
     * never takes the warning colour (audit M-1); the mark carries
     * the status, by shape as well as colour.
     */
    private fun checklistRow(entry: SavingChecklist.Entry): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(CHECK_ROW_H)
            setPadding(dp(4), dp(2), dp(4), dp(2))
        }
        row.addView(checkMark(entry.status))
        val name = label(entry.name, taBodyLarge, literalColor = colorOnSurface, center = false).apply {
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(dp(12), 0, dp(8), 0)
        }
        row.addView(name, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(label(entry.valueText, taBodyMedium, literalColor = colorOnSurfaceVariant, center = false))
        val note = entry.note
        val root: View = if (note == null) {
            row
        } else {
            // Rows with a note carry a second, quieter line under
            // the value line, indented to the item name. (The old
            // Bluetooth advice row used this until v0.3.17 moved
            // Bluetooth to the judged limit tier; the mechanism
            // stays for any future noted row.)
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                addView(row)
                addView(
                    label(note, taBodySmall, literalColor = colorOnSurfaceVariant, center = false).apply {
                        setPadding(dp(44), 0, dp(4), dp(8))
                    },
                )
            }
        }
        return root.apply {
            val tv = TypedValue()
            if (theme.resolveAttribute(android.R.attr.selectableItemBackground, tv, true)) {
                setBackgroundResource(tv.resourceId)
            }
            isClickable = true
            isFocusable = true
            contentDescription =
                listOfNotNull(entry.name, entry.valueText, entry.note).joinToString(" ")
            setOnClickListener { onChecklistTap(entry) }
        }
    }

    /**
     * Status mark: a standard Material filled icon, distinguished by
     * shape AND colour (check_circle in the cool role, error in the
     * warning role, help and info in the quiet onSurfaceVariant) so
     * the meaning never rides on colour alone. Until v0.3.20 these
     * were text glyphs (✓ ！ ？ i) on hand-drawn container-coloured
     * circles; the v0.3.21 standardisation uses the stock icons, whose
     * shapes do not depend on the font. The verdict also lives in
     * words in the row's contentDescription.
     */
    private fun checkMark(status: SavingChecklist.Status): View {
        val (icon, tint) = when (status) {
            SavingChecklist.Status.OK -> R.drawable.ic_check_circle to colorCool
            SavingChecklist.Status.NG -> R.drawable.ic_error to colorWarning
            SavingChecklist.Status.UNKNOWN -> R.drawable.ic_help to colorOnSurfaceVariant
            SavingChecklist.Status.INFO -> R.drawable.ic_info to colorOnSurfaceVariant
        }
        return android.widget.ImageView(this).apply {
            setImageResource(icon)
            imageTintList = ColorStateList.valueOf(tint)
            layoutParams = LinearLayout.LayoutParams(dp(24), dp(24))
        }
    }

    /**
     * Where a row tap goes (v0.3.25): a row that is NOT saving no
     * longer jumps straight to its destination — the user asked
     * 「いきなり設定画面に飛ぶのではなく」 — it first opens the
     * explanation sheet (showChecklistGuide): what change saves
     * battery, what it costs, and, for settings items, the taps to
     * make on the screen that opens. Only the sheet's primary
     * button proceeds to the destination below (or applies the
     * in-app fix). Rows that are already saving, still being read,
     * or info-only keep the direct destinations they have always
     * had: the settings screen that owns the value, the rules
     * screen for this app's own toggle, the permission flow when
     * the auto-saving row is waiting on it, and the cooling dialog
     * for temperature.
     */
    private fun onChecklistTap(entry: SavingChecklist.Entry) {
        if (entry.status == SavingChecklist.Status.NG) {
            showChecklistGuide(entry)
        } else {
            openChecklistDestination(entry)
        }
    }

    /**
     * The pre-fix explanation sheet (v0.3.25). One quiet dialog in
     * the app's existing MaterialAlertDialogBuilder style and the
     * 案E minimal-white manner: the item name as title, then up to
     * three short sections — 「節電になること」 (ChecklistGuide.what),
     * 「かわりに、できなくなること」 (tradeoff) and, for settings
     * items only, 「設定画面での手順」 (the numbered steps). The
     * primary button is the guide's action label and does what the
     * row tap used to do directly; 「やめる」 closes and changes
     * nothing. INFO items (temperature) carry no destination, so
     * their only button is the closing one. The sheet exists only
     * at the moment of fixing — the rows themselves gain no
     * permanent text (the v0.3.24 noise audit's line).
     */
    private fun showChecklistGuide(entry: SavingChecklist.Entry) {
        val guide = ChecklistGuide.forItem(entry.item)
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(4), dp(24), 0)
        }
        body.addView(guideSection("節電になること", guide.what))
        body.addView(guideSection("かわりに、できなくなること", guide.tradeoff))
        if (guide.steps.isNotEmpty()) {
            val stepsText = guide.steps
                .mapIndexed { i, step -> "${i + 1}. $step" }
                .joinToString("\n")
            body.addView(guideSection("設定画面での手順", stepsText))
        }
        val builder = MaterialAlertDialogBuilder(this)
            .setTitle(entry.name)
            .setView(body)
        when (guide.kind) {
            ChecklistGuide.Kind.INFO ->
                builder.setPositiveButton(guide.actionLabel, null)
            ChecklistGuide.Kind.IN_APP -> {
                builder.setNegativeButton("やめる", null)
                builder.setPositiveButton(guide.actionLabel) { _, _ ->
                    applyChecklistFix(entry.item)
                }
            }
            ChecklistGuide.Kind.SETTINGS, ChecklistGuide.Kind.APP_SCREEN -> {
                builder.setNegativeButton("やめる", null)
                builder.setPositiveButton(guide.actionLabel) { _, _ ->
                    openChecklistDestination(entry)
                }
            }
        }
        builder.show()
    }

    /** One headed section of the guide sheet: a small quiet title
     *  over its body text, in the same type scale as the rest of
     *  the app (no new sizes, no decoration). */
    private fun guideSection(heading: String, text: String): View {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8), 0, dp(4))
        }
        col.addView(label(heading, taTitleSmall, literalColor = colorOnSurface, center = false))
        col.addView(label(text, taBodyMedium, literalColor = colorOnSurfaceVariant, center = false))
        return col
    }

    /** The in-app fixes behind the sheet's 「この内容で直す」:
     *  exactly the two values the app can write itself. */
    private fun applyChecklistFix(item: SavingChecklist.Item) {
        when (item) {
            SavingChecklist.Item.BRIGHTNESS -> fixBrightness()
            SavingChecklist.Item.SCREEN_TIMEOUT -> fixScreenTimeout()
            else -> Unit
        }
    }

    /**
     * The destinations a row tap has always led to: the settings
     * screen that owns the value, the rules screen for this app's
     * own toggle, the permission flow when the auto-saving row is
     * waiting on it, and a how-to dialog for temperature — heat has
     * no settings screen, so the honest destination is an
     * explanation (the same cooling advice the home HEAT line
     * gives, spelled out). The v0.3.16 rows each open the settings
     * screen that owns their switch (Wi-Fi, data usage, location,
     * NFC, Bluetooth, sound for haptics, display for the dark
     * theme). Since v0.3.25 an NG row reaches this only through
     * its explanation sheet's primary button.
     */
    private fun openChecklistDestination(entry: SavingChecklist.Entry) {
        when (entry.item) {
            SavingChecklist.Item.BRIGHTNESS, SavingChecklist.Item.SCREEN_TIMEOUT,
            SavingChecklist.Item.DARK_THEME ->
                openDisplaySettings()
            SavingChecklist.Item.TEMPERATURE -> showCoolingDialog()
            SavingChecklist.Item.POWER_SAVER -> openBatterySaverSettings()
            SavingChecklist.Item.AUTO_SAVING ->
                if (entry.status == SavingChecklist.Status.UNKNOWN) {
                    // The row's UNKNOWN exists only for the missing
                    // WRITE_SETTINGS permission (see SavingChecklist).
                    requestWriteSettings()
                } else {
                    showScreen(1)
                }
            // v0.3.16 rows: each goes to the settings screen that
            // owns the switch, with the wireless/sound screens as
            // fallbacks where a direct screen may not exist.
            SavingChecklist.Item.WIFI ->
                openSettingsAction(Settings.ACTION_WIFI_SETTINGS, Settings.ACTION_WIRELESS_SETTINGS)
            SavingChecklist.Item.MOBILE_DATA ->
                openSettingsAction(Settings.ACTION_DATA_USAGE_SETTINGS, Settings.ACTION_WIRELESS_SETTINGS)
            SavingChecklist.Item.LOCATION ->
                openSettingsAction(Settings.ACTION_LOCATION_SOURCE_SETTINGS, null)
            SavingChecklist.Item.NFC ->
                openSettingsAction(Settings.ACTION_NFC_SETTINGS, Settings.ACTION_WIRELESS_SETTINGS)
            SavingChecklist.Item.BLUETOOTH ->
                openSettingsAction(Settings.ACTION_BLUETOOTH_SETTINGS, Settings.ACTION_WIRELESS_SETTINGS)
            SavingChecklist.Item.HAPTIC_FEEDBACK ->
                openSettingsAction(Settings.ACTION_SOUND_SETTINGS, null)
            // v0.3.17: the data saver's switch lives on the data
            // usage screen (the same destination as mobile data).
            SavingChecklist.Item.DATA_SAVER ->
                openSettingsAction(Settings.ACTION_DATA_USAGE_SETTINGS, Settings.ACTION_WIRELESS_SETTINGS)
        }
    }

    private fun openDisplaySettings() {
        try {
            startActivity(Intent(Settings.ACTION_DISPLAY_SETTINGS))
        } catch (e: Exception) {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }

    /**
     * Open a specific settings screen, falling back to a broader
     * screen and finally to the settings top when a device does not
     * implement the direct one.
     */
    private fun openSettingsAction(action: String, fallback: String?) {
        try {
            startActivity(Intent(action))
        } catch (e: Exception) {
            try {
                if (fallback != null) startActivity(Intent(fallback))
                else throw e
            } catch (e2: Exception) {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            }
        }
    }

    private fun showCoolingDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("電池を冷ますには")
            .setMessage(
                "・重いアプリ（ゲーム・動画）を閉じる\n" +
                    "・充電しながらの使用を控える\n" +
                    "・ケースを外して、涼しい場所で休ませる",
            )
            .setPositiveButton("わかった", null)
            .show()
    }

    // ---------- Rules ----------
    private fun buildRules(): View {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(PAGE_PAD_H), dp(16), dp(PAGE_PAD_H), dp(16))
        }
        col.addView(label("残量で自動で変わる", taTitleLarge, literalColor = colorOnSurface, center = false))
        // The "~15 min" note is no longer a permanent line here (audit R-8):
        // it is told once when auto-saving is turned ON (see switchRow).
        // The three stages are ONE contained group (v0.3.21): a single
        // standard card holding three rows — a state-colour dot, the
        // stage title and its one-line detail — divided by hairlines.
        // Until v0.3.20 each stage was its own card filled with its
        // state container colour (the M3 anti-pattern of colouring
        // whole surfaces by severity, and three saturated blocks of
        // purple/orange/pink stacked on one screen); the state colour
        // now lives in the dot alone, and all text is onSurface /
        // onSurfaceVariant. See docs/M3_REVIEW_v0.3.20.md F-6.
        val stages = listOf(
            StageUi("通常 31%〜", "何もしない・見守るだけ", colorPrimary),
            StageUi("節電 30%以下", "定期チェックで 明るさ↓ ・ 消灯15秒（許可がある時）", colorWarning),
            StageUi("緊急 15%以下", "省電力モードの1タップをホームに大きく出す", colorError),
        )
        val stagesCard = card()
        val stagesBox = cardColumn(stagesCard)
        stages.forEachIndexed { i, st ->
            if (i > 0) {
                stagesBox.addView(
                    View(this).apply {
                        setBackgroundColor(
                            themeColor(com.google.android.material.R.attr.colorOutlineVariant),
                        )
                    },
                    LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)),
                )
            }
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(12), 0, dp(12))
            }
            row.addView(
                View(this).apply {
                    background = android.graphics.drawable.GradientDrawable().apply {
                        shape = android.graphics.drawable.GradientDrawable.OVAL
                        setColor(st.dotColor)
                    }
                },
                LinearLayout.LayoutParams(dp(12), dp(12)),
            )
            val texts = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(16), 0, 0, 0)
            }
            texts.addView(label(st.title, taTitleMedium, literalColor = colorOnSurface, center = false))
            texts.addView(label(st.detail, taBodySmall, literalColor = colorOnSurfaceVariant, center = false))
            row.addView(texts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            stagesBox.addView(row)
        }
        withTopMargin(stagesCard, CARD_GAP)
        col.addView(stagesCard)
        // The auto-saving switch sits with its stage cards (v0.3.18,
        // audit F-5): it is the stages' master switch, not a peer of
        // the notification switches further down.
        col.addView(switchRow("自動節電", "auto_saving", true))
        // The fixed "30%（固定）" row was removed (audit R-2): the stage card
        // "節電 30%以下" already says it. The trailing "設定はこの4つだけ"
        // note was removed with it (audit R-3).
        // Group: notifications (grouped with headings in v0.3.18,
        // audit F-5 — until now the five switches stood in one
        // undifferentiated column).
        col.addView(groupHeading("通知"))
        col.addView(switchRow("80%充電の通知", "notify_80", true))
        col.addView(switchRow("発熱の通知", "notify_heat", true))
        // Level alerts (v0.3.22, user request: notify at a remaining
        // level of the user's choosing). The selection itself is the
        // setting — no master switch; nothing selected, no alerts.
        // Firing and re-arming are LevelAlerts' job, driven by the
        // periodic checker (BatteryCheckReceiver).
        col.addView(levelAlertSection())
        // Group: haptics. Haptic feedback for the home face's motion
        // (v0.3.5, user request; default ON). Same switch part and
        // persistence as the others.
        col.addView(groupHeading("触覚"))
        col.addView(switchRow("触覚フィードバック", "haptics", true))
        // Group: checklist. Limit mode (v0.3.17, default OFF): while
        // ON, home gains the second checklist card 「限界まで節電」.
        // Since v0.3.18 this switch lives ONLY here (audit F-3):
        // the foot of home's checklist card carries a guide row
        // pointing here instead of a second switch. Nothing else on
        // this screen depends on the key, so no re-render is needed
        // here. The one-line note under the switch says what the
        // mode does where it happens (home) — without it, the
        // switch's effect is invisible from this screen.
        col.addView(groupHeading("チェックリスト"))
        col.addView(switchRow("限界まで節電", "limit_mode", false))
        col.addView(
            label(
                "ONにすると、ホームに「限界まで節電」チェックリストが出ます",
                taBodySmall, literalColor = colorOnSurfaceVariant, center = false,
            ),
        )
        // Group: permission (v0.3.18, audit F-6). The WRITE_SETTINGS
        // permission gates auto-saving, so its state and entrance
        // belong on this "decide" screen too — until now they lived
        // only in onboarding and on home's NEED_PERMISSION button.
        col.addView(groupHeading("許可"))
        col.addView(permissionRow())
        return ScrollView(this).apply { addView(col) }
    }

    /**
     * Group heading on the rules screen (v0.3.18, audit F-5):
     * TitleMedium in onSurface (the tertiary tint it carried until
     * v0.3.20 was retired by the M3 standardisation — headings are
     * structure, and structure is not coloured), with one
     * ACTION_GAP of air above it, so the switch column reads as
     * named groups instead of one undifferentiated list.
     */
    private fun groupHeading(text: String): View {
        val tv = label(text, taTitleMedium, literalColor = colorOnSurface, center = false)
        val lp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
        )
        lp.topMargin = dp(ACTION_GAP)
        tv.layoutParams = lp
        return tv
    }

    /**
     * The WRITE_SETTINGS permission's state and entrance, in the
     * rules screen's 許可 group (v0.3.18, audit F-6): a switch-row
     * shape — name on the left, state on the right. Granted shows
     * a quiet state word; not granted shows the text-button part
     * wired to the same request flow onboarding and home use.
     * This is a second entrance to an existing flow, not a new
     * capability: the permission itself, and what it unlocks, are
     * unchanged.
     */
    private fun permissionRow(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(12), 0, dp(12)) // same rhythm as switchRow
        }
        val tv = label("設定の変更", taBodyLarge, literalColor = colorOnSurface, center = false)
        row.addView(tv, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        if (Settings.System.canWrite(this)) {
            row.addView(
                label("許可済み", taBodyMedium, literalColor = colorOnSurfaceVariant, center = false),
            )
        } else {
            val b = textButton("許可する") { requestWriteSettings() }
            b.layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            row.addView(b)
        }
        return row
    }

    private data class StageUi(
        val title: String,
        val detail: String,
        val dotColor: Int,
    )

    private fun switchRow(text: String, key: String, default: Boolean): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(12), 0, dp(12)) // spacing scale — was off-scale 10dp
        }
        val tv = label(text, taBodyLarge, literalColor = colorOnSurface, center = false)
        row.addView(tv, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val sw = MaterialSwitch(this).apply {
            contentDescription = text // label for screen readers (R9)
            isChecked = prefs.getBoolean(key, default)
            setOnCheckedChangeListener { _, v ->
                prefs.edit().putBoolean(key, v).apply()
                if (key == "auto_saving" && !v) ProfileManager.restore(this@MainActivity)
                if (key == "auto_saving" && v) showIntervalNoteOnce()
                if (key == "auto_saving") showScreen(1)
            }
        }
        row.addView(sw)
        return row
    }

    // ---------- Level alerts (v0.3.22) ----------

    /** The user's chosen level-alert thresholds, as stored by the checker. */
    private fun levelAlertValues(): Set<Int> =
        prefs.getStringSet(BatteryCheckReceiver.PREF_LEVEL_ALERT_VALUES, emptySet())
            .orEmpty().mapNotNull { it.toIntOrNull() }.toSet()

    private fun setLevelAlertValue(value: Int, selected: Boolean) {
        val next = levelAlertValues().toMutableSet()
        if (selected) next.add(value) else next.remove(value)
        prefs.edit()
            .putStringSet(
                BatteryCheckReceiver.PREF_LEVEL_ALERT_VALUES,
                next.map { it.toString() }.toSet(),
            )
            .apply()
        showScreen(1)
    }

    /**
     * The level-alert picker in the rules 通知 group (v0.3.22):
     * a label, one ChipGroup, and the honest lag note. Preset
     * values are M3 Filter Chips (multi-select); values the user
     * added themselves stand beside them as Input Chips whose
     * close icon removes them; the last chip opens the slider
     * dialog that adds a new value. Every change persists at once
     * and re-renders this screen, the same pattern the switches
     * use. The lag note sits right under the setting, as with the
     * auto profile's interval note: judgement runs at the ~15-min
     * check, so the notification can arrive late — said here,
     * briefly, where the choice is made.
     */
    private fun levelAlertSection(): View {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(12), 0, 0) // switchRow rhythm
        }
        col.addView(
            label("残量の通知", taBodyLarge, literalColor = colorOnSurface, center = false),
        )
        val selected = levelAlertValues()
        val group = ChipGroup(this).apply {
            isSingleSelection = false
        }
        LEVEL_ALERT_PRESETS.forEach { v ->
            group.addView(
                Chip(this).apply {
                    text = "${v}%"
                    isCheckable = true
                    isChecked = v in selected
                    setOnCheckedChangeListener { _, checked -> setLevelAlertValue(v, checked) }
                },
            )
        }
        selected.filter { it !in LEVEL_ALERT_PRESETS }.sortedDescending().forEach { v ->
            group.addView(
                Chip(this).apply {
                    text = "${v}%"
                    isCloseIconVisible = true
                    contentDescription = "残量 ${v}% の通知"
                    setOnCloseIconClickListener { setLevelAlertValue(v, false) }
                },
            )
        }
        group.addView(
            Chip(this).apply {
                text = "＋ 自分で決める"
                setOnClickListener { showCustomLevelDialog() }
            },
        )
        val groupLp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
        )
        groupLp.topMargin = dp(8)
        col.addView(group, groupLp)
        val note = label(
            "チェックは約15分ごとのため、通知は残量に届いてから少し遅れることがあります",
            taBodySmall, literalColor = colorOnSurfaceVariant, center = false,
        )
        val noteLp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
        )
        noteLp.topMargin = dp(8)
        col.addView(note, noteLp)
        return col
    }

    /**
     * Add-a-value dialog for the level alerts (v0.3.22): an M3
     * Slider (5–95%, in 5% steps) with the value it points at.
     * Picking a preset value here simply selects that preset —
     * the stored set does not care how a value arrived.
     */
    private fun showCustomLevelDialog() {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(8), dp(24), 0)
        }
        val valueLabel = label("25%", taTitleLarge, literalColor = colorOnSurface)
        container.addView(valueLabel)
        val slider = com.google.android.material.slider.Slider(this).apply {
            valueFrom = 5f
            valueTo = 95f
            stepSize = 5f
            value = 25f
            addOnChangeListener { _, v, _ -> valueLabel.text = "${v.toInt()}%" }
        }
        container.addView(slider)
        MaterialAlertDialogBuilder(this)
            .setTitle("通知する残量")
            .setView(container)
            .setPositiveButton("追加") { _, _ ->
                setLevelAlertValue(slider.value.toInt(), true)
            }
            .setNegativeButton("やめる", null)
            .show()
    }

    // ---------- Tips (v0.3.22) ----------

    private data class TipUi(val title: String, val body: String)

    /**
     * The tips screen (third tab, コツ; v0.3.22, user request):
     * reading material about charging, built from the same parts
     * as the rules stages — a page title, group headings, and one
     * standard card per group holding title/body rows divided by
     * hairlines. Nothing here operates the device: no settings
     * links, no switches. The phone-to-phone procedures are spelled
     * out in the body text itself.
     *
     * Content discipline: only well-established facts, phrased at
     * the strength the platform documentation supports. Anything
     * model-dependent (wireless reverse charging above all) says
     * so in its own text instead of promising it works everywhere.
     */
    private fun buildTips(): View {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(PAGE_PAD_H), dp(16), dp(PAGE_PAD_H), dp(16))
        }
        col.addView(label("充電のコツ", taTitleLarge, literalColor = colorOnSurface, center = false))
        // v0.3.24 (noise audit): no intro line — it only restated
        // the title and the group headings below it.
        val groups: List<Pair<String, List<TipUi>>> = listOf(
            "スマホから給電する" to listOf(
                TipUi(
                    "スマホ同士をケーブルでつなぐ",
                    "USB-C ケーブルで2台をつなぐと、片方からもう片方へ充電できます。" +
                        "給電したい側のスマホで、接続時の通知から USB の設定を開き、「接続先のデバイスを充電」をONにします。" +
                        "逆向きに充電されることもあるので、残量の減り方で向きを確認してください。",
                ),
                TipUi(
                    "ワイヤレスで分け合う（逆充電）",
                    "対応機種では、背面に置くだけでイヤホンや他のスマホを充電できます。" +
                        "Pixel では「バッテリーシェア」という名前で、設定のバッテリーからONにします。" +
                        "対応していない機種では使えません。充電はゆっくりで、位置がずれると止まります。",
                ),
                TipUi(
                    "車・パソコン・タブレットからも",
                    "車の USB ポートやノートパソコン、タブレットの USB-C からも給電できます。" +
                        "コンセントの充電器よりゆっくりですが、充電器がない時の助けになります。",
                ),
            ),
            "速く充電する" to listOf(
                TipUi(
                    "機内モード・電源OFFが速い",
                    "通信を止めると充電中の消費が減るぶん、実質的に速くたまります。" +
                        "急ぐ時は機内モード、使わない時は電源を切って充電すると効果的です。",
                ),
                TipUi(
                    "充電器とケーブルで速さが変わる",
                    "急速充電は、対応した充電器とケーブルの組み合わせで初めて効きます。" +
                        "規格の合わない安価なケーブルでは遅くなることがあり、安全のためにも規格品を選んでください。",
                ),
            ),
            "電池を長持ちさせる" to listOf(
                TipUi(
                    "20〜80%の範囲がやさしい",
                    "満充電や空に近い状態が長く続くと、電池は劣化しやすくなります。" +
                        "日常的には残量20〜80%の範囲で使うのが目安です。" +
                        "機種によっては充電の上限を80%に止める設定もあります。",
                ),
                TipUi(
                    "熱がいちばんの大敵",
                    "充電しながらのゲームや動画など重い操作は、発熱と劣化のもとです。" +
                        "急速充電やワイヤレス充電も熱を持ちやすいので、急がない時は通常のケーブル充電が穏やかです。" +
                        "熱いと感じたら、ケースを外して休ませてください。",
                ),
                TipUi(
                    "100%のままつなぎっぱなしにしない",
                    "満充電のまま長時間つないでおくのも電池には負担です。" +
                        "夜間の充電では、アラームに合わせて満充電のタイミングを調整する「最適化充電」（Pixel のアダプティブ充電など）を使うと安心です。",
                ),
            ),
        )
        groups.forEach { (heading, tips) ->
            col.addView(groupHeading(heading))
            val c = card()
            val box = cardColumn(c)
            tips.forEachIndexed { i, tip ->
                if (i > 0) {
                    box.addView(
                        View(this).apply {
                            setBackgroundColor(
                                themeColor(com.google.android.material.R.attr.colorOutlineVariant),
                            )
                        },
                        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)),
                    )
                }
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(0, dp(12), 0, dp(12))
                }
                row.addView(label(tip.title, taTitleMedium, literalColor = colorOnSurface, center = false))
                val body = label(tip.body, taBodyMedium, literalColor = colorOnSurface, center = false)
                body.setPadding(0, dp(4), 0, 0)
                row.addView(body)
                box.addView(row)
            }
            withTopMargin(c, CARD_GAP)
            col.addView(c)
        }
        return ScrollView(this).apply { addView(col) }
    }
}
