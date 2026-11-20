package com.taigashinohara.mochimori

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.OvalShape
import android.graphics.drawable.shapes.RoundRectShape
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
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
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
 *   structure      -> colorTertiary / surface roles
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
 */
class MainActivity : AppCompatActivity() {

    private lateinit var content: FrameLayout
    private lateinit var bottomNav: NavigationBarView
    private lateinit var toolbar: MaterialToolbar
    private val prefs by lazy { getSharedPreferences("rules", MODE_PRIVATE) }

    private var snap: BatterySnapshot? = null
    private var currentScreen = 0
    private var detailsOpen = false
    private var onboardingPage = 0
    private var syncingNav = false

    // M3 text appearances (type scale comes from the theme, not per-screen sizes)
    private val taHeadline = com.google.android.material.R.style.TextAppearance_Material3_HeadlineMedium
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

        // Home face slot height (v0.3.7: tokenised; the value itself is
        // unchanged from v0.3.6). The face's internal geometry — body
        // proportions, flame/flake headroom, ground shadow — lives in
        // BatteryFaceView; this token only fixes the slot it is given.
        const val FACE_H = 320

        // Glass plate behind the face (layout plan A, v0.3.11): the
        // plate is the battery BODY's rect grown by these amounts —
        // 24dp at the sides, 28dp above (it also covers the cap, which
        // stands ~11dp proud of the body) and 16dp below (over the
        // ground shadow's zone). Radius is the ExtraLarge shape token.
        // The body's rect itself is BatteryFaceView's geometry; the
        // plate mirrors its formula in buildHome() and never changes
        // the face's own size (FACE_H above is untouched).
        const val FACE_PLATE_PAD_H = 24
        const val FACE_PLATE_TOP = 28
        const val FACE_PLATE_BOTTOM = 16
        const val FACE_PLATE_RADIUS = 28
    }

    private fun themeColor(attr: Int): Int = MaterialColors.getColor(this, attr, Color.BLACK)
    private fun isNight(): Boolean =
        (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
    private val colorOnSurface get() = themeColor(com.google.android.material.R.attr.colorOnSurface)
    private val colorOnSurfaceVariant get() = themeColor(com.google.android.material.R.attr.colorOnSurfaceVariant)
    private val colorPrimary get() = themeColor(com.google.android.material.R.attr.colorPrimary)
    private val colorSecondary get() = themeColor(com.google.android.material.R.attr.colorSecondary)
    private val colorTertiary get() = themeColor(com.google.android.material.R.attr.colorTertiary)
    private val colorCool get() = themeColor(R.attr.colorMochiStateCool)
    private val colorOnCool get() = themeColor(R.attr.colorOnMochiStateCool)
    private val colorWarning get() = themeColor(R.attr.colorMochiWarning)
    private val colorOnWarning get() = themeColor(R.attr.colorOnMochiWarning)
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
            // Simple Glass foundation: one shared background for every
            // screen — a quiet 3-stop gradient plus soft lights in the
            // role colours' pale tints. The glass surfaces above it take
            // their colour from this light showing through, never from
            // paint on the surface itself.
            // v0.3.9 (real-device screenshot: "見づらい"): the stops now
            // stay inside ONE hue family (primary → secondary, both
            // blue-navy) — the tertiary (purple) stop is gone from the
            // foundation — Dynamic Color no longer tints anything (see
            // MochiApp), and the lights themselves are much fainter
            // (see AppBackgroundDrawable). Simple and calm by default.
            background = AppBackgroundDrawable(
                paper = themeColor(com.google.android.material.R.attr.colorSurface),
                tintA = themeColor(com.google.android.material.R.attr.colorPrimaryContainer),
                tintB = themeColor(com.google.android.material.R.attr.colorSecondaryContainer),
                blobA = themeColor(com.google.android.material.R.attr.colorPrimaryContainer),
                blobB = themeColor(com.google.android.material.R.attr.colorSecondaryContainer),
                blobC = themeColor(com.google.android.material.R.attr.colorPrimaryContainer),
                density = resources.displayMetrics.density,
            )
        }
        toolbar = MaterialToolbar(this).apply {
            title = "もち守り"
            // Overlap-weight band over the foundation (parts.md: top
            // bar; thickened in v0.3.9 so text always reads — see
            // GlassDrawable.band).
            background = GlassDrawable.band(
                themeColor(com.google.android.material.R.attr.colorSurface),
                isNight(), dp(1).toFloat(),
            )
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
            // Overlap-weight band (parts.md: tab bar; thickened in
            // v0.3.9, see GlassDrawable.band). The floating pill
            // form is deliberately NOT adopted: it is inseparable from
            // the edge-to-edge inset handling, and changing the bar's
            // shape without a real device to check it on risks the
            // layout — recorded as 未適用 in the glass review.
            background = GlassDrawable.band(
                themeColor(com.google.android.material.R.attr.colorSurface),
                isNight(), dp(1).toFloat(),
            )
            // v0.3.9: tab colours are set explicitly from the fixed
            // roles. On the v0.3.8 screenshot the unselected labels all
            // but vanished (translucent band + bright light behind +
            // wallpaper-tinted theme); on the thickened band these
            // pairs measure ~9:1 (dark) / ~7:1 (light) — see
            // docs/GLASS_REVIEW_v0.3.8.md.
            val navTint = android.content.res.ColorStateList(
                arrayOf(
                    intArrayOf(android.R.attr.state_checked),
                    intArrayOf(),
                ),
                intArrayOf(colorPrimary, colorOnSurfaceVariant),
            )
            itemIconTintList = navTint
            itemTextColor = navTint
            itemActiveIndicatorColor = android.content.res.ColorStateList.valueOf(
                themeColor(com.google.android.material.R.attr.colorSecondaryContainer),
            )
            inflateMenu(R.menu.bottom_nav)
            labelVisibilityMode = NavigationBarView.LABEL_VISIBILITY_LABELED
            setOnItemSelectedListener { item ->
                if (syncingNav) return@setOnItemSelectedListener true
                showScreen(
                    when (item.itemId) {
                        R.id.nav_rules -> 1
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
     * M3 Text Button (transparent container, 48dp target). The label
     * colour is the secondary role (deep navy), not primary: the
     * v0.3.10 glass audit's worst-case composite put primary #0277BD
     * at ≈3.7:1 on the light foundation's lower tint — under the
     * 4.5:1 gate — while secondary #46617A measures ≈5.0:1 there and
     * keeps a blue, tappable read against the near-black body text.
     * In the dark scheme both roles pass easily; secondary keeps the
     * two schemes in the same language.
     */
    private fun textButton(text: String, action: () -> Unit): MaterialButton {
        val b = MaterialButton(this).apply {
            this.text = text
            isAllCaps = false
            backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
            setTextColor(colorSecondary)
            strokeWidth = 0
            minimumHeight = dp(48) // M3 minimum touch target
            setOnClickListener { action() }
        }
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.topMargin = dp(8)
        b.layoutParams = lp
        return b
    }

    /**
     * Glass pill chip for the home screen's numbers toggle (layout
     * plan A, v0.3.11): the flat text button becomes a small pill of
     * reading glass (GlassDrawable.chip) with the same secondary
     * label as the text buttons — the chip recipe's worst-case
     * contrast is ≈5.8:1 (light) / ≈5.2:1 (dark), above the 4.5:1
     * gate (see the factory's doc). No icon: the mock-up's bar-chart
     * glyph has no existing part to reuse, and the label alone names
     * what the chip does (audit M-2's rule). The press feedback is a
     * ripple bounded by a pill mask over the glass; the touch target
     * keeps the 48dp minimum. The fold behaviour is unchanged — this
     * is the toggle's new clothes, not a new control. A plain
     * TextView carries it so no button tint machinery can wash the
     * glass out.
     */
    private fun glassChip(text: String, action: () -> Unit): TextView {
        val radiusPx = dp(24).toFloat() // half the 48dp target height: a true pill
        val glass = GlassDrawable.chip(
            radiusPx, isNight(), dp(1).toFloat(),
            themeColor(com.google.android.material.R.attr.colorSurface),
        )
        val mask = ShapeDrawable(RoundRectShape(FloatArray(8) { radiusPx }, null, null)).apply {
            paint.color = Color.WHITE
        }
        val chip = TextView(this).apply {
            this.text = text
            setTextAppearance(taLabelLarge)
            setTextColor(colorSecondary)
            background = RippleDrawable(
                ColorStateList.valueOf(ColorUtils.setAlphaComponent(colorSecondary, 48)),
                glass,
                mask,
            )
            gravity = Gravity.CENTER
            minimumHeight = dp(48) // M3 minimum touch target, same as the text buttons
            setPadding(dp(20), 0, dp(20), 0)
            isClickable = true
            isFocusable = true
            setOnClickListener { action() }
        }
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.topMargin = dp(8)
        chip.layoutParams = lp
        return chip
    }

    /**
     * Small group heading inside the home numbers fold (v0.3.18,
     * audit F-2): TitleSmall in the tertiary role — the art
     * direction's structural colour for headings — centred like
     * everything else in the home column. The fold's lines stay
     * BodySmall; the heading is what tells the reader which of the
     * fold's two kinds of information a line belongs to.
     */
    private fun foldHeading(text: String): TextView {
        val tv = label(text, taTitleSmall, literalColor = colorTertiary)
        val lp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
        )
        lp.topMargin = dp(12)
        tv.layoutParams = lp
        return tv
    }

    /**
     * Card used only where a contained group is meaningful — since
     * v0.3.8 a Simple Glass surface (see GlassDrawable): the neutral
     * surface-container card becomes reading glass (white 62%→54%
     * light / 19%→13% dark over the foundation's light), and the
     * role-container cards (rules stages) become role-tint glass —
     * the container colour kept nearly opaque with a gentle gradient,
     * so state colours and on-container text contrast are unchanged.
     * Both carry the 1dp specular edge. The card's own fill is
     * transparent; the glass is a full-size child behind the content,
     * which keeps MaterialCardView's shape/outline behaviour intact.
     * Radius moves from Medium (12dp) to the Large token (20dp), the
     * Simple Glass card band (20–28dp).
     */
    private fun card(bgAttr: Int): MaterialCardView = MaterialCardView(this).apply {
        setCardBackgroundColor(ColorStateList.valueOf(Color.TRANSPARENT))
        radius = dp(20).toFloat() // ShapeAppearance Large token (Simple Glass card band)
        cardElevation = 0f
        useCompatPadding = false
        val glass = View(this@MainActivity).apply {
            background = if (bgAttr == com.google.android.material.R.attr.colorSurfaceContainer) {
                GlassDrawable.reading(
                    dp(20).toFloat(), isNight(), dp(1).toFloat(),
                    themeColor(com.google.android.material.R.attr.colorSurface),
                )
            } else {
                GlassDrawable.tint(themeColor(bgAttr), dp(20).toFloat(), isNight(), dp(1).toFloat())
            }
        }
        addView(glass, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
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
                col.addView(label("もち守り", taHeadline, literalColor = colorTertiary))
                col.addView(label("何もしなくても、電池が持つ。", taBodyLarge, literalColor = colorOnSurface))
                col.addView(label("自動でできること", taTitleMedium, literalColor = colorCool))
                col.addView(label("残量が減ったら、明るさと消灯時間を\n自動で下げます", taBodyLarge, literalColor = colorOnSurface))
                col.addView(label("充電80%と発熱は、通知で知らせます", taBodyLarge, literalColor = colorOnSurface))
                col.addView(progressDots(0))
                col.addView(primaryButton("次へ") { showOnboarding(1) })
            }
            1 -> {
                col.addView(label("正直な線引き", taTitleLarge, literalColor = colorTertiary))
                col.addView(label("1タップでできること", taTitleMedium, literalColor = colorOnSurface))
                col.addView(label("省電力モードはボタンから\n設定画面を開く", taBodyLarge, literalColor = colorOnSurface))
                col.addView(label("できないこと", taTitleMedium, literalColor = colorOnSurface))
                col.addView(label("他のアプリを止めることはできません", taBodyLarge, literalColor = colorOnSurface))
                col.addView(label("Wi-Fiを勝手に切ることもできません", taBodyLarge, literalColor = colorOnSurface))
                col.addView(progressDots(1))
                col.addView(primaryButton("次へ") { showOnboarding(2) })
            }
            else -> {
                col.addView(label("許可のお願い", taTitleLarge, literalColor = colorTertiary))
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
     * (R-10). Since the v0.3.8 design pass the current position is
     * carried by shape AND the tertiary role colour (parts.md: dot
     * indicator — current = role-colour solid, others = quiet dots);
     * the spoken step count stays in the contentDescription.
     */
    private fun progressDots(page: Int): TextView {
        val text = (0..2).joinToString(" ") { if (it == page) "●" else "○" }
        val span = android.text.SpannableString(text)
        val start = page * 2 // each dot + one separating space
        span.setSpan(
            android.text.style.ForegroundColorSpan(colorTertiary),
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
            setPadding(dp(PAGE_PAD_H), dp(16), dp(PAGE_PAD_H), dp(16))
        }
        // Distribute the cluster over the full viewport height: weighted
        // spacers above and below absorb the slack, so the face no longer
        // huddles in the top half with a dead lower half (v0.3.4). When the
        // content outgrows the viewport the spacers collapse and it scrolls.
        // Layout plan A (v0.3.11): the weights go 3:4 instead of 1:1, so
        // the cluster rides a little higher, as in the chosen mock-up.
        col.addView(View(this), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 3f))
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
        // Layout plan A (v0.3.11): a thin decorative-glass plate behind
        // the face, one size larger than the battery body, so face +
        // plate read as a single cluster. The plate is a layout element
        // only — BatteryFaceView's drawing is untouched, and the face
        // slot keeps its FACE_H height and full width. The body's rect
        // is mirrored from BatteryFaceView.onSizeChanged (top zone
        // 58dp, shadow zone 20dp, body height capped at 272dp, width =
        // height / 1.55 capped at 60% of the slot, top biased 0.35 into
        // the leftover height); the plate grows that rect by the
        // FACE_PLATE_* tokens.
        val faceFrame = FrameLayout(this)
        run {
            val density = resources.displayMetrics.density
            val slotWDp = resources.displayMetrics.widthPixels / density - 2 * PAGE_PAD_H
            val availHDp = FACE_H - 58f - 20f
            var bodyHDp = minOf(availHDp, 272f)
            var bodyWDp = bodyHDp / 1.55f
            val maxBodyWDp = slotWDp * 0.60f
            if (bodyWDp > maxBodyWDp) {
                bodyWDp = maxBodyWDp
                bodyHDp = bodyWDp * 1.55f
            }
            val bodyTopDp = 58f + (availHDp - bodyHDp) * 0.35f
            val plate = View(this).apply {
                background = GlassDrawable.decorative(
                    dp(FACE_PLATE_RADIUS).toFloat(), isNight(), dp(1).toFloat(),
                )
            }
            faceFrame.addView(
                plate,
                FrameLayout.LayoutParams(
                    dp((bodyWDp + 2 * FACE_PLATE_PAD_H).toInt()),
                    dp((bodyHDp + FACE_PLATE_TOP + FACE_PLATE_BOTTOM).toInt()),
                    Gravity.TOP or Gravity.CENTER_HORIZONTAL,
                ).apply { topMargin = dp((bodyTopDp - FACE_PLATE_TOP).toInt()) },
            )
        }
        faceFrame.addView(face, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        col.addView(faceFrame, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(FACE_H)))

        val (labelText, accent) = when (result.state) {
            BatteryJudge.State.SAVING -> "節電できてる" to colorCool
            BatteryJudge.State.CHARGING -> "充電中" to colorCool
            BatteryJudge.State.EMERGENCY -> "残量わずか" to colorError
            BatteryJudge.State.UNKNOWN -> "確認中" to colorOnSurfaceVariant
            BatteryJudge.State.NOT_SAVING -> "節電できてない" to colorWarning
        }
        // Layout plan A (v0.3.11): label + reason pull in tighter under
        // the face so the three read as one block — the state label is
        // pulled 8dp up into the face slot's empty shadow zone (the
        // drawn shadow ends ~4dp above the slot's bottom edge, so
        // nothing overlaps) and the label→reason gap closes to 4dp.
        // Both moves stay inside the spacing scale.
        val stateLabel = label(labelText, taHeadline, literalColor = accent)
        stateLabel.setPadding(0, dp(4), 0, 0)
        col.addView(
            stateLabel,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(-8)
            },
        )
        // Reason: BodyLarge on onSurface. onSurfaceVariant at this size was
        // too dim to read on the dark theme (user screenshot, v0.3.4).
        col.addView(label(result.reason, taBodyLarge, literalColor = colorOnSurface))
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
                col.addView(label("抜くのがおすすめです", taTitleMedium, literalColor = colorCool))
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
        // checklist in v0.3.18 (audit F-1). The normal card is
        // reading glass (card() with the surfaceContainer role) and
        // always present; its foot carries the guide row to the
        // rules screen, where the limit-mode switch now lives alone
        // (v0.3.18, audit F-3). The limit card exists ONLY while
        // that mode is ON (the user's instruction: the limit list
        // appears only in limit mode — no greyed-out placeholder
        // when it is off). Rows are measured values judged by
        // SavingChecklist, and every row taps through to the place
        // that can change it (see onChecklistTap).
        val checklistInput = readChecklistInput(s)
        val limitMode = prefs.getBoolean("limit_mode", false)
        val checklist = checklistCard(
            SavingChecklist.evaluateNormal(checklistInput),
            title = "節電チェック",
            limitStyle = false,
            footer = limitGuideRow(),
        )
        withTopMargin(checklist, CARD_GAP)
        col.addView(checklist)
        if (limitMode) {
            val limitCard = checklistCard(
                SavingChecklist.evaluateLimit(checklistInput),
                title = "限界まで節電",
                limitStyle = true,
                footer = null,
            )
            withTopMargin(limitCard, CARD_GAP)
            col.addView(limitCard)
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
                "満充電まで${BatteryPace.formatDuration((s.chargeTimeRemainingMs / 60000.0).roundToLong())}（目安）"
            } else {
                null
            }
            else -> {
                val minutes = BatteryPace.estimateMinutesToEmpty(BatteryHistory.load(this))
                if (minutes != null) {
                    "あと${BatteryPace.formatDuration(minutes)}で0%（目安）"
                } else {
                    "0%までの時間は計測中"
                }
            }
        }
        val numbersText = "残量 ${if (s.levelKnown) "${s.levelPercent}%" else "—"}" +
            " ・ 温度 ${if (s.tempKnown) "${"%.1f".format(s.tempC)}℃" else "—"}" +
            " ・ 明るさ ${if (s.brightness >= 0) "${s.brightness}/255" else "—"}" +
            if (s.voltageMv > 0) " ・ 電圧 ${"%.2f".format(s.voltageMv / 1000f)}V" else ""
        // Health lines: only what this device actually reported.
        // No design capacity is readable by a general app, so no
        // degradation percentage is ever derived or shown.
        val healthLines = listOfNotNull(
            BatteryHealth.healthLabel(s.health)?.let { "電池の健康 $it" },
            if (s.cycleCount >= 0) "サイクル数 約${s.cycleCount}回" else null,
            BatteryHealth.estimatedFullCapacityMah(s.chargeCounterMicroAh, s.levelPercent)
                ?.let { "満充電容量 約${"%,d".format(it)}mAh（推定）" },
        )
        col.addView(glassChip(if (detailsOpen) "数値を閉じる" else "数値を見る") {
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
            val timeAndHealth = listOfNotNull(remainingText).plus(healthLines)
            if (timeAndHealth.isNotEmpty()) {
                col.addView(foldHeading("残り時間と健康状態"))
                timeAndHealth.forEach { line ->
                    col.addView(label(line, taBodySmall, literalColor = colorOnSurfaceVariant))
                }
            }
            col.addView(foldHeading("いまの数値"))
            col.addView(label(numbersText, taBodySmall, literalColor = colorOnSurfaceVariant))
        }
        // Bottom spacer of the 3:4 pair (see the top spacer, v0.3.11).
        col.addView(View(this), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 4f))
        return ScrollView(this).apply {
            isFillViewport = true
            addView(col, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
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
     * is the shared card() reading glass — text sits on it, so it
     * takes the thick, text-bearing recipe like every other reading
     * surface. The percent is SavingChecklist.percent for THIS
     * card's list only: judged items only, UNKNOWN rows out of the
     * denominator, and a dash in the middle when nothing could be
     * judged at all. The limit card is told apart from the normal
     * one inside the existing roles only: its donut arc and title
     * take the warning role (the tier is the "push harder" one);
     * the surface, marks and row language are unchanged. [footer],
     * when given, is appended under the rows (the normal card
     * carries the guide row to the rules screen there — the
     * limit-mode switch itself moved to rules alone in v0.3.18).
     */
    private fun checklistCard(
        entries: List<SavingChecklist.Entry>,
        title: String,
        limitStyle: Boolean,
        footer: View?,
    ): MaterialCardView {
        val c = card(com.google.android.material.R.attr.colorSurfaceContainer)
        val box = cardColumn(c)
        val percent = SavingChecklist.percent(entries)
        val summary = SavingChecklist.summary(entries)

        val head = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val donutSize = dp(72)
        val donutWrap = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(donutSize, donutSize)
            contentDescription =
                if (percent != null) "$title ${percent}パーセント、$summary" else "$title、$summary"
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
                setTextAppearance(taTitleLarge)
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
        headTexts.addView(
            label(
                title, taTitleMedium,
                literalColor = if (limitStyle) colorWarning else colorOnSurface,
                center = false,
            ),
        )
        headTexts.addView(
            label(summary, taBodyMedium, literalColor = colorOnSurfaceVariant, center = false),
        )
        head.addView(
            headTexts,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
        )
        box.addView(head)
        entries.forEach { box.addView(checklistRow(it)) }
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
            minimumHeight = dp(48)
            setPadding(dp(4), dp(8), dp(4), 0)
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
     * a single line, 48dp tall, with the theme's selectable ripple.
     * The value stays onSurfaceVariant in every state — body text
     * never takes the warning colour (audit M-1); the mark carries
     * the status, by shape as well as colour.
     */
    private fun checklistRow(entry: SavingChecklist.Entry): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(48)
            setPadding(dp(4), dp(4), dp(4), dp(4))
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
     * Status mark: a glyph in a small filled circle, distinguished by
     * shape AND colour (✓ in the cool role, ！ in the warning role,
     * ？ and i in the surface-variant role) so the meaning never rides
     * on colour alone. The circle fill is the matching container colour
     * and the glyph its role colour — large-symbol contrast in both
     * schemes.
     */
    private fun checkMark(status: SavingChecklist.Status): TextView {
        val (glyph, fill, fg) = when (status) {
            SavingChecklist.Status.OK -> Triple(
                "✓",
                themeColor(R.attr.colorMochiStateCoolContainer),
                colorCool,
            )
            SavingChecklist.Status.NG -> Triple(
                "！",
                themeColor(R.attr.colorMochiWarningContainer),
                colorWarning,
            )
            SavingChecklist.Status.UNKNOWN -> Triple(
                "？",
                themeColor(com.google.android.material.R.attr.colorSurfaceVariant),
                colorOnSurfaceVariant,
            )
            SavingChecklist.Status.INFO -> Triple(
                // Advice, not a verdict: the same quiet surface as
                // UNKNOWN, told apart by the glyph (i, not ？).
                "i",
                themeColor(com.google.android.material.R.attr.colorSurfaceVariant),
                colorOnSurfaceVariant,
            )
        }
        return TextView(this).apply {
            text = glyph
            setTextAppearance(taLabelLarge)
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(fg)
            gravity = Gravity.CENTER
            background = ShapeDrawable(OvalShape()).apply { paint.color = fill }
            layoutParams = LinearLayout.LayoutParams(dp(28), dp(28))
        }
    }

    /**
     * Where a row tap goes: the settings screen that owns the value,
     * the rules screen for this app's own toggle, the permission flow
     * when the auto-saving row is waiting on it, and a how-to dialog
     * for temperature — heat has no settings screen, so the honest
     * destination is an explanation (the same cooling advice the
     * home HEAT line gives, spelled out). The v0.3.16 rows each open
     * the settings screen that owns their switch (Wi-Fi, data usage,
     * location, NFC, Bluetooth, sound for haptics, display for the
     * dark theme).
     */
    private fun onChecklistTap(entry: SavingChecklist.Entry) {
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
        col.addView(label("残量で自動で変わる", taTitleLarge, literalColor = colorTertiary, center = false))
        // The "~15 min" note is no longer a permanent line here (audit R-8):
        // it is told once when auto-saving is turned ON (see switchRow).
        // Each stage is one meaningful contained group -> one filled card,
        // coloured by its state container role, text in the matching on* role.
        val stages = listOf(
            StageUi(
                "通常 31%〜", "何もしない・見守るだけ",
                com.google.android.material.R.attr.colorPrimaryContainer,
                com.google.android.material.R.attr.colorOnPrimaryContainer,
            ),
            StageUi(
                "節電 30%以下", "定期チェックで 明るさ↓ ・ 消灯15秒（許可がある時）",
                R.attr.colorMochiWarningContainer, R.attr.colorOnMochiWarningContainer,
            ),
            StageUi(
                "緊急 15%以下", "省電力モードの1タップをホームに大きく出す",
                com.google.android.material.R.attr.colorErrorContainer,
                com.google.android.material.R.attr.colorOnErrorContainer,
            ),
        )
        stages.forEach { st ->
            val c = card(st.containerAttr)
            val box = cardColumn(c)
            box.addView(label(st.title, taTitleMedium, literalColor = themeColor(st.onContainerAttr), center = false))
            box.addView(label(st.detail, taBodySmall, literalColor = themeColor(st.onContainerAttr), center = false))
            withTopMargin(c, CARD_GAP)
            col.addView(c)
        }
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
     * TitleMedium in the tertiary role — the catalog's 小見出し
     * language, the same structural colour as this screen's
     * TitleLarge page title — with one ACTION_GAP of air above it,
     * so the switch column reads as named groups instead of one
     * undifferentiated list.
     */
    private fun groupHeading(text: String): View {
        val tv = label(text, taTitleMedium, literalColor = colorTertiary, center = false)
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
        val containerAttr: Int,
        val onContainerAttr: Int,
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
}
