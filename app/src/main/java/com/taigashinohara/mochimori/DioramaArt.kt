package com.taigashinohara.mochimori

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF

/**
 * The diorama parts as generated image assets (v0.4.0).
 *
 * The face's whole cast is now individually AI-generated
 * miniature-diorama photographs, cut out to transparency and
 * stored in drawable-nodpi as transparent WebP (lossy q88 —
 * the v0.3.28 recipe; 32 parts, 835,392 bytes in total). The
 * parts were generated as sheets, cut out with the same
 * white→alpha post-processing as the cooling objects of
 * v0.3.28, and tested in full compositions before the user
 * approved the integration design (see
 * proposals/diorama-integration-design.md and
 * docs/ART_DIRECTION.md).
 *
 * Two parts needed extra work in the pipeline:
 *  - the body's window is a REAL hole in the photograph: the
 *    liquid (still code-drawn) is laid behind the body and
 *    shows through it. The hole's measured position inside
 *    the body image is [WIN_L]…[WIN_B] below.
 *  - v0.4.1: the body photograph was regenerated on a pure
 *    green backdrop and chroma-keyed (green-dominance alpha
 *    with a smooth ramp + despill), because the v0.4.0
 *    white-cut body's silhouette read jagged on the device.
 *    The other 31 parts are unchanged.
 *  - the eyes come as canonical pair canvases (880×320): the
 *    open beads were fused in the source and the closed arcs
 *    sat at different fractions, so both pairs were rebuilt
 *    with their centres at the same fractions (240/880 and
 *    640/880 across, 160/320 down) — the view swaps the two
 *    images to blink and the eyes never jump sideways.
 *
 * Which parts appear is decided by [DioramaScene] (state
 * mapping) and [HeatScene] (the cooling objects) — this
 * object only paints. It replaces CoolObjectArt, whose seven
 * flat-vector objects are retired with the code-drawn face.
 */
object DioramaArt {

    enum class Part(val res: Int) {
        BODY(R.drawable.dio_body),
        EYES_OPEN(R.drawable.dio_eyes_open),
        EYES_CLOSED(R.drawable.dio_eyes_closed),
        MOUTH_SMILE(R.drawable.dio_mouth_smile),
        MOUTH_FLAT(R.drawable.dio_mouth_flat),
        MOUTH_OPEN(R.drawable.dio_mouth_open),
        MOUTH_ZIGZAG(R.drawable.dio_mouth_zigzag),
        SWEAT_BIG(R.drawable.dio_sweat_big),
        SWEAT_SMALL(R.drawable.dio_sweat_small),
        FLAME_S(R.drawable.dio_flame_s),
        FLAME_M(R.drawable.dio_flame_m),
        FLAME_L(R.drawable.dio_flame_l),
        STEAM(R.drawable.dio_steam),
        PARASOL(R.drawable.dio_parasol),
        FAN(R.drawable.dio_fan),
        AIRCON(R.drawable.dio_aircon),
        ICE(R.drawable.dio_ice),
        WINDCHIME(R.drawable.dio_windchime),
        SHAVED_ICE(R.drawable.dio_shaved_ice),
        SNOWMAN(R.drawable.dio_snowman),
        PENGUIN(R.drawable.dio_penguin),
        WATERMELON(R.drawable.dio_watermelon),
        LIGHTNING(R.drawable.dio_lightning),
        SPARKLE(R.drawable.dio_sparkle),
        SNOWFLAKE(R.drawable.dio_snowflake),
        GROUND_GREEN(R.drawable.dio_ground_green),
        GROUND_DRY(R.drawable.dio_ground_dry),
        GROUND_DESERT(R.drawable.dio_ground_desert),
        BACKDROP_COOL(R.drawable.dio_backdrop_cool),
        BACKDROP_MILD(R.drawable.dio_backdrop_mild),
        BACKDROP_HOT(R.drawable.dio_backdrop_hot),
        BACKDROP_DESERT(R.drawable.dio_backdrop_desert),
    }

    // The window hole inside the body image, measured from the
    // cut-out's alpha: the one enclosed transparent component.
    // v0.4.1 re-measured for the regenerated body
    // (proposals/diorama-parts/body-v2.png, 650×991; window at
    // px x 125..523, y 515..811) — the new photograph's window
    // sits lower and narrower than the v0.4.0 body's.
    const val WIN_L = 0.1923f
    const val WIN_T = 0.5197f
    const val WIN_R = 0.8046f
    const val WIN_B = 0.8184f

    /** The part for a backdrop decision. */
    fun backdropPart(d: DioramaScene.Backdrop): Part = when (d) {
        DioramaScene.Backdrop.COOL -> Part.BACKDROP_COOL
        DioramaScene.Backdrop.MILD -> Part.BACKDROP_MILD
        DioramaScene.Backdrop.HOT -> Part.BACKDROP_HOT
        DioramaScene.Backdrop.DESERT -> Part.BACKDROP_DESERT
    }

    /** The part for a ground decision. */
    fun groundPart(d: DioramaScene.Ground): Part = when (d) {
        DioramaScene.Ground.GREEN -> Part.GROUND_GREEN
        DioramaScene.Ground.DRY -> Part.GROUND_DRY
        DioramaScene.Ground.DESERT -> Part.GROUND_DESERT
    }

    /** The part for a mouth decision. */
    fun mouthPart(d: DioramaScene.Mouth): Part = when (d) {
        DioramaScene.Mouth.SMILE -> Part.MOUTH_SMILE
        DioramaScene.Mouth.FLAT -> Part.MOUTH_FLAT
        DioramaScene.Mouth.OPEN -> Part.MOUTH_OPEN
        DioramaScene.Mouth.ZIGZAG -> Part.MOUTH_ZIGZAG
    }

    /** The part for a flame decision (null when there is none). */
    fun flamePart(d: DioramaScene.Flame): Part? = when (d) {
        DioramaScene.Flame.NONE -> null
        DioramaScene.Flame.SMALL -> Part.FLAME_S
        DioramaScene.Flame.MEDIUM -> Part.FLAME_M
        DioramaScene.Flame.LARGE -> Part.FLAME_L
    }

    /** The part for one earned cooling object (HeatScene's set). */
    fun coolObjectPart(obj: HeatScene.CoolObject): Part = when (obj) {
        HeatScene.CoolObject.PARASOL -> Part.PARASOL
        HeatScene.CoolObject.WINDBELL -> Part.WINDCHIME
        HeatScene.CoolObject.ICE -> Part.ICE
        HeatScene.CoolObject.AIRCON -> Part.AIRCON
        HeatScene.CoolObject.FAN -> Part.FAN
        HeatScene.CoolObject.SHAVED_ICE -> Part.SHAVED_ICE
        HeatScene.CoolObject.SNOWMAN -> Part.SNOWMAN
    }

    private val cache = HashMap<Part, Bitmap>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val dst = RectF()
    private val src = Rect()

    fun bitmap(resources: Resources, part: Part): Bitmap? {
        // The live face draws on the UI thread and the widget on a
        // background thread; guard the lazy decode (the same rule
        // CoolObjectArt kept).
        synchronized(cache) {
            cache[part]?.let { return it }
            val bmp = BitmapFactory.decodeResource(resources, part.res) ?: return null
            cache[part] = bmp
            return bmp
        }
    }

    /**
     * Draws [part] fit-centred in the rect (cx, cy, w, h) — the
     * bitmap's own aspect is preserved, so tight-cropped parts
     * of any shape land at their natural proportions. [alpha]
     * and an extra uniform [scale] about the centre are the
     * transform hooks the live face animates flames, sweat,
     * steam and sparkles with.
     */
    fun drawCentered(
        canvas: Canvas, resources: Resources, part: Part,
        cx: Float, cy: Float, w: Float, h: Float,
        alpha: Int = 255, scale: Float = 1f,
    ) {
        val bmp = bitmap(resources, part) ?: return
        val bw = bmp.width.toFloat()
        val bh = bmp.height.toFloat()
        if (bw <= 0f || bh <= 0f || w <= 0f || h <= 0f) return
        val s = minOf(w / bw, h / bh) * scale
        val dw = bw * s
        val dh = bh * s
        dst.set(cx - dw / 2f, cy - dh / 2f, cx + dw / 2f, cy + dh / 2f)
        paint.alpha = alpha
        canvas.drawBitmap(bmp, null, dst, paint)
        paint.alpha = 255
    }

    /** Draws [part] stretched exactly into [rect] (the body). */
    fun drawInto(canvas: Canvas, resources: Resources, part: Part, rect: RectF) {
        val bmp = bitmap(resources, part) ?: return
        paint.alpha = 255
        canvas.drawBitmap(bmp, null, rect, paint)
    }

    /**
     * Draws [part] covering [rect] completely, centre-cropped
     * (the backdrop boards filling the face slot).
     */
    fun drawCover(canvas: Canvas, resources: Resources, part: Part, rect: RectF) {
        val bmp = bitmap(resources, part) ?: return
        val bw = bmp.width.toFloat()
        val bh = bmp.height.toFloat()
        if (bw <= 0f || bh <= 0f || rect.width() <= 0f || rect.height() <= 0f) return
        val scale = maxOf(rect.width() / bw, rect.height() / bh)
        val sw = rect.width() / scale
        val sh = rect.height() / scale
        src.set(
            ((bw - sw) / 2f).toInt(), ((bh - sh) / 2f).toInt(),
            ((bw + sw) / 2f).toInt(), ((bh + sh) / 2f).toInt(),
        )
        paint.alpha = 255
        canvas.drawBitmap(bmp, src, rect, paint)
    }
}
