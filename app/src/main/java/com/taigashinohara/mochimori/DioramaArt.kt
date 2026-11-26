package com.taigashinohara.mochimori

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.min

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
 *  - v0.4.2: the body was regenerated once more on green and
 *    keyed the same way — a wide metal cap sitting flush on
 *    the body (v0.4.1's narrow cap showed a gap), and a much
 *    larger, taller window with a recessed inner frame, so
 *    the liquid reads bigger and the whole thing reads more
 *    like a battery. The other 31 parts are unchanged.
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
        BODY_GRASS(R.drawable.dio_body_grass),
        BODY_DESERT(R.drawable.dio_body_desert),
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
    // v0.4.2 re-measured for the second regenerated body
    // (proposals/diorama-parts/body-v3.png, 851×1348; the hole
    // at px x 158..688, y 356..1104) — the window is the inner
    // opening of the recessed frame (where the green actually
    // showed through), not the recess's outer rim. Its area is
    // ~35% of the body image, about 1.9× the v0.4.1 window.
    const val WIN_L = 0.1857f
    const val WIN_T = 0.2641f
    const val WIN_R = 0.8085f
    const val WIN_B = 0.8190f

    /**
     * Master gate for integrated-stage surrounding props (cooling objects,
     * grounded rewards, clay flames, cap steam, and headroom framing).
     * Enabled once dio_body_desert and the chroma-keyed prop replacements
     * are in place; both BatteryFaceView and WidgetFaceRenderer read this
     * single flag so the two never disagree.
     */
    const val STAGE_PROPS_READY = true

    /** Depth layer for a prop placed on an integrated [StageSpec]. */
    enum class StageLayer { BACK, FRONT, CAP_TOP }

    /**
     * Grounded placement of one prop in [StageSpec] normalised coordinates
     * (`0..1` of `stageRect`). [anchorBottomY] is where the prop's bottom
     * edge meets the pedestal surface (or cap top for [StageLayer.CAP_TOP]);
     * values below 0 extend into [StageSpec.headroomTopFrac].
     */
    data class PropSeat(
        val anchorX: Float,
        val anchorBottomY: Float,
        val maxWFrac: Float,
        val maxHFrac: Float,
        val layer: StageLayer,
        val castShadow: Boolean = true,
        val shadowScaleX: Float = 0.72f,
        val shadowScaleY: Float = 0.14f,
        val shadowAlpha: Int = 60,
    )

    /** One flame seat on an integrated stage, active at or above [minLevel] (1..3). */
    data class FlameSeat(
        val seat: PropSeat,
        val minLevel: Int,
        /** Relative scale multiplier per flame stage (0, 1, 2, 3). */
        val stageScale: Float = 1f,
    )

    /** Pure float rect returned by [computeStageBounds] (JVM unit-testable). */
    data class StageBounds(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
    ) {
        val width: Float get() = right - left
        val height: Float get() = bottom - top
    }

    /**
     * Geometry and face-placement contract for one integrated
     * battery + pedestal base image (window cut out, no face).
     * Adding the hot "battery + cracked clay" asset after quota recovery
     * is a single [StageSpec] entry wired into [stageSpec].
     */
    data class StageSpec(
        val bodyPart: Part,
        /** Natural image aspect ratio (width / height). */
        val aspectRatio: Float,
        /** Enclosed transparent window hole inside the image (0..1 fractions). */
        val winL: Float,
        val winT: Float,
        val winR: Float,
        val winB: Float,
        /** Tuck-under pad as a fraction of the battery body width. */
        val winPadOfBodyW: Float,
        /** Window clip corner radius / expanded window width. */
        val winRadiusRatio: Float,
        /** Battery body horizontal centre & width as fractions of image width. */
        val bodyCenterX: Float,
        val bodyWidthFrac: Float,
        /** Eye pair vertical centre as a fraction of image height. */
        val eyesCenterY: Float,
        /** Mouth vertical offset below eyes, as a fraction of body width. */
        val mouthOffsetYOfBodyW: Float,
        /** Extra headroom above the cap (fraction of stageH) for parasol & steam. */
        val headroomTopFrac: Float = 0.24f,
        /** Extra room below the pedestal (fraction of stageH) for its cast shadow. */
        val headroomBottomFrac: Float = 0.06f,
        /** Horizontal slack on each side (fraction of stageW) for overhanging props. */
        val padSideFrac: Float = 0.06f,
        /** Soft cast-shadow ellipse under the circular pedestal (fractions of stage). */
        val pedestalShadowCenterY: Float = 0.975f,
        val pedestalShadowWidthFrac: Float = 0.88f,
        val pedestalShadowHeightFrac: Float = 0.13f,
    )

    // Integrated battery + grass pedestal (759x718, magenta-keyed + despill).
    val STAGE_GRASS = StageSpec(
        bodyPart = Part.BODY_GRASS,
        aspectRatio = 759f / 718f,
        winL = 0.4032f,
        winT = 0.2535f,
        winR = 0.5995f,
        winB = 0.6114f,
        winPadOfBodyW = 0.020f,
        winRadiusRatio = 0.295f,
        bodyCenterX = 380f / 759f,
        bodyWidthFrac = 248f / 759f,
        eyesCenterY = 120f / 718f,
        mouthOffsetYOfBodyW = 0.056f,
    )

    // Integrated battery + cracked terracotta desert pedestal (758x716, magenta-keyed + despill).
    val STAGE_DESERT = StageSpec(
        bodyPart = Part.BODY_DESERT,
        aspectRatio = 758f / 716f,
        winL = 0.4011f,
        winT = 0.2514f,
        winR = 0.5989f,
        winB = 0.6117f,
        winPadOfBodyW = 0.020f,
        winRadiusRatio = 0.295f,
        bodyCenterX = 379f / 758f,
        bodyWidthFrac = 248f / 758f,
        eyesCenterY = 120f / 716f,
        mouthOffsetYOfBodyW = 0.056f,
    )

    /**
     * Integrated base asset for the 2-base stage decision (GRASS / DESERT).
     */
    fun stageSpec(base: DioramaScene.StageBase): StageSpec = when (base) {
        DioramaScene.StageBase.GRASS -> STAGE_GRASS
        DioramaScene.StageBase.DESERT -> STAGE_DESERT
    }

    /** Legacy overload delegating to the 2-base [stageSpec]. */
    fun stageSpec(ground: DioramaScene.Ground): StageSpec = when (ground) {
        DioramaScene.Ground.GREEN -> stageSpec(DioramaScene.StageBase.GRASS)
        DioramaScene.Ground.DRY, DioramaScene.Ground.DESERT -> stageSpec(DioramaScene.StageBase.DESERT)
    }

    /**
     * Computes the inner [StageSpec] image rect inside an available box
     * `[availLeft..availRight, availTop..availBottom]`.
     * When [includePropHeadroom] is true, reserves [StageSpec.headroomTopFrac],
     * [StageSpec.headroomBottomFrac] and [StageSpec.padSideFrac] so the
     * parasol, cap steam and pedestal shadow never clip at the slot edges.
     */
    fun computeStageBounds(
        availLeft: Float,
        availTop: Float,
        availRight: Float,
        availBottom: Float,
        spec: StageSpec,
        includePropHeadroom: Boolean = STAGE_PROPS_READY,
        maxStageH: Float = Float.POSITIVE_INFINITY,
    ): StageBounds {
        val availW = (availRight - availLeft).coerceAtLeast(1f)
        val availH = (availBottom - availTop).coerceAtLeast(1f)
        val wMult = if (includePropHeadroom) 1f + spec.padSideFrac * 2f else 1f
        val hMult = if (includePropHeadroom) 1f + spec.headroomTopFrac + spec.headroomBottomFrac else 1f
        val topPadFrac = if (includePropHeadroom) spec.headroomTopFrac else 0f

        var stageH = min(availH / hMult, maxStageH)
        var stageW = stageH * spec.aspectRatio
        if (stageW * wMult > availW) {
            stageW = availW / wMult
            stageH = stageW / spec.aspectRatio
        }
        val totalH = stageH * hMult
        val left = availLeft + (availW - stageW) / 2f
        val top = availTop + (availH - totalH) / 2f + stageH * topPadFrac
        return StageBounds(left, top, left + stageW, top + stageH)
    }

    /**
     * Fixed pedestal seats for all 7 [HeatScene.CoolObject] items on an
     * integrated stage (normalised to `stageRect`, matching the approved
     * cool diorama composition + tabletop/standing aircon on back-left).
     */
    val COOL_PROP_SEATS: Map<HeatScene.CoolObject, PropSeat> = mapOf(
        // Back layer: planted behind the battery silhouette
        HeatScene.CoolObject.PARASOL to PropSeat(
            anchorX = 0.73f, anchorBottomY = 0.59f, maxWFrac = 0.58f, maxHFrac = 0.70f,
            layer = StageLayer.BACK, castShadow = false,
        ),
        HeatScene.CoolObject.WINDBELL to PropSeat(
            anchorX = 0.17f, anchorBottomY = 0.75f, maxWFrac = 0.23f, maxHFrac = 0.56f,
            layer = StageLayer.BACK, castShadow = true, shadowScaleX = 0.65f, shadowAlpha = 55,
        ),
        HeatScene.CoolObject.AIRCON to PropSeat(
            anchorX = 0.30f, anchorBottomY = 0.70f, maxWFrac = 0.18f, maxHFrac = 0.20f,
            layer = StageLayer.BACK, castShadow = true, shadowScaleX = 0.80f, shadowAlpha = 55,
        ),
        HeatScene.CoolObject.FAN to PropSeat(
            anchorX = 0.81f, anchorBottomY = 0.76f, maxWFrac = 0.29f, maxHFrac = 0.39f,
            layer = StageLayer.BACK, castShadow = true, shadowScaleX = 0.68f, shadowAlpha = 55,
        ),
        // Front layer: seated on the front half of the pedestal
        HeatScene.CoolObject.ICE to PropSeat(
            anchorX = 0.21f, anchorBottomY = 0.80f, maxWFrac = 0.17f, maxHFrac = 0.19f,
            layer = StageLayer.FRONT, castShadow = true, shadowScaleX = 0.80f, shadowAlpha = 62,
        ),
        HeatScene.CoolObject.SNOWMAN to PropSeat(
            anchorX = 0.33f, anchorBottomY = 0.84f, maxWFrac = 0.16f, maxHFrac = 0.22f,
            layer = StageLayer.FRONT, castShadow = true, shadowScaleX = 0.72f, shadowAlpha = 65,
        ),
        HeatScene.CoolObject.SHAVED_ICE to PropSeat(
            anchorX = 0.71f, anchorBottomY = 0.84f, maxWFrac = 0.18f, maxHFrac = 0.20f,
            layer = StageLayer.FRONT, castShadow = true, shadowScaleX = 0.75f, shadowAlpha = 65,
        ),
    )

    /**
     * Grounded 7/7 reward props (heat == 0 only). Floating air stickers
     * (sparkle / snowflake) are intentionally omitted on the integrated
     * stage to match the approved diorama vision.
     */
    val DECOR_SEATS: List<Pair<Part, PropSeat>> = listOf(
        Part.PENGUIN to PropSeat(
            anchorX = 0.44f, anchorBottomY = 0.86f, maxWFrac = 0.15f, maxHFrac = 0.17f,
            layer = StageLayer.FRONT, castShadow = true, shadowScaleX = 0.74f, shadowAlpha = 68,
        ),
        Part.WATERMELON to PropSeat(
            anchorX = 0.60f, anchorBottomY = 0.86f, maxWFrac = 0.12f, maxHFrac = 0.12f,
            layer = StageLayer.FRONT, castShadow = true, shadowScaleX = 0.82f, shadowAlpha = 62,
        ),
    )

    /**
     * Flame seats hugging the battery's base and shoulders (back flames for
     * stages 1..3, front base tongues at stage 3 only). Sweat drops are
     * intentionally omitted on the integrated stage to match the hot goal.
     */
    val STAGE_FLAME_SEATS: List<FlameSeat> = listOf(
        FlameSeat(
            PropSeat(0.30f, 0.74f, 0.26f, 0.48f, StageLayer.BACK, castShadow = true, shadowScaleX = 0.72f, shadowAlpha = 45),
            minLevel = 1, stageScale = 1.0f,
        ),
        FlameSeat(
            PropSeat(0.70f, 0.74f, 0.26f, 0.48f, StageLayer.BACK, castShadow = true, shadowScaleX = 0.72f, shadowAlpha = 45),
            minLevel = 1, stageScale = 1.0f,
        ),
        FlameSeat(
            PropSeat(0.32f, 0.50f, 0.22f, 0.36f, StageLayer.BACK, castShadow = false),
            minLevel = 2, stageScale = 0.92f,
        ),
        FlameSeat(
            PropSeat(0.68f, 0.50f, 0.22f, 0.36f, StageLayer.BACK, castShadow = false),
            minLevel = 2, stageScale = 0.92f,
        ),
        FlameSeat(
            PropSeat(0.68f, 0.34f, 0.22f, 0.38f, StageLayer.BACK, castShadow = false),
            minLevel = 3, stageScale = 1.05f,
        ),
        FlameSeat(
            PropSeat(0.66f, 0.79f, 0.15f, 0.23f, StageLayer.FRONT, castShadow = true, shadowScaleX = 0.70f, shadowAlpha = 40),
            minLevel = 3, stageScale = 0.95f,
        ),
        FlameSeat(
            PropSeat(0.34f, 0.79f, 0.15f, 0.22f, StageLayer.FRONT, castShadow = true, shadowScaleX = 0.70f, shadowAlpha = 40),
            minLevel = 3, stageScale = 0.90f,
        ),
    )

    /** Sculpted steam curl rising from the silver terminal cap (Stage 0 only). */
    val STEAM_SEAT = PropSeat(
        anchorX = 0.50f, anchorBottomY = 0.02f, maxWFrac = 0.22f, maxHFrac = 0.26f,
        layer = StageLayer.CAP_TOP, castShadow = false,
    )

    // Approved face proportions relative to the battery body width (bw).
    const val SPLIT_EYE_DIAM_OF_BW = 0.095f
    const val SPLIT_EYE_SEP_OF_BW = 0.40f
    private const val EYE_OPEN_BEAD_PX = 121f
    private const val EYE_LEFT_CX_PX = 240f
    private const val EYE_RIGHT_CX_PX = 640f

    /** Per-mouth width as a fraction of the battery body width. */
    fun mouthWidthOfBody(mouth: DioramaScene.Mouth): Float = when (mouth) {
        DioramaScene.Mouth.SMILE -> 0.142f
        DioramaScene.Mouth.FLAT -> 0.138f
        DioramaScene.Mouth.OPEN -> 0.128f
        DioramaScene.Mouth.ZIGZAG -> 0.205f
    }

    /** Computes the liquid window rect (with tuck-under pad) inside [stageRect]. */
    fun computeWindowRect(stageRect: RectF, spec: StageSpec, out: RectF) {
        val sw = stageRect.width()
        val sh = stageRect.height()
        val pad = sw * spec.bodyWidthFrac * spec.winPadOfBodyW
        out.set(
            stageRect.left + sw * spec.winL - pad,
            stageRect.top + sh * spec.winT - pad,
            stageRect.left + sw * spec.winR + pad,
            stageRect.top + sh * spec.winB + pad,
        )
    }

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
    private val paint by lazy { Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG) }
    private val shadowPaint by lazy {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            shader = RadialGradient(
                0f, 0f, 1f,
                intArrayOf(
                    Color.argb(255, 8, 18, 26),
                    Color.argb(150, 8, 18, 26),
                    Color.TRANSPARENT,
                ),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
    }
    private val dst by lazy { RectF() }
    private val src by lazy { Rect() }

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

    /**
     * Draws the left and right halves of a canonical 880x320 eye-pair
     * bitmap ([Part.EYES_OPEN] or [Part.EYES_CLOSED]) separately so the
     * eye diameter ([SPLIT_EYE_DIAM_OF_BW]) and centre-to-centre
     * separation ([SPLIT_EYE_SEP_OF_BW]) match the approved diorama
     * proportions without modifying the source eye assets.
     */
    fun drawSplitEyes(
        canvas: Canvas, resources: Resources, part: Part,
        cx: Float, cy: Float, bodyW: Float,
    ) {
        val bmp = bitmap(resources, part) ?: return
        val bw = bmp.width
        val bh = bmp.height
        val halfW = bw / 2
        if (halfW <= 0 || bh <= 0 || bodyW <= 0f) return
        val scale = (bodyW * SPLIT_EYE_DIAM_OF_BW) / EYE_OPEN_BEAD_PX
        val dw = halfW * scale
        val dh = bh * scale
        val sep = bodyW * SPLIT_EYE_SEP_OF_BW
        val topY = cy - dh / 2f
        paint.alpha = 255

        val leftX = (cx - sep / 2f) - (EYE_LEFT_CX_PX / halfW) * dw
        src.set(0, 0, halfW, bh)
        dst.set(leftX, topY, leftX + dw, topY + dh)
        canvas.drawBitmap(bmp, src, dst, paint)

        val rightX = (cx + sep / 2f) - ((EYE_RIGHT_CX_PX - halfW) / halfW) * dw
        src.set(halfW, 0, bw, bh)
        dst.set(rightX, topY, rightX + dw, topY + dh)
        canvas.drawBitmap(bmp, src, dst, paint)
    }

    /** Draws eyes + mouth on the forehead of an integrated [StageSpec] base. */
    fun drawStageFace(
        canvas: Canvas, resources: Resources,
        stageRect: RectF, spec: StageSpec,
        eyesPart: Part, mouth: DioramaScene.Mouth,
    ) {
        val bodyW = stageRect.width() * spec.bodyWidthFrac
        val bcx = stageRect.left + stageRect.width() * spec.bodyCenterX
        val eyeY = stageRect.top + stageRect.height() * spec.eyesCenterY
        val mouthY = eyeY + bodyW * spec.mouthOffsetYOfBodyW
        drawSplitEyes(canvas, resources, eyesPart, bcx, eyeY, bodyW)
        val mouthW = bodyW * mouthWidthOfBody(mouth)
        drawCentered(canvas, resources, mouthPart(mouth), bcx, mouthY, mouthW, mouthW)
    }

    /**
     * Zero-allocation radial contact shadow drawn as a scaled unit circle.
     * Works identically on hardware-accelerated View Canvas, Widget Bitmap
     * Canvas, and Robolectric without BlurMaskFilter.
     */
    fun drawContactShadow(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        rx: Float,
        ry: Float,
        alpha: Int,
    ) {
        if (rx <= 0.5f || ry <= 0.5f || alpha <= 0) return
        shadowPaint.alpha = alpha.coerceIn(0, 255)
        canvas.save()
        canvas.translate(cx, cy)
        canvas.scale(rx, ry)
        canvas.drawCircle(0f, 0f, 1f, shadowPaint)
        canvas.restore()
        shadowPaint.alpha = 255
    }

    /** Soft elliptical cast shadow beneath the circular pedestal base. */
    fun drawStagePedestalShadow(
        canvas: Canvas,
        stageRect: RectF,
        spec: StageSpec,
        darkTheme: Boolean,
    ) {
        val sw = stageRect.width()
        val sh = stageRect.height()
        if (sw <= 0f || sh <= 0f) return
        drawContactShadow(
            canvas = canvas,
            cx = stageRect.centerX(),
            cy = stageRect.top + sh * spec.pedestalShadowCenterY,
            rx = sw * spec.pedestalShadowWidthFrac * 0.5f,
            ry = sh * spec.pedestalShadowHeightFrac * 0.5f,
            alpha = if (darkTheme) 78 else 42,
        )
    }

    /**
     * Draws [part] bottom-centre grounded at [seat] inside [stageRect], with
     * an optional soft contact shadow at its foot. [scale] scales about the
     * bottom anchor so breathing flames stay glued to the pedestal.
     */
    fun drawGrounded(
        canvas: Canvas,
        resources: Resources,
        part: Part,
        stageRect: RectF,
        seat: PropSeat,
        alpha: Int = 255,
        scale: Float = 1f,
        dyPx: Float = 0f,
        darkTheme: Boolean = false,
    ) {
        val bmp = bitmap(resources, part) ?: return
        val bw = bmp.width.toFloat()
        val bh = bmp.height.toFloat()
        val sw = stageRect.width()
        val sh = stageRect.height()
        if (bw <= 0f || bh <= 0f || sw <= 0f || sh <= 0f) return
        val maxW = sw * seat.maxWFrac
        val maxH = sh * seat.maxHFrac
        val s = min(maxW / bw, maxH / bh) * scale
        val dw = bw * s
        val dh = bh * s
        val cx = stageRect.left + sw * seat.anchorX
        val bottomY = stageRect.top + sh * seat.anchorBottomY + dyPx
        if (seat.castShadow) {
            val shadowAlpha = if (darkTheme) (seat.shadowAlpha * 1.25f).toInt() else seat.shadowAlpha
            drawContactShadow(
                canvas = canvas,
                cx = cx,
                cy = bottomY - dh * 0.03f,
                rx = dw * seat.shadowScaleX * 0.5f,
                ry = maxOf(2f, dh * seat.shadowScaleY * 0.5f),
                alpha = shadowAlpha,
            )
        }
        dst.set(cx - dw / 2f, bottomY - dh, cx + dw / 2f, bottomY)
        paint.alpha = alpha.coerceIn(0, 255)
        canvas.drawBitmap(bmp, null, dst, paint)
        paint.alpha = 255
    }

    /** Flame stage scale multiplier for integrated-stage flames (levels 0..3). */
    fun flameStageScale(flame: DioramaScene.Flame): Float = when (flame) {
        DioramaScene.Flame.NONE -> 0f
        DioramaScene.Flame.SMALL -> 0.62f
        DioramaScene.Flame.MEDIUM -> 0.84f
        DioramaScene.Flame.LARGE -> 1.04f
    }

    /** Numeric flame level (0..3) corresponding to [DioramaScene.Flame]. */
    fun flameStageLevel(flame: DioramaScene.Flame): Int = when (flame) {
        DioramaScene.Flame.NONE -> 0
        DioramaScene.Flame.SMALL -> 1
        DioramaScene.Flame.MEDIUM -> 2
        DioramaScene.Flame.LARGE -> 3
    }
}
