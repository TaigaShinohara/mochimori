package com.taigashinohara.mochimori

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Shader
import androidx.core.graphics.ColorUtils

/**
 * Simple Glass surface for the View system (adopted v0.3.8, see
 * docs/GLASS_REVIEW_v0.3.7.md and docs/ART_DIRECTION.md).
 *
 * The platform has no per-view backdrop blur for in-window Views
 * (RenderEffect blurs a view's own rendering, not what is behind it;
 * window-level blur-behind exists only for windows/dialogs), so the
 * glass is the skill's documented fallback: a translucent fill with a
 * gentle top-to-bottom gradient (same hue, ~8pt alpha step) over the
 * shared gradient-and-light background, plus the specular edge — a 1dp
 * stroke whose shader runs bright at the top (white 75% light / 38%
 * dark) to faint at the bottom (15% / 6%). What shows through the alpha
 * is the app background's light, which is where the colour comes from:
 * surfaces are not painted with colour, the light behind them is.
 *
 * Two families, per the skill's surface table:
 *  - neutral "reading glass" (text-bearing surfaces): white at
 *    62%→54% alpha (light) / 19%→13% (dark).
 *  - role-tint glass (stage cards): the role's container colour kept
 *    nearly opaque (94%→88%) so the state colour and its on-container
 *    text keep their contrast; only a hint of the background light
 *    passes through.
 *
 * v0.3.10 (glass audit, docs/GLASS_REVIEW_v0.3.9.md): the DARK reading
 * glass now carries the skill's sanctioned "paper veil" (紙色のベール)
 * under the white gradient — the theme's surface colour at alpha 180.
 * Reason: with the fixed palette, the foundation's brightest dark
 * light (primaryContainer #004B73 at full strength behind a card)
 * plus white 19% glass put the auxiliary text at ≈3.8:1 in the worst
 * case, under the 4.5:1 gate; more white would only lighten the
 * background further, so the veil pulls it back down (worst case
 * becomes ≈4.7:1 at the glass's top edge, ≈5.7:1 at its bottom). The
 * light scheme needs no veil (worst case ≈7.2:1 without one).
 */
class GlassDrawable(
    private val baseColor: Int,
    private val topAlpha: Int,
    private val bottomAlpha: Int,
    private val radiusPx: Float,
    private val night: Boolean,
    private val veilColor: Int = 0,
    private val veilAlpha: Int = 0,
) : android.graphics.drawable.Drawable() {

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val veilPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val rect = RectF()
    private var fillShader: Shader? = null
    private var edgeShader: Shader? = null

    override fun onBoundsChange(bounds: android.graphics.Rect) {
        super.onBoundsChange(bounds)
        rect.set(bounds)
        val top = ColorUtils.setAlphaComponent(baseColor, topAlpha)
        val bottom = ColorUtils.setAlphaComponent(baseColor, bottomAlpha)
        fillShader = LinearGradient(
            rect.left, rect.top, rect.left, rect.bottom,
            top, bottom, Shader.TileMode.CLAMP,
        )
        val edgeTop = if (night) 97 else 191 // white 38% / 75%
        val edgeBottom = if (night) 15 else 38 // white 6% / 15%
        edgeShader = LinearGradient(
            rect.left, rect.top, rect.left, rect.bottom,
            ColorUtils.setAlphaComponent(android.graphics.Color.WHITE, edgeTop),
            ColorUtils.setAlphaComponent(android.graphics.Color.WHITE, edgeBottom),
            Shader.TileMode.CLAMP,
        )
    }

    override fun draw(canvas: Canvas) {
        if (rect.isEmpty) return
        if (veilAlpha > 0) {
            // Paper veil first (flat, no gradient): it caps how bright
            // the background behind the glass can get, in either theme.
            veilPaint.color = ColorUtils.setAlphaComponent(veilColor, veilAlpha)
            canvas.drawRoundRect(rect, radiusPx, radiusPx, veilPaint)
        }
        fillPaint.shader = fillShader
        canvas.drawRoundRect(rect, radiusPx, radiusPx, fillPaint)
        fillPaint.shader = null
        // Specular edge: inset by half the stroke so it stays inside.
        val inset = edgePaint.strokeWidth / 2f
        val edge = RectF(rect).apply { inset(inset, inset) }
        edgePaint.shader = edgeShader
        canvas.drawRoundRect(edge, (radiusPx - inset).coerceAtLeast(0f), (radiusPx - inset).coerceAtLeast(0f), edgePaint)
        edgePaint.shader = null
    }

    override fun getOutline(outline: Outline) {
        outline.setRoundRect(bounds, radiusPx)
    }

    override fun setAlpha(alpha: Int) {
        fillPaint.alpha = alpha
        edgePaint.alpha = alpha
        veilPaint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        fillPaint.colorFilter = colorFilter
        edgePaint.colorFilter = colorFilter
        veilPaint.colorFilter = colorFilter
    }

    @Deprecated("Drawable opacity contract")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    fun setEdgeWidthPx(px: Float) {
        edgePaint.strokeWidth = px
    }

    companion object {
        /**
         * Reading glass for text-bearing neutral surfaces (cards).
         * In the dark scheme the caller passes the theme's surface
         * colour for the paper veil (v0.3.10, see class doc).
         */
        fun reading(radiusPx: Float, night: Boolean, edgePx: Float, surfaceColor: Int): GlassDrawable =
            if (night) {
                GlassDrawable(
                    android.graphics.Color.WHITE, 48, 33, radiusPx, true,
                    veilColor = surfaceColor, veilAlpha = 180,
                )
            } else {
                GlassDrawable(android.graphics.Color.WHITE, 158, 138, radiusPx, false)
            }.apply { setEdgeWidthPx(edgePx) }

        /** Role-tint glass: the container colour, nearly opaque. */
        fun tint(baseColor: Int, radiusPx: Float, night: Boolean, edgePx: Float): GlassDrawable =
            GlassDrawable(baseColor, 240, 224, radiusPx, night).apply { setEdgeWidthPx(edgePx) }

        /**
         * Decorative glass （飾りガラス）: no text ever sits on it, so it
         * takes the skill's thinnest surface — white 44%→36% (light) /
         * 9%→5% (dark) — with the same specular edge as every other
         * glass surface. Used for the plate behind the home face
         * (layout plan A, v0.3.11): the plate only has to read as the
         * same glass family as the vessel and the cards, never to carry
         * information itself.
         */
        fun decorative(radiusPx: Float, night: Boolean, edgePx: Float): GlassDrawable =
            if (night) {
                GlassDrawable(android.graphics.Color.WHITE, 23, 13, radiusPx, true)
            } else {
                GlassDrawable(android.graphics.Color.WHITE, 112, 92, radiusPx, false)
            }.apply { setEdgeWidthPx(edgePx) }

        /**
         * Pill chip for the home screen's "数値を見る" toggle (layout
         * plan A, v0.3.11). The surface is the reading-glass recipe
         * (white 62%→54% light / 19%→13% dark), and in the dark scheme
         * the paper veil is stronger than the card recipe's (alpha 230
         * instead of 180): the chip floats directly under the face,
         * inside the foundation's centre light, which is the brightest
         * backdrop any small text in the app sits on. Static worst-case
         * composites over that whole region (gradient + all three
         * lights, both glass edges) put the secondary label at
         * ≈5.8:1 (light) / ≈5.2:1 (dark) — above the 4.5:1 gate with
         * margin, where the plain card recipe measured only ≈4.6:1 in
         * the dark scheme. The light scheme needs no veil (≈7:1+
         * without one on the reading glass).
         */
        fun chip(radiusPx: Float, night: Boolean, edgePx: Float, surfaceColor: Int): GlassDrawable =
            if (night) {
                GlassDrawable(
                    android.graphics.Color.WHITE, 48, 33, radiusPx, true,
                    veilColor = surfaceColor, veilAlpha = 230,
                )
            } else {
                GlassDrawable(android.graphics.Color.WHITE, 158, 138, radiusPx, false)
            }.apply { setEdgeWidthPx(edgePx) }

        /**
         * Band for the toolbar / navigation bar (square corners).
         *
         * v0.3.9: this is NO LONGER reading glass. On the real device
         * the reading-glass band (white 19%→13% in dark) sat over the
         * foundation's bright bottom light and the navigation labels
         * washed out to near-invisibility — the skill's own rule says
         * a surface that must read regardless of what is behind it
         * gets thickness ("重ね面" class). The band therefore uses the
         * theme's surface colour at overlap-surface weight: the
         * specular edge stays, the light behind no longer decides
         * whether text reads.
         *
         * v0.3.10: the light scheme's bottom edge goes 88% → 94%
         * (alphas 235→224 become 245→240). The audit's worst-case
         * composite put the selected tab's primary label at ≈4.46:1
         * on the old bottom edge — a hair under the 4.5:1 gate; at
         * 94% it measures ≈4.5:1 even under the foundation's
         * strongest light, and higher everywhere else. The dark
         * scheme (95%→90%) already measured ≈8.9:1 and is unchanged.
         */
        fun band(baseColor: Int, night: Boolean, edgePx: Float): GlassDrawable =
            if (night) {
                GlassDrawable(baseColor, 242, 230, 0f, true)
            } else {
                GlassDrawable(baseColor, 245, 240, 0f, false)
            }.apply { setEdgeWidthPx(edgePx) }
    }
}
