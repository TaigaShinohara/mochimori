package com.taigashinohara.mochimori

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
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
import com.google.android.material.color.MaterialColors
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.navigation.NavigationBarView
import com.google.android.material.progressindicator.LinearProgressIndicator

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
 */
class MainActivity : AppCompatActivity() {

    private lateinit var content: FrameLayout
    private lateinit var bottomNav: NavigationBarView
    private lateinit var toolbar: MaterialToolbar
    private val prefs by lazy { getSharedPreferences("rules", MODE_PRIVATE) }

    private var snap: BatterySnapshot? = null
    private var currentScreen = 0
    private var detailsOpen = false
    private var goodOpen = false
    private var onboardingPage = 0
    private var syncingNav = false

    // M3 text appearances (type scale comes from the theme, not per-screen sizes)
    private val taHeadline = com.google.android.material.R.style.TextAppearance_Material3_HeadlineMedium
    private val taTitleLarge = com.google.android.material.R.style.TextAppearance_Material3_TitleLarge
    private val taTitleMedium = com.google.android.material.R.style.TextAppearance_Material3_TitleMedium
    private val taBodyLarge = com.google.android.material.R.style.TextAppearance_Material3_BodyLarge
    private val taBodyMedium = com.google.android.material.R.style.TextAppearance_Material3_BodyMedium
    private val taBodySmall = com.google.android.material.R.style.TextAppearance_Material3_BodySmall

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
                        R.id.nav_insights -> 1
                        R.id.nav_rules -> 2
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
            1 -> R.id.nav_insights
            2 -> R.id.nav_rules
            else -> R.id.nav_home
        }
        if (bottomNav.selectedItemId != targetId) {
            syncingNav = true
            bottomNav.selectedItemId = targetId
            syncingNav = false
        }
        setContent(
            when (index) {
                1 -> buildInsights()
                2 -> buildRules()
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
        col.addView(View(this), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
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
        col.addView(face, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(FACE_H)))

        val (labelText, accent) = when (result.state) {
            BatteryJudge.State.SAVING -> "節電できてる" to colorCool
            BatteryJudge.State.CHARGING -> "充電中" to colorCool
            BatteryJudge.State.EMERGENCY -> "残量わずか" to colorError
            BatteryJudge.State.UNKNOWN -> "確認中" to colorOnSurfaceVariant
            BatteryJudge.State.NOT_SAVING -> "節電できてない" to colorWarning
        }
        col.addView(label(labelText, taHeadline, literalColor = accent))
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

        // Numbers are folded away by default; the toggle names what is inside
        // (audit M-2). The rough estimate lives here now (audit R-7).
        val estText = if (result.state != BatteryJudge.State.UNKNOWN) {
            if (result.estimatedHours >= 1f) {
                "目安 約${"%.0f".format(result.estimatedHours)}時間"
            } else {
                "目安 約${"%.0f".format(result.estimatedHours * 60)}分"
            }
        } else {
            null
        }
        val detailsText = listOfNotNull(
            estText,
            "残量 ${if (s.levelKnown) "${s.levelPercent}%" else "—"}" +
                " ・ 温度 ${if (s.tempKnown) "${"%.1f".format(s.tempC)}℃" else "—"}" +
                " ・ 明るさ ${if (s.brightness >= 0) "${s.brightness}/255" else "—"}",
        ).joinToString(" ・ ")
        col.addView(textButton(if (detailsOpen) "数値を閉じる" else "数値を見る") {
            detailsOpen = !detailsOpen
            showScreen(0)
        })
        if (detailsOpen) {
            col.addView(label(detailsText, taBodySmall, literalColor = colorOnSurfaceVariant))
        }
        col.addView(View(this), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
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

    // ---------- Insights (R4, R5) ----------
    private enum class FixAction { BRIGHTNESS, AUTO_ON, HEAT_GUIDE, SAVER }
    private data class Insight(
        val title: String,
        val detail: String,
        val percent: Int,
        val fixLabel: String?,
        val fix: FixAction?,
        // Good-state items speak in the cool role, never the warning role
        // (v0.3.1 design pass: colour must match the meaning of the item).
        val good: Boolean = false,
    )

    private fun buildInsights(): View {
        refresh()
        val s = snap!!
        val waste = mutableListOf<Insight>()
        val good = mutableListOf<Insight>()
        if (s.brightness >= BatteryJudge.BRIGHTNESS_NEAR_MAX) {
            // Detail is the number only; the bar already shows the amount
            // (audit R-9). No ranking claim ("いちばん大きい") in prose.
            waste += Insight("明るさが最大付近のまま", "${s.brightness}/255", 90, "明るさを下げる", FixAction.BRIGHTNESS)
        } else if (s.brightness >= 0) {
            good += Insight("明るさは控えめ", "現在 ${s.brightness}/255", (s.brightness * 100 / 255).coerceIn(5, 100), null, null, good = true)
        }
        if (s.tempKnown && s.tempC >= 35f) {
            waste += Insight("電池が温かい", "現在 ${"%.1f".format(s.tempC)}℃ ・ 熱は電池の大敵です", ((s.tempC - 30) * 8).toInt().coerceIn(10, 100), "冷まし方を見る", FixAction.HEAT_GUIDE)
        }
        if (s.levelKnown && s.levelPercent <= 30 && !prefs.getBoolean("auto_saving", true)) {
            waste += Insight("自動節電がOFF", "残量 ${s.levelPercent}% ・ ONにすると自動で下げます", 100 - s.levelPercent, "ルールでONにする", FixAction.AUTO_ON)
        }
        if (s.levelKnown && s.levelPercent <= BatteryJudge.EMERGENCY_PERCENT) {
            waste += Insight("残量がわずか", "現在 ${s.levelPercent}% ・ 省電力モードの出番です", 100 - s.levelPercent, "省電力モードを開く", FixAction.SAVER)
        }
        val shownWaste = waste.take(3)
        // The sample card was retired (audit R-1): no placeholder data on a
        // real-data screen. It returns only as real data with the weekly
        // summary feature.

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(PAGE_PAD_H), dp(16), dp(PAGE_PAD_H), dp(16))
        }
        col.addView(label("今の無駄 ${shownWaste.size}件", taTitleLarge, literalColor = colorWarning, center = false))
        // The "責めない" posture line was removed (audit R-5): the tone is
        // carried by the wording itself, not declared on every visit.
        if (shownWaste.isEmpty()) {
            // Good/empty state says so calmly instead of leaving a bare "0件"
            // screen (v0.3.1 design pass).
            val empty = label("今は無駄なし。良い状態です", taTitleMedium, literalColor = colorCool, center = false)
            withTopMargin(empty, CARD_GAP)
            col.addView(empty)
        }
        shownWaste.forEach { col.addView(insightCard(it)) }
        if (good.isNotEmpty()) {
            // Good items are folded by default (audit R-6): heading + count
            // only. The home face already carries the "good" message; this
            // screen's job is the waste. (The 0-waste empty-state line above
            // stays, so an all-good screen is never bare.)
            col.addView(textButton(if (goodOpen) "良い状態を閉じる" else "良い状態 ${good.size}件") {
                goodOpen = !goodOpen
                showScreen(1)
            })
            if (goodOpen) {
                good.forEach { col.addView(insightCard(it)) }
            }
        }
        return ScrollView(this).apply { addView(col) }
    }

    private fun insightCard(item: Insight): View {
        val c = card(com.google.android.material.R.attr.colorSurfaceContainer)
        val box = cardColumn(c)
        box.addView(label(item.title, taTitleMedium, literalColor = colorOnSurface, center = false))
        val bar = LinearProgressIndicator(this).apply {
            max = 100
            setProgressCompat(item.percent, false)
            setIndicatorColor(if (item.good) colorCool else colorWarning)
            trackColor = themeColor(com.google.android.material.R.attr.colorSurfaceVariant)
        }
        val blp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        blp.topMargin = dp(8); blp.bottomMargin = dp(8) // spacing scale — was off-scale 6dp
        box.addView(bar, blp)
        box.addView(label(item.detail, taBodySmall, literalColor = colorOnSurfaceVariant, center = false))
        if (item.fixLabel != null && item.fix != null) {
            val b = MaterialButton(this).apply {
                text = item.fixLabel
                isAllCaps = false
                backgroundTintList = ColorStateList.valueOf(colorWarning)
                setTextColor(colorOnWarning)
                setOnClickListener { runFix(item.fix) }
            }
            box.addView(
                b,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) },
            )
        }
        withTopMargin(c, CARD_GAP)
        return c
    }

    private fun runFix(action: FixAction) {
        when (action) {
            FixAction.BRIGHTNESS -> fixBrightness()
            FixAction.AUTO_ON -> showScreen(2)
            FixAction.SAVER -> openBatterySaverSettings()
            FixAction.HEAT_GUIDE -> Toast.makeText(
                this,
                "アプリでは直せません。ケースを外し、涼しい場所で休ませてください",
                Toast.LENGTH_LONG,
            ).show()
        }
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
        col.addView(switchRow("自動節電", "auto_saving", true))
        // The fixed "30%（固定）" row was removed (audit R-2): the stage card
        // "節電 30%以下" already says it. The trailing "設定はこの4つだけ"
        // note was removed with it (audit R-3).
        col.addView(switchRow("80%充電の通知", "notify_80", true))
        col.addView(switchRow("発熱の通知", "notify_heat", true))
        // Haptic feedback for the home face's motion (v0.3.5, user request;
        // default ON). Same switch part and persistence as the others.
        col.addView(switchRow("触覚フィードバック", "haptics", true))
        return ScrollView(this).apply { addView(col) }
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
                if (key == "auto_saving") showScreen(2)
            }
        }
        row.addView(sw)
        return row
    }
}
