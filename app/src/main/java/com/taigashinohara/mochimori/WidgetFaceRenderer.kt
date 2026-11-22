package com.taigashinohara.mochimori

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.view.ContextThemeWrapper
import androidx.core.graphics.ColorUtils
import com.google.android.material.color.MaterialColors
import kotlin.math.min
import kotlin.math.sin

/**
 * Static face for the home-screen widget (v0.3.12).
 *
 * A widget cannot host the animated custom View, so this renders ONE
 * still frame of the face into a Bitmap with plain Canvas calls. It is
 * deliberately a simplified portrait of [BatteryFaceView], drawn with
 * the same geometry language (upright body 1:1.55, cap on top centre,
 * liquid filling from the bottom by level) and the same fixed state
 * colours, frozen at a representative phase:
 *  - the liquid surface is a gentle still wave (no tilt, no splash,
 *    no particles — a widget frame is honest about being a still),
 *  - the simple face (dot eyes + mouth) is drawn with its eyes open,
 *    the bolt appears only while charging,
 *  - bubbles are a few fixed seeds inside the liquid.
 * State comes from [BatteryJudge] via the caller — this renderer never
 * judges anything itself.
 *
 * v0.3.21: the still follows the live face's M3 flattening — solid
 * shell and outline, no aura, ground shadow, gloss, rim, cavity
 * shading, bolt glow or flake halo (see BatteryFaceView's doc).
 *
 * v0.3.23: the still follows the live face's simple-face redesign —
 * the flames and crystals are retired here too, and the character's
 * dot eyes and mouth (smile / open oval / flat line, by state) are
 * drawn over the body, exactly as on the home screen.
 *
 * v0.3.27: the still follows the live face's burning → cooling
 * expression, simplified for a frozen frame: the metal rim band
 * under the cap, the liquid coloured by the checklist heat
 * (HeatScene.liquidColor), geometric flames at the scene's flame
 * level (frozen upright, no sway), the earned cooling objects as
 * the same small geometric shapes, and the mouth following the
 * heat. The scene is decided by the caller (WidgetUpdater) from
 * the same checklist reads as the home screen, via the shared
 * ChecklistReader — the widget never judges differently.
 */
object WidgetFaceRenderer {

    private enum class Kind { SAVING, HOT, CHARGING, UNKNOWN }

    // Virtual canvas the face is composed in; the bitmap is this space
    // scaled up. Proportions mirror BatteryFaceView.onSizeChanged.
    private const val VW = 150f
    private const val VH = 190f

    fun render(
        context: Context,
        state: BatteryJudge.State,
        levelPercent: Int,
        scene: HeatScene.Scene? = null,
    ): Bitmap {
        val kind = when (state) {
            BatteryJudge.State.SAVING -> Kind.SAVING
            BatteryJudge.State.CHARGING -> Kind.CHARGING
            BatteryJudge.State.NOT_SAVING, BatteryJudge.State.EMERGENCY -> Kind.HOT
            BatteryJudge.State.UNKNOWN -> Kind.UNKNOWN
        }
        val density = context.resources.displayMetrics.density
        // 60dp x 76dp on screen, rendered at 2x for crispness.
        val pxW = (60f * density * 2f).toInt().coerceAtLeast(120)
        val pxH = (pxW * VH / VW).toInt()
        val bitmap = Bitmap.createBitmap(pxW, pxH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.scale(pxW / VW, pxH / VH)
        draw(canvas, context, kind, levelPercent.coerceIn(0, 100), scene)
        return bitmap
    }

    private fun draw(canvas: Canvas, context: Context, kind: Kind, level: Int, scene: HeatScene.Scene?) {
        val themed = ContextThemeWrapper(context, R.style.Theme_MochiMori)

        fun attr(attrRes: Int, fallback: Int): Int =
            MaterialColors.getColor(themed, attrRes, fallback)

        val cool = attr(R.attr.colorMochiStateCool, Color.rgb(0x02, 0x77, 0xBD))
        val onCool = attr(R.attr.colorOnMochiStateCool, Color.WHITE)
        val coolFill = attr(R.attr.colorMochiStateCoolContainer, Color.rgb(0xE0, 0xF2, 0xFB))
        val hot = attr(R.attr.colorMochiWarning, Color.rgb(0xE6, 0x51, 0x00))
        val onHot = attr(R.attr.colorOnMochiWarning, Color.WHITE)
        val hotFill = attr(R.attr.colorMochiWarningContainer, Color.rgb(0xFF, 0xED, 0xD9))
        val neutral = attr(
            com.google.android.material.R.attr.colorOnSurfaceVariant, Color.rgb(0x90, 0x90, 0x90),
        )
        val neutralFill = attr(
            com.google.android.material.R.attr.colorSurfaceVariant, Color.rgb(0xF0, 0xF0, 0xF0),
        )
        val cavityBase = attr(
            com.google.android.material.R.attr.colorSurfaceContainerHigh, Color.rgb(0xDF, 0xEA, 0xF2),
        )

        var (accent, container, onAccent) = when (kind) {
            Kind.SAVING, Kind.CHARGING -> Triple(cool, coolFill, onCool)
            Kind.HOT -> Triple(hot, hotFill, onHot)
            Kind.UNKNOWN -> Triple(neutral, neutralFill, neutralFill)
        }
        // Heat expression (v0.3.27): while a heat verdict exists,
        // the liquid's colour follows the heat, exactly as on the
        // live face; the shell tint blends between the cool and
        // warning containers. Charging keeps the plain cool face.
        val heat = scene?.heat
        if (heat != null && (kind == Kind.SAVING || kind == Kind.HOT)) {
            accent = HeatScene.liquidColor(heat)
            container = ColorUtils.blendARGB(coolFill, hotFill, heat)
        }
        val cavity = ColorUtils.blendARGB(cavityBase, accent, 0.10f)

        // ---- Geometry (mirrors BatteryFaceView.onSizeChanged) ----
        val topZone = 34f
        val bottomZone = 12f
        val availH = VH - topZone - bottomZone
        var bodyH = min(availH, 150f)
        var bodyW = bodyH / 1.55f
        val maxBodyW = VW * 0.60f
        if (bodyW > maxBodyW) {
            bodyW = maxBodyW
            bodyH = bodyW * 1.55f
        }
        val k = bodyW / 156f // scale vs the live face's dp numbers
        val u: (Float) -> Float = { v -> v * k }
        val body = RectF(
            (VW - bodyW) / 2f, topZone + (availH - bodyH) * 0.35f,
            (VW + bodyW) / 2f, topZone + (availH - bodyH) * 0.35f + bodyH,
        )
        val inset = u(7f)
        val inner = RectF(body).apply { inset(inset, inset) }
        val capW = bodyW * 0.44f
        val capH = u(13f)
        val cap = RectF(
            body.centerX() - capW / 2f, body.top - capH + u(2f),
            body.centerX() + capW / 2f, body.top + u(2f),
        )
        val radius = u(22f)
        val innerRadius = u(16f)

        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
        }

        // No aura and no ground shadow (removed in v0.3.21, with the
        // live face).

        // ---- Heat expression around the body (v0.3.27) ----
        // Drawn first, as on the live face, so the body overlaps
        // the flames' bases. Frozen: flames stand upright, the
        // fan's blades rest at a fixed angle.
        if (scene != null && kind != Kind.UNKNOWN) {
            if (kind != Kind.CHARGING && scene.flameLevel > 0 && heat != null) {
                drawFlames(canvas, fill, body, scene.flameLevel, heat)
            }
            if (scene.objects.isNotEmpty()) {
                drawCoolObjects(canvas, fill, stroke, body, scene)
            }
        }

        // ---- Body: the container colour as an opaque solid (v0.3.21) ----
        fill.color = container
        canvas.drawRoundRect(body, radius, radius, fill)

        // Cavity (the unfilled part stays a visible neutral surface).
        fill.color = cavity
        canvas.drawRoundRect(inner, innerRadius, innerRadius, fill)

        // ---- Liquid: fills from the bottom, one still gentle wave ----
        val fillH = inner.height() * (level / 100f)
        if (fillH > 0.5f) {
            val baseAmp = when (kind) {
                Kind.HOT -> u(2.1f)
                Kind.CHARGING -> u(1.5f)
                Kind.SAVING -> u(1.1f)
                Kind.UNKNOWN -> 0f
            }
            val phase0 = 0.9f
            val kf1 = (2 * Math.PI / (inner.width() * 0.9f)).toFloat()
            val kf2 = (2 * Math.PI / (inner.width() * 0.45f)).toFloat()
            fun surfaceY(x: Float): Float {
                val dx = x - inner.left
                val baseY = inner.bottom - fillH
                return baseY +
                    baseAmp * sin((dx * kf1 - phase0).toDouble()).toFloat() +
                    baseAmp * 0.45f * sin((dx * kf2 + phase0 * 0.63f + 1.7f).toDouble()).toFloat()
            }
            val clip = Path().apply { addRoundRect(inner, innerRadius, innerRadius, Path.Direction.CW) }
            canvas.save()
            canvas.clipPath(clip)
            val wave = Path()
            wave.moveTo(inner.left - 2f, inner.bottom + 2f)
            val steps = 24
            for (i in 0..steps) {
                val x = inner.left + inner.width() * (i / steps.toFloat())
                wave.lineTo(x, surfaceY(x))
            }
            wave.lineTo(inner.right + 2f, inner.bottom + 2f)
            wave.close()
            fill.shader = LinearGradient(
                0f, inner.top, 0f, inner.bottom,
                intArrayOf(
                    ColorUtils.blendARGB(accent, Color.WHITE, 0.50f),
                    ColorUtils.blendARGB(accent, Color.WHITE, 0.16f),
                    accent,
                    ColorUtils.blendARGB(accent, Color.BLACK, 0.20f),
                ),
                floatArrayOf(0f, 0.35f, 0.72f, 1f), Shader.TileMode.CLAMP,
            )
            canvas.drawPath(wave, fill)
            fill.shader = null
            // A few still bubbles (fixed seeds, only under the surface).
            if (kind != Kind.UNKNOWN && fillH > u(24f)) {
                fill.color = ColorUtils.blendARGB(accent, Color.WHITE, 0.55f)
                val seeds = floatArrayOf(0.11f, 0.53f, 0.83f, 0.31f, 0.67f, 0.23f, 0.97f, 0.41f)
                val count = when (kind) {
                    Kind.HOT -> 8
                    Kind.CHARGING -> 7
                    else -> 3
                }
                for (i in 0 until count) {
                    val seed = seeds[i]
                    val xF = frac(seed * 7.31f)
                    val yF = frac(seed * 3.97f)
                    val x = inner.left + inner.width() * xF
                    val y = (inner.bottom - u(3f)) - yF * (fillH - u(12f))
                    if (y > surfaceY(x) + u(2f)) {
                        fill.alpha = 110
                        canvas.drawCircle(x, y, u(0.8f) + u(1.6f) * frac(seed * 3.7f), fill)
                    }
                }
                fill.alpha = 255
            }
            // Bright meniscus line along the surface.
            val edge = Path()
            for (i in 0..steps) {
                val x = inner.left + inner.width() * (i / steps.toFloat())
                if (i == 0) edge.moveTo(x, surfaceY(x)) else edge.lineTo(x, surfaceY(x))
            }
            stroke.color = ColorUtils.blendARGB(accent, Color.WHITE, 0.65f)
            stroke.alpha = 235
            stroke.strokeWidth = u(2.3f)
            canvas.drawPath(edge, stroke)
            stroke.alpha = 255
            canvas.restore()
        }

        // ---- Rim band (v0.3.27): the metal band across the body's
        // top, under the cap — the battery read of the approved
        // mock-up, drawn over the cavity and liquid like the live
        // face's band, clipped to the body's round rect.
        run {
            val rimClip = Path().apply { addRoundRect(body, radius, radius, Path.Direction.CW) }
            canvas.save()
            canvas.clipPath(rimClip)
            fill.shader = LinearGradient(
                body.left, 0f, body.right, 0f,
                intArrayOf(
                    ColorUtils.blendARGB(neutral, Color.BLACK, 0.28f),
                    ColorUtils.blendARGB(neutral, Color.WHITE, 0.20f),
                    ColorUtils.blendARGB(neutral, Color.WHITE, 0.65f),
                    ColorUtils.blendARGB(neutral, Color.WHITE, 0.20f),
                    ColorUtils.blendARGB(neutral, Color.BLACK, 0.28f),
                ),
                floatArrayOf(0f, 0.28f, 0.5f, 0.72f, 1f), Shader.TileMode.CLAMP,
            )
            canvas.drawRect(body.left, body.top, body.right, body.top + body.height() * 0.075f, fill)
            fill.shader = null
            canvas.restore()
        }

        // ---- Cap (metal gradient, kept as on the live face) ----
        val capLight = ColorUtils.blendARGB(neutral, Color.WHITE, 0.65f)
        val capMid = ColorUtils.blendARGB(neutral, Color.WHITE, 0.20f)
        val capDark = ColorUtils.blendARGB(neutral, Color.BLACK, 0.28f)
        fill.shader = LinearGradient(
            cap.left, 0f, cap.right, 0f,
            intArrayOf(capDark, capMid, capLight, capMid, capDark),
            floatArrayOf(0f, 0.28f, 0.5f, 0.72f, 1f), Shader.TileMode.CLAMP,
        )
        canvas.drawRoundRect(cap, u(4f), u(4f), fill)
        fill.shader = null

        // ---- Outline: the state colour as a solid stroke (v0.3.21) ----
        stroke.color = accent
        stroke.strokeWidth = u(4.5f)
        canvas.drawRoundRect(body, radius, radius, stroke)

        // ---- Charging bolt (the charging state's marker) ----
        if (kind == Kind.CHARGING) {
            bolt(canvas, fill, stroke, body, accent, onAccent, u)
        }

        // ---- The simple face (v0.3.23): dot eyes + mouth ----
        // The same marks as the live face, frozen with the eyes
        // open: a calm smile while saving or charging, a small
        // open mouth while not saving, a flat line while unknown.
        // Drawn last, over the cavity and the liquid, in the state
        // colour deepened toward black — the live face's rule.
        val faceColor = ColorUtils.blendARGB(accent, Color.BLACK, 0.45f)
        fill.color = faceColor
        fill.alpha = 242
        val cx = body.centerX()
        val eyeR = u(3.4f)
        val eyeY = body.top + body.height() * 0.17f
        val eyeDX = body.width() * 0.155f
        for (side in -1..1 step 2) {
            canvas.drawCircle(cx + side * eyeDX, eyeY, eyeR, fill)
        }
        val mouthY = body.top + body.height() * 0.28f
        // v0.3.27: while a heat verdict exists the mouth follows
        // the heat, as on the live face; otherwise the v0.3.23
        // mouths by kind.
        val heatMouth = scene?.takeIf { it.heat != null }?.mouth
        val mouth: HeatScene.Mouth? = when {
            kind == Kind.UNKNOWN -> HeatScene.Mouth.FLAT
            kind == Kind.CHARGING -> HeatScene.Mouth.SMILE
            heatMouth != null -> heatMouth
            kind == Kind.SAVING -> HeatScene.Mouth.SMILE
            else -> null // legacy not-saving: the small open oval
        }
        when (mouth) {
            HeatScene.Mouth.SMILE -> {
                val mw = u(16f); val mh = u(11f)
                stroke.color = faceColor
                stroke.alpha = 242
                stroke.strokeWidth = u(2.4f)
                canvas.drawArc(
                    cx - mw / 2f, mouthY - mh / 2f, cx + mw / 2f, mouthY + mh / 2f,
                    25f, 130f, false, stroke,
                )
                stroke.alpha = 255
            }
            HeatScene.Mouth.WAVY -> {
                // Trembling zigzag, frozen (the live face's hot mouth).
                val halfW = u(8f); val amp = u(2.2f)
                stroke.color = faceColor
                stroke.alpha = 242
                stroke.strokeWidth = u(2.4f)
                val seg = (halfW * 2f) / 4f
                val zig = Path()
                zig.moveTo(cx - halfW, mouthY)
                for (i in 1..4) {
                    val x = cx - halfW + seg * i
                    val y = when {
                        i == 4 -> mouthY
                        i % 2 == 1 -> mouthY - amp
                        else -> mouthY + amp
                    }
                    zig.lineTo(x, y)
                }
                canvas.drawPath(zig, stroke)
                stroke.alpha = 255
            }
            HeatScene.Mouth.FLAT -> {
                val half = u(5.5f)
                stroke.color = faceColor
                stroke.alpha = 242
                stroke.strokeWidth = u(2.4f)
                canvas.drawLine(cx - half, mouthY, cx + half, mouthY, stroke)
                stroke.alpha = 255
            }
            null -> {
                val mw = u(7.5f); val mh = u(10f)
                val oval = RectF(cx - mw / 2f, mouthY - mh / 2f, cx + mw / 2f, mouthY + mh / 2f)
                canvas.drawOval(oval, fill)
            }
        }
        fill.alpha = 255
    }

    /** The bolt — charging only, same proportions as the live face. */
    private fun bolt(
        canvas: Canvas, fill: Paint, stroke: Paint, body: RectF,
        accent: Int, onAccent: Int, u: (Float) -> Float,
    ) {
        val cx = body.centerX(); val cy = body.centerY()
        val bodyH = body.height()
        // Flat fill (v0.3.21: no glow circle, no gradient — the same
        // single colour as the live face's boltFill).
        val boltH = bodyH * 0.36f
        val path = Path()
        path.moveTo(cx + u(4.5f), cy - boltH * 0.5f)
        path.lineTo(cx - u(9f), cy + u(3.5f))
        path.lineTo(cx - u(1f), cy + u(3.5f))
        path.lineTo(cx - u(4.5f), cy + boltH * 0.5f)
        path.lineTo(cx + u(9f), cy - u(3.5f))
        path.lineTo(cx + u(1f), cy - u(3.5f))
        path.close()
        fill.color = ColorUtils.blendARGB(onAccent, Color.WHITE, 0.55f)
        canvas.drawPath(path, fill)
        stroke.color = ColorUtils.blendARGB(accent, Color.BLACK, 0.28f)
        stroke.strokeWidth = u(1.1f)
        canvas.drawPath(path, stroke)
    }

    // The crystals and flame helpers that stood here until
    // v0.3.22 were removed in v0.3.23, with their drawings. The
    // geometric heat helpers of v0.3.27 follow — a different
    // drawing in a different language (see the class doc).

    // Heat-expression colours (character-expression only, the
    // same values as the live face's; recorded in ART_DIRECTION).
    private val FLAME_PALE = Color.rgb(0xF2, 0x99, 0x7A)
    private val FLAME_DEEP = Color.rgb(0xD4, 0x2A, 0x1E)
    private val FLAME_INNER_PALE = Color.rgb(0xFF, 0xC9, 0x8A)
    private val FLAME_INNER_DEEP = Color.rgb(0xF0, 0x76, 0x2B)
    private val FLAME_CORE = Color.rgb(0xFF, 0xE3, 0xAE)
    private val OBJ_LINE = Color.rgb(0x6E, 0x8C, 0xA0)
    private val OBJ_BLUE = Color.rgb(0x9C, 0xC8, 0xE8)
    private val OBJ_ICE = Color.rgb(0xD8, 0xF0, 0xFA)
    private val OBJ_PEACH = Color.rgb(0xF8, 0xC4, 0x71)
    private val OBJ_WHITE = Color.rgb(0xFF, 0xFF, 0xFF)
    private val OBJ_PINK = Color.rgb(0xF1, 0x94, 0xA0)
    private val OBJ_DARK = Color.rgb(0x45, 0x5A, 0x64)

    // Flame anchors around the body, in the live face's order
    // and fractions; counts per flame level 0…3.
    private val FLAME_ANCHORS = arrayOf(
        floatArrayOf(0.16f, 0.0f, 1.0f),
        floatArrayOf(0.84f, 0.0f, 1.0f),
        floatArrayOf(-0.04f, 0.32f, 0.85f),
        floatArrayOf(1.04f, 0.32f, 0.85f),
        floatArrayOf(0.50f, -0.03f, 1.15f),
        floatArrayOf(-0.05f, 0.66f, 0.75f),
        floatArrayOf(1.05f, 0.66f, 0.75f),
    )
    private val FLAME_COUNTS = intArrayOf(0, 3, 5, 7)
    private val FLAME_SCALES = floatArrayOf(0f, 0.55f, 0.8f, 1.05f)

    /** Geometric flames, frozen upright (a still frame has no sway). */
    private fun drawFlames(canvas: Canvas, fill: Paint, body: RectF, level: Int, heat: Float) {
        val count = FLAME_COUNTS[level.coerceIn(0, 3)]
        if (count == 0) return
        val levelScale = FLAME_SCALES[level.coerceIn(0, 3)]
        val outer = ColorUtils.blendARGB(FLAME_PALE, FLAME_DEEP, heat)
        val inner = ColorUtils.blendARGB(FLAME_INNER_PALE, FLAME_INNER_DEEP, heat)
        val unit = body.width() * 0.26f
        for (i in 0 until count) {
            val a = FLAME_ANCHORS[i]
            val bx = body.left + body.width() * a[0]
            val by = body.top + body.height() * a[1]
            val h = unit * a[2] * levelScale
            val w = h * 0.62f
            fill.color = outer
            canvas.drawPath(flamePath(bx, by, w, h), fill)
            fill.color = inner
            canvas.drawPath(flamePath(bx, by, w * 0.55f, h * 0.58f), fill)
            fill.color = FLAME_CORE
            canvas.drawPath(flamePath(bx, by, w * 0.26f, h * 0.30f), fill)
        }
    }

    /** One geometric flame: a teardrop silhouette standing on (bx, by). */
    private fun flamePath(bx: Float, by: Float, w: Float, h: Float): Path {
        val path = Path()
        path.moveTo(bx - w / 2f, by)
        path.cubicTo(bx - w / 2f, by - h * 0.45f, bx - w * 0.18f, by - h * 0.62f, bx, by - h)
        path.cubicTo(bx + w * 0.30f, by - h * 0.60f, bx + w / 2f, by - h * 0.42f, bx + w / 2f, by)
        path.quadTo(bx, by - h * 0.10f, bx - w / 2f, by)
        path.close()
        return path
    }

    private fun drawCoolObjects(
        canvas: Canvas, fill: Paint, stroke: Paint, body: RectF, scene: HeatScene.Scene,
    ) {
        val s = body.width() * 0.26f
        stroke.strokeWidth = s * 0.045f
        for (obj in HeatScene.CoolObject.values()) {
            if (obj !in scene.objects) continue
            val (fx, fy) = objectAnchor(obj)
            drawCoolObject(
                canvas, fill, stroke, obj,
                body.left + body.width() * fx, body.top + body.height() * fy, s,
            )
        }
    }

    /** Each object's fixed seat around the body (the live face's ring). */
    private fun objectAnchor(obj: HeatScene.CoolObject): Pair<Float, Float> = when (obj) {
        HeatScene.CoolObject.PARASOL -> -0.19f to 0.06f
        HeatScene.CoolObject.AIRCON -> 1.19f to 0.04f
        HeatScene.CoolObject.WINDBELL -> -0.19f to 0.34f
        HeatScene.CoolObject.FAN -> 1.19f to 0.40f
        HeatScene.CoolObject.ICE -> -0.19f to 0.68f
        HeatScene.CoolObject.SHAVED_ICE -> 1.19f to 0.72f
        HeatScene.CoolObject.SNOWMAN -> -0.18f to 0.94f
    }

    /** One cooling object, centred at (cx, cy) in a box ~s wide —
     *  the live face's geometric shapes, simplified for the still. */
    private fun drawCoolObject(
        canvas: Canvas, fill: Paint, stroke: Paint,
        obj: HeatScene.CoolObject, cx: Float, cy: Float, s: Float,
    ) {
        stroke.color = OBJ_LINE
        when (obj) {
            HeatScene.CoolObject.PARASOL -> {
                canvas.save()
                canvas.rotate(-14f, cx, cy)
                val rect = RectF(cx - s * 0.55f, cy - s * 0.38f, cx + s * 0.55f, cy + s * 0.38f)
                val canopy = Path().apply { arcTo(rect, 180f, 180f); close() }
                fill.color = OBJ_PEACH
                canvas.drawPath(canopy, fill)
                canvas.drawPath(canopy, stroke)
                canvas.drawLine(cx, cy - s * 0.30f, cx - s * 0.12f, cy + s * 0.52f, stroke)
                canvas.restore()
            }
            HeatScene.CoolObject.AIRCON -> {
                val rect = RectF(cx - s * 0.52f, cy - s * 0.27f, cx + s * 0.52f, cy + s * 0.27f)
                fill.color = OBJ_WHITE
                canvas.drawRoundRect(rect, s * 0.08f, s * 0.08f, fill)
                canvas.drawRoundRect(rect, s * 0.08f, s * 0.08f, stroke)
                stroke.color = OBJ_BLUE
                canvas.drawLine(cx - s * 0.38f, cy + s * 0.05f, cx + s * 0.38f, cy + s * 0.05f, stroke)
                canvas.drawLine(cx - s * 0.38f, cy + s * 0.17f, cx + s * 0.38f, cy + s * 0.17f, stroke)
            }
            HeatScene.CoolObject.WINDBELL -> {
                canvas.drawLine(cx, cy - s * 0.52f, cx, cy - s * 0.18f, stroke)
                val rect = RectF(cx - s * 0.27f, cy - s * 0.20f, cx + s * 0.27f, cy + s * 0.34f)
                val dome = Path().apply { arcTo(rect, 180f, 180f); close() }
                fill.color = OBJ_ICE
                canvas.drawPath(dome, fill)
                canvas.drawPath(dome, stroke)
                val strip = RectF(cx - s * 0.08f, cy + s * 0.10f, cx + s * 0.08f, cy + s * 0.46f)
                fill.color = OBJ_PEACH
                canvas.drawRect(strip, fill)
                canvas.drawRect(strip, stroke)
            }
            HeatScene.CoolObject.FAN -> {
                val headY = cy - s * 0.10f
                val headR = s * 0.34f
                canvas.drawLine(cx, headY + headR * 0.6f, cx, cy + s * 0.42f, stroke)
                canvas.drawLine(cx - s * 0.24f, cy + s * 0.46f, cx + s * 0.24f, cy + s * 0.46f, stroke)
                fill.color = OBJ_ICE
                canvas.drawCircle(cx, headY, headR, fill)
                // Blades at rest (a still frame does not turn them).
                fill.color = OBJ_BLUE
                for (i in 0..2) {
                    canvas.save()
                    canvas.translate(cx, headY)
                    canvas.rotate(30f + i * 120f)
                    val blade = RectF(-headR * 0.30f, -headR * 0.92f, headR * 0.30f, -headR * 0.12f)
                    canvas.drawOval(blade, fill)
                    canvas.restore()
                }
                fill.color = OBJ_WHITE
                canvas.drawCircle(cx, headY, headR * 0.16f, fill)
                canvas.drawCircle(cx, headY, headR, stroke)
            }
            HeatScene.CoolObject.ICE -> {
                val diamond = Path().apply {
                    moveTo(cx, cy - s * 0.42f)
                    lineTo(cx + s * 0.36f, cy)
                    lineTo(cx, cy + s * 0.42f)
                    lineTo(cx - s * 0.36f, cy)
                    close()
                }
                fill.color = OBJ_ICE
                canvas.drawPath(diamond, fill)
                canvas.drawPath(diamond, stroke)
                canvas.drawLine(cx, cy - s * 0.42f, cx, cy + s * 0.08f, stroke)
                canvas.drawLine(cx, cy + s * 0.08f, cx + s * 0.36f, cy, stroke)
            }
            HeatScene.CoolObject.SHAVED_ICE -> {
                val rect = RectF(cx - s * 0.38f, cy - s * 0.34f, cx + s * 0.38f, cy + s * 0.42f)
                val dome = Path().apply { arcTo(rect, 180f, 180f); close() }
                fill.color = OBJ_WHITE
                canvas.drawPath(dome, fill)
                canvas.drawPath(dome, stroke)
                fill.color = OBJ_PINK
                canvas.drawCircle(cx, cy - s * 0.30f, s * 0.07f, fill)
                val bowl = Path().apply {
                    moveTo(cx - s * 0.44f, cy + s * 0.06f)
                    lineTo(cx + s * 0.44f, cy + s * 0.06f)
                    lineTo(cx + s * 0.27f, cy + s * 0.40f)
                    lineTo(cx - s * 0.27f, cy + s * 0.40f)
                    close()
                }
                fill.color = OBJ_BLUE
                canvas.drawPath(bowl, fill)
                canvas.drawPath(bowl, stroke)
            }
            HeatScene.CoolObject.SNOWMAN -> {
                fill.color = OBJ_WHITE
                canvas.drawCircle(cx, cy + s * 0.18f, s * 0.30f, fill)
                canvas.drawCircle(cx, cy + s * 0.18f, s * 0.30f, stroke)
                canvas.drawCircle(cx, cy - s * 0.24f, s * 0.20f, fill)
                canvas.drawCircle(cx, cy - s * 0.24f, s * 0.20f, stroke)
                fill.color = OBJ_DARK
                canvas.drawCircle(cx - s * 0.07f, cy - s * 0.27f, s * 0.03f, fill)
                canvas.drawCircle(cx + s * 0.07f, cy - s * 0.27f, s * 0.03f, fill)
                canvas.drawCircle(cx, cy + s * 0.10f, s * 0.03f, fill)
                canvas.drawCircle(cx, cy + s * 0.26f, s * 0.03f, fill)
                fill.color = OBJ_PEACH
                canvas.drawCircle(cx + s * 0.04f, cy - s * 0.20f, s * 0.035f, fill)
            }
        }
    }

    private fun frac(v: Float): Float = v - kotlin.math.floor(v)
}
