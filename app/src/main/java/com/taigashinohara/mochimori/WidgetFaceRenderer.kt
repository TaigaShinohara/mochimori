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
import kotlin.math.cos
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
 *  - flames stand upright (phase 0), crystals sit where the live face
 *    puts them, the bolt appears only while charging,
 *  - bubbles are a few fixed seeds inside the liquid.
 * State comes from [BatteryJudge] via the caller — this renderer never
 * judges anything itself.
 *
 * v0.3.21: the still follows the live face's M3 flattening — solid
 * shell and outline, no aura, ground shadow, gloss, rim, cavity
 * shading, bolt glow or flake halo (see BatteryFaceView's doc).
 */
object WidgetFaceRenderer {

    private enum class Kind { SAVING, HOT, CHARGING, UNKNOWN }

    // Virtual canvas the face is composed in; the bitmap is this space
    // scaled up. Proportions mirror BatteryFaceView.onSizeChanged.
    private const val VW = 150f
    private const val VH = 190f

    fun render(context: Context, state: BatteryJudge.State, levelPercent: Int): Bitmap {
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
        draw(canvas, context, kind, levelPercent.coerceIn(0, 100))
        return bitmap
    }

    private fun draw(canvas: Canvas, context: Context, kind: Kind, level: Int) {
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

        val (accent, container, onAccent) = when (kind) {
            Kind.SAVING, Kind.CHARGING -> Triple(cool, coolFill, onCool)
            Kind.HOT -> Triple(hot, hotFill, onHot)
            Kind.UNKNOWN -> Triple(neutral, neutralFill, neutralFill)
        }
        val cavity = ColorUtils.blendARGB(cavityBase, accent, 0.10f)
        val hotOuter = ColorUtils.blendARGB(hot, Color.rgb(0xBF, 0x36, 0x00), 0.45f)
        val hotInner = ColorUtils.blendARGB(hot, Color.WHITE, 0.35f)
        val hotCore = ColorUtils.blendARGB(hot, Color.rgb(0xFF, 0xF3, 0xB0), 0.75f)

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

        // ---- Flames behind the body (hot face, frozen at phase 0) ----
        if (kind == Kind.HOT) {
            val baseY = body.top + u(7f)
            val maxH = (baseY - u(6f)).coerceAtLeast(u(20f))
            fun fh(frac: Float) = min(maxH, maxH * frac)
            // The bed: one wide, low flame mound the others rise from.
            flameLayer(canvas, fill, body.centerX(), baseY + u(4f), bodyW * 0.46f, fh(0.36f), 0f, hotOuter)
            // Five standing flames, centre highest — same envelope and
            // per-flame phases as the live face, frozen.
            flameFull(canvas, fill, body.centerX() - bodyW * 0.36f, baseY + u(5f), bodyW * 0.105f, fh(0.55f), 4.1f, hotOuter, hot, hotInner, hotCore)
            flameFull(canvas, fill, body.centerX() + bodyW * 0.36f, baseY + u(5f), bodyW * 0.105f, fh(0.55f), 1.3f, hotOuter, hot, hotInner, hotCore)
            flameFull(canvas, fill, body.centerX() - bodyW * 0.185f, baseY + u(3f), bodyW * 0.125f, fh(0.78f), 2.4f, hotOuter, hot, hotInner, hotCore)
            flameFull(canvas, fill, body.centerX() + bodyW * 0.185f, baseY + u(3f), bodyW * 0.125f, fh(0.78f), 5.3f, hotOuter, hot, hotInner, hotCore)
            flameFull(canvas, fill, body.centerX(), baseY, bodyW * 0.145f, fh(1.0f), 0f, hotOuter, hot, hotInner, hotCore)
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

        // ---- State marks ----
        when (kind) {
            Kind.SAVING -> crystals(canvas, stroke, body, accent, u)
            Kind.CHARGING -> {
                crystals(canvas, stroke, body, accent, u)
                bolt(canvas, fill, stroke, body, accent, onAccent, u)
            }
            else -> Unit
        }
    }

    /** Snowflakes balanced around the body — same layout as the live face. */
    private fun crystals(canvas: Canvas, paint: Paint, body: RectF, color: Int, u: (Float) -> Float) {
        val l = body.left; val t = body.top
        val bw = body.width(); val bh = body.height()
        snowflake(canvas, paint, l - bw * 0.16f, t + bh * 0.02f, u(13f), color, 235)
        snowflake(canvas, paint, l + bw * 1.16f, t + bh * 0.02f, u(11f), color, 235)
        snowflake(canvas, paint, l - bw * 0.20f, t + bh * 0.42f, u(8.5f), color, 200)
        snowflake(canvas, paint, l + bw * 1.20f, t + bh * 0.55f, u(7.5f), color, 200)
        snowflake(canvas, paint, l + bw * 0.26f, t + bh * 0.22f, u(7f), color, 110)
        snowflake(canvas, paint, l + bw * 0.74f, t + bh * 0.68f, u(6f), color, 110)
    }

    private fun snowflake(canvas: Canvas, paint: Paint, cx: Float, cy: Float, r: Float, color: Int, alpha: Int) {
        val path = Path()
        val branches = floatArrayOf(0.52f, 0.34f, 0.78f, 0.24f)
        for (i in 0 until 6) {
            val a = Math.toRadians((i * 60 - 90).toDouble())
            val ux = cos(a).toFloat(); val uy = sin(a).toFloat()
            path.moveTo(cx, cy)
            path.lineTo(cx + ux * r, cy + uy * r)
            for (b in branches.indices step 2) {
                val at = branches[b]; val lenF = branches[b + 1]
                val bx = cx + ux * r * at; val by = cy + uy * r * at
                for (s in -1..1 step 2) {
                    val ba = a + s * Math.toRadians(42.0)
                    path.moveTo(bx, by)
                    path.lineTo(bx + cos(ba).toFloat() * r * lenF, by + sin(ba).toFloat() * r * lenF)
                }
            }
        }
        // Single pass (the halo under-stroke was removed in v0.3.21).
        paint.color = ColorUtils.setAlphaComponent(color, alpha)
        paint.strokeWidth = r * 0.13f
        canvas.drawPath(path, paint)
        canvas.drawCircle(cx, cy, r * 0.16f, paint)
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

    /** One standing flame in four layers, frozen at phase [ph]. */
    private fun flameFull(
        canvas: Canvas, paint: Paint, baseCx: Float, baseY: Float,
        halfW: Float, height: Float, ph: Float,
        outer: Int, mid: Int, inner: Int, core: Int,
    ) {
        val grow = 1f + sin(ph * 2).toFloat() * 0.07f
        flameLayer(canvas, paint, baseCx, baseY, halfW, height * grow, ph, outer)
        flameLayer(canvas, paint, baseCx, baseY, halfW * 0.70f, height * 0.70f * grow, ph + 0.9f, mid)
        flameLayer(canvas, paint, baseCx, baseY, halfW * 0.45f, height * 0.46f * grow, ph + 1.7f, inner)
        flameLayer(canvas, paint, baseCx, baseY, halfW * 0.25f, height * 0.27f * grow, ph + 2.3f, core)
    }

    private fun flameLayer(
        canvas: Canvas, paint: Paint, baseCx: Float, baseY: Float,
        halfW: Float, height: Float, ph: Float, color: Int,
    ) {
        val bend = sin(ph.toDouble()).toFloat() * halfW * 0.38f
        val wobble = sin(ph * 2 + 1.3).toFloat() * halfW * 0.13f
        val path = Path()
        path.moveTo(baseCx - halfW, baseY)
        path.cubicTo(
            baseCx - halfW * 1.08f, baseY - height * 0.36f,
            baseCx - halfW * 0.40f + wobble, baseY - height * 0.60f,
            baseCx + bend, baseY - height,
        )
        path.cubicTo(
            baseCx + halfW * 0.46f + wobble * 0.6f, baseY - height * 0.58f,
            baseCx + halfW * 1.08f, baseY - height * 0.36f,
            baseCx + halfW, baseY,
        )
        path.cubicTo(
            baseCx + halfW * 0.42f, baseY - height * 0.10f,
            baseCx - halfW * 0.42f, baseY - height * 0.10f,
            baseCx - halfW, baseY,
        )
        path.close()
        paint.color = color
        canvas.drawPath(path, paint)
    }

    private fun frac(v: Float): Float = v - kotlin.math.floor(v)
}
