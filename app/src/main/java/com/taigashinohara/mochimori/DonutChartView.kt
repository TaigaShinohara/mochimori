package com.taigashinohara.mochimori

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

/**
 * Donut chart for the saving checklist's score (v0.3.16, user
 * request: the score as a percent with a circular chart).
 *
 * A quiet ring: a full track circle in the track colour, and the
 * judged-OK share as an arc in the progress colour starting at the
 * top. The percent number itself is a TextView the caller overlays
 * in the middle, so text keeps the theme's type scale and TalkBack
 * order; this view is pure drawing and carries no text.
 *
 * A null percent draws the track only — the checklist could not
 * judge anything, and the ring must not fake a score.
 * Colours are theme roles resolved by the caller (the progress arc
 * takes the cool state role, the track the surface-variant role);
 * this view holds no colour literals of its own beyond fallbacks.
 */
class DonutChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    /** 0..100, or null when nothing could be judged. */
    var percent: Int? = null
        set(value) {
            field = value
            invalidate()
        }

    var progressColor: Int = Color.rgb(2, 119, 189)
        set(value) {
            field = value
            invalidate()
        }

    var trackColor: Int = Color.LTGRAY
        set(value) {
            field = value
            invalidate()
        }

    private val strokeWidthPx = 10f * resources.displayMetrics.density

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = strokeWidthPx
    }

    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = strokeWidthPx
        strokeCap = Paint.Cap.ROUND
    }

    private val bounds = RectF()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val inset = strokeWidthPx / 2f + 1f
        bounds.set(
            paddingLeft + inset,
            paddingTop + inset,
            width - paddingRight - inset,
            height - paddingBottom - inset,
        )
        if (bounds.width() <= 0f || bounds.height() <= 0f) return

        trackPaint.color = trackColor
        canvas.drawOval(bounds, trackPaint)

        val p = percent ?: return
        if (p <= 0) return
        progressPaint.color = progressColor
        // A full 100% with a round cap would double-draw the cap at
        // the seam; a hair under 360 keeps the ring closed and calm.
        val sweep = minOf(p, 100) * 3.6f
        canvas.drawArc(bounds, -90f, minOf(sweep, 359.9f), false, progressPaint)
    }
}
