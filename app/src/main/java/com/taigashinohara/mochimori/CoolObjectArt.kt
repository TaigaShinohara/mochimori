package com.taigashinohara.mochimori

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF

/**
 * The cooling objects as generated image assets (v0.3.28).
 *
 * Until v0.3.27 the seven objects were drawn with plain Canvas
 * shapes in both [BatteryFaceView] and [WidgetFaceRenderer]. At
 * the user's instruction ("それぞれ画像として生成して使おう")
 * each object is now an individually AI-generated illustration —
 * the same flat geometric vector language and pale pastel palette
 * as the approved v4 mock-up board (see docs/ART_DIRECTION.md for
 * the generation prompts and the white→alpha post-processing
 * recipe, so future objects can be made to match).
 *
 * The assets live in drawable-nodpi as 512×512 transparent WebP
 * (quality 88); the object is centred in the frame with its
 * longest side at 96% of the canvas, so drawing the bitmap into
 * an s×s box centred on the object's seat reproduces the old
 * shapes' footprint. Which objects appear is still decided by
 * [HeatScene] alone — this object only paints.
 *
 * The fan's slow blade rotation retired with the Canvas shapes:
 * as a still image the fan simply stands there, on the home
 * screen as much as in the widget.
 */
object CoolObjectArt {

    private val RES_FOR: Map<HeatScene.CoolObject, Int> = mapOf(
        HeatScene.CoolObject.PARASOL to R.drawable.coolobj_parasol,
        HeatScene.CoolObject.WINDBELL to R.drawable.coolobj_windbell,
        HeatScene.CoolObject.ICE to R.drawable.coolobj_ice,
        HeatScene.CoolObject.AIRCON to R.drawable.coolobj_aircon,
        HeatScene.CoolObject.FAN to R.drawable.coolobj_fan,
        HeatScene.CoolObject.SHAVED_ICE to R.drawable.coolobj_shaved_ice,
        HeatScene.CoolObject.SNOWMAN to R.drawable.coolobj_snowman,
    )

    private val cache = HashMap<HeatScene.CoolObject, Bitmap>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val dst = RectF()

    /** Draws [obj] centred at (cx, cy) in a box s wide and s tall. */
    fun draw(canvas: Canvas, resources: Resources, obj: HeatScene.CoolObject, cx: Float, cy: Float, s: Float) {
        val bmp = bitmap(resources, obj) ?: return
        dst.set(cx - s / 2f, cy - s / 2f, cx + s / 2f, cy + s / 2f)
        canvas.drawBitmap(bmp, null, dst, paint)
    }

    private fun bitmap(resources: Resources, obj: HeatScene.CoolObject): Bitmap? {
        // The live face draws on the UI thread and the widget on a
        // background thread; guard the lazy decode.
        synchronized(cache) {
            cache[obj]?.let { return it }
            val res = RES_FOR[obj] ?: return null
            val bmp = BitmapFactory.decodeResource(resources, res) ?: return null
            cache[obj] = bmp
            return bmp
        }
    }
}
