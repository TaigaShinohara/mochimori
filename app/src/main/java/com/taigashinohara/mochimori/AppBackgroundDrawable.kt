package com.taigashinohara.mochimori

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.Shader
import androidx.core.graphics.ColorUtils

/**
 * The shared Simple Glass foundation (土台), adopted v0.3.8: one
 * background for every screen, never drawn per screen.
 *
 * Per the skill's standard template (quality.md §1): a quiet 3-stop
 * gradient (paper → the lead colour's tint → a second tint, adjacent
 * stops kept close in hue and low in saturation) with three soft
 * lights (blobs) laid over it — the lead colour top-left, a support
 * colour bottom-right, a third at bottom-centre. The lights use the
 * role colours' pale tints, never the raw role colours, and are drawn
 * as radial gradients fading to transparent: on the View system there
 * is no backdrop blur for in-window content, so the radial falloff IS
 * the blur (60–80dp of softness is approximated by the gradient's
 * radius). The glass surfaces above this foundation pick their colour
 * up from this light through their translucency.
 *
 * Colours are resolved from the app's fixed palette by the caller
 * (Dynamic Color no longer flows in anywhere — v0.3.9, see
 * GLASS_REVIEW_v0.3.8.md), and the same drawable serves light and
 * dark — the token values differ, the recipe doesn't.
 *
 * v0.3.9 simplification (user: "見づらい／もっとシンプルに"): the
 * lights are much fainter than the v0.3.8 pass (alpha 165 → 76) and
 * the third light moved from bottom-centre — where it sat directly
 * behind the bottom navigation and washed its labels out — to the
 * centre of the screen, behind the battery face, where a soft halo
 * belongs. The foundation is now felt as a calm, nearly-flat field
 * with a hint of light, not as a coloured gradient.
 */
class AppBackgroundDrawable(
    private val paper: Int,
    private val tintA: Int,
    private val tintB: Int,
    private val blobA: Int,
    private val blobB: Int,
    private val blobC: Int,
    private val density: Float,
) : android.graphics.drawable.Drawable() {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private var baseShader: Shader? = null
    private var blobShaders: Array<Shader?> = arrayOfNulls(3)
    private var blobCenters: Array<FloatArray> = emptyArray()
    private var blobRadii: FloatArray = FloatArray(0)

    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)
        val w = bounds.width().toFloat()
        val h = bounds.height().toFloat()
        if (w <= 0f || h <= 0f) return
        // Diagonal 3-stop gradient: paper at the top, tints below.
        baseShader = LinearGradient(
            0f, 0f, w * 0.35f, h,
            intArrayOf(paper, tintA, tintB),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP,
        )
        val r = 170f * density // ~340dp diameter, inside the 280–360dp band
        // Positions: lead top-left, support bottom-right (both pushed
        // partly off-screen), third at screen centre behind the face —
        // NOT bottom-centre any more (v0.3.9: it washed out the bottom
        // navigation's labels on the real device).
        blobCenters = arrayOf(
            floatArrayOf(w * 0.06f, h * 0.05f),
            floatArrayOf(w * 0.97f, h * 0.96f),
            floatArrayOf(w * 0.50f, h * 0.40f),
        )
        blobRadii = floatArrayOf(r, r * 1.05f, r * 0.85f)
        val colors = intArrayOf(blobA, blobB, blobC)
        for (i in 0 until 3) {
            blobShaders[i] = RadialGradient(
                blobCenters[i][0], blobCenters[i][1], blobRadii[i],
                ColorUtils.setAlphaComponent(colors[i], 76),
                ColorUtils.setAlphaComponent(colors[i], 0),
                Shader.TileMode.CLAMP,
            )
        }
    }

    override fun draw(canvas: Canvas) {
        val b = bounds
        if (b.isEmpty) return
        paint.shader = baseShader
        canvas.drawRect(b, paint)
        for (i in 0 until 3) {
            val s = blobShaders.getOrNull(i) ?: continue
            paint.shader = s
            canvas.drawCircle(blobCenters[i][0], blobCenters[i][1], blobRadii[i], paint)
        }
        paint.shader = null
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
    }

    @Deprecated("Drawable opacity contract")
    override fun getOpacity(): Int = PixelFormat.OPAQUE
}
