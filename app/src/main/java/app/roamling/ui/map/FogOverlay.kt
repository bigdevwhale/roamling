package app.roamling.ui.map

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.view.MotionEvent
import app.roamling.data.Shadow
import app.roamling.domain.FogStore
import app.roamling.geo.Grid
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** What the overlay draws this frame. */
class Scene(
    val fix: Pair<Double, Double>?,
    val trail: List<Pair<Double, Double>>,
    val leader: String?,
    val scent: Triple<Double, Double, String>?,
    val scentKind: String = "sparkle",
    val shadows: List<Shadow>,
    val rainbow: Pair<Double, Double>?,
    val home: Pair<Double, Double>?,
    val dark: Boolean,
    val walking: Boolean,
    /** After the finale the fog is warm: a golden haze instead of grey. */
    val warm: Boolean = false,
    /** Sealed districts and their spirit's seal number. */
    val seals: Map<Long, Int> = emptyMap(),
    /** Glimpse the Keeper: a big shape far in the fog. */
    val keeper: Boolean = false,
    val homeDistrict: Long? = null,
    val hereDistrict: Long? = null,
    /** The spirit (by ordinal) of each district known so far; districts not in it are still unknown. */
    val kinds: Map<Long, Int> = emptyMap(),
)

sealed interface Pick {
    data object Scent : Pick
    data class OfShadow(val s: Shadow) : Pick
    data object Rainbow : Pick
    data class District(val key: Long) : Pick
}

/**
 * The fog over the map, after Fogwalk's: rendered into a coarse bitmap pinned to the world and only moved while the map
 * pans; explored cells are cut out with soft edges. Over it: the walk's trail, the nose's goal, wild shadows, the rainbow's end
 * and the leader walking with you.
 */
class FogOverlay(private val ctx: Context, private val fog: FogStore, private val density: Float, private val onLongPress: ((Double, Double) -> Unit)? = null, private val onPick: (Pick) -> Unit) : Overlay() {
    @Volatile var scene: Scene? = null
    private val scale = 3
    private var fogBitmap: Bitmap? = null
    private var fogCanvas: Canvas? = null
    private var fogKey: List<Any>? = null
    private var cacheAx = 1.0; private var cacheAy = 1.0; private var cacheOx = 0.0; private var cacheOy = 0.0
    private var lastAx = 0.0; private var zoomedAt = 0L
    private val dst = RectF()
    private val clearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT) }
    private val blit = Paint(Paint.FILTER_BITMAP_FLAG)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val sprite = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val shadowPaint = Paint(Paint.FILTER_BITMAP_FLAG).apply { colorFilter = PorterDuffColorFilter(0xFF6E6A86.toInt(), PorterDuff.Mode.MULTIPLY) }
    private val path = Path()
    private var ax = 0.0; private var bx = 0.0; private var ay = 0.0; private var by = 0.0
    private val hits = ArrayList<Pair<Pick, FloatArray>>()
    private val bitmaps = HashMap<String, Bitmap?>()

    @SuppressLint("DiscouragedApi")
    private fun bmp(name: String): Bitmap? = bitmaps.getOrPut(name) {
        val id = ctx.resources.getIdentifier(name, "drawable", ctx.packageName)
        if (id == 0) null else BitmapFactory.decodeResource(ctx.resources, id, BitmapFactory.Options().apply { inScaled = false })
    }

    private fun sx(lon: Double) = (ax * Grid.mx(lon) + bx).toFloat()
    private fun sy(lat: Double) = (ay * Grid.my(lat) + by).toFloat()
    private fun metersPx(lat: Double) = (ax / Grid.N / Grid.cellMeters(lat)).toFloat()

    override fun draw(c: Canvas, mv: MapView, shadow: Boolean) {
        if (shadow) return
        val s = scene ?: return
        val w = mv.width; val h = mv.height
        if (w == 0 || h == 0) return
        val proj = mv.projection
        val bb = proj.boundingBox
        val nw = proj.toPixels(GeoPoint(bb.latNorth, bb.lonWest), null)
        val se = proj.toPixels(GeoPoint(bb.latSouth, bb.lonEast), null)
        val mx0 = Grid.mx(bb.lonWest); val mx1 = Grid.mx(bb.lonEast); val my0 = Grid.my(bb.latNorth); val my1 = Grid.my(bb.latSouth)
        if (mx1 - mx0 <= 0 || my1 - my0 <= 0) return
        ax = (se.x - nw.x) / (mx1 - mx0); bx = nw.x - ax * mx0
        ay = (se.y - nw.y) / (my1 - my0); by = nw.y - ay * my0
        // a turned map (#3) shows the corners of a bigger square: the fog reaches the screen's diagonal then
        val d = kotlin.math.hypot(w.toDouble(), h.toDouble())
        padX = if (mv.mapOrientation % 360f != 0f) (d - w) / 2 else 0.0
        padY = if (mv.mapOrientation % 360f != 0f) (d - h) / 2 else 0.0
        drawFog(c, s, w, h)
        hits.clear()
        drawDistricts(c, s, mx0, mx1, my0, my1)
        val t = System.currentTimeMillis()
        drawTrail(c, s)
        s.home?.let { (la, lo) -> bmp("ic_door")?.let { b -> drawSprite(c, b, sx(lo), sy(la), 30 * density, 1f) } }
        s.rainbow?.let { drawRainbow(c, it, t) }
        s.scent?.let { drawScent(c, it, s.scentKind, t) }
        s.shadows.forEach { drawShadow(c, it, t) }
        if (s.keeper) drawKeeper(c, s, t)
        drawWalker(c, s, t)
    }

    private var padX = 0.0
    private var padY = 0.0

    private fun drawFog(c: Canvas, s: Scene, w: Int, h: Int) {
        val key = listOf(fog.version.value, s.dark, s.warm, w, h, padX > 0)
        val now = android.os.SystemClock.uptimeMillis()
        if (ax != lastAx) zoomedAt = now
        lastAx = ax
        val bmp = fogBitmap
        val ratio = ax / cacheAx
        val rebuild = bmp == null || key != fogKey || run {
            val l = ax * cacheOx + bx; val t = ay * cacheOy + by
            val r = l + bmp.width * scale * ratio; val b = t + bmp.height * scale * (ay / cacheAy)
            val covers = l <= -padX && t <= -padY && r >= w + padX && b >= h + padY
            val settled = abs(ratio - 1) > 0.002 && now - zoomedAt > 220
            !covers || ratio !in 0.7..1.45 || settled
        }
        if (rebuild) { render(s, w, h); fogKey = key }
        val f = fogBitmap ?: return
        val l = (ax * cacheOx + bx).toFloat(); val t = (ay * cacheOy + by).toFloat()
        dst.set(l, t, l + (f.width * scale * ax / cacheAx).toFloat(), t + (f.height * scale * ay / cacheAy).toFloat())
        c.drawBitmap(f, null, dst, blit)
    }

    private fun render(s: Scene, w: Int, h: Int) {
        val margin = max(w, h) * 0.3 + max(padX, padY)
        cacheAx = ax; cacheAy = ay
        val ux = scale / ax; val uy = scale / ay
        cacheOx = floor(((-margin - bx) / ax) / ux) * ux
        cacheOy = floor(((-margin - by) / ay) / uy) * uy
        val fw = ((w + 2 * margin) / scale).toInt() + 3
        val fh = ((h + 2 * margin) / scale).toInt() + 3
        var b = fogBitmap
        if (b == null || b.width != fw || b.height != fh) { b?.recycle(); b = Bitmap.createBitmap(fw, fh, Bitmap.Config.ARGB_8888); fogBitmap = b; fogCanvas = Canvas(b) }
        val fc = fogCanvas!!
        b.eraseColor(0)
        // a soft mist: a pale wash with darker drifts, pinned to the world
        fill.maskFilter = null
        fill.shader = null
        fill.color = when { s.warm && s.dark -> 0xF0332A22.toInt(); s.warm -> 0xE6F0DEC0.toInt(); s.dark -> 0xF0222A33.toInt(); else -> 0xEAD9DDD8.toInt() }
        fc.drawRect(0f, 0f, fw.toFloat(), fh.toFloat(), fill)
        val n = 1 shl 16
        val px = ax / scale / n
        val gx0 = floor(cacheOx * n).toInt(); val gy0 = floor(cacheOy * n).toInt()
        val cols = (fw / px).toInt() + 2; val rows = (fh / px).toInt() + 2
        if (cols * rows < 4000) {
            fill.maskFilter = BlurMaskFilter(max(2f, (px * .5).toFloat()), BlurMaskFilter.Blur.NORMAL)
            for (gx in gx0 until gx0 + cols) for (gy in gy0 until gy0 + rows) {
                val hsh = app.roamling.domain.hash("$gx:$gy")
                if (hsh and 3 != 0) continue
                fill.color = if (s.dark) 0x30000000 else 0x28707C88
                val cx = ((gx + .5 + (hsh shr 4 and 7) / 10.0) / n - cacheOx) / ux
                val cy = ((gy + .5 + (hsh shr 8 and 7) / 10.0) / n - cacheOy) / uy
                fc.drawCircle(cx.toFloat(), cy.toFloat(), (px * (.5 + (hsh shr 12 and 3) * .15)).toFloat(), fill)
            }
            fill.maskFilter = null
        }
        cutExplored(fc, cacheOx, cacheOx + fw * ux, cacheOy, cacheOy + fh * uy)
    }

    private fun cutExplored(fc: Canvas, mx0: Double, mx1: Double, my0: Double, my1: Double) {
        var level = FogStore.LEVELS.last()
        for (l in FogStore.LEVELS) if (ax / (1 shl l) >= 4 * density) { level = l; break }
        val n = 1 shl level
        val cellPx = ax / n / scale
        val x0 = max(0, floor(mx0 * n).toInt() - 2); val x1 = min(n - 1, floor(mx1 * n).toInt() + 2)
        val y0 = max(0, floor(my0 * n).toInt() - 2); val y1 = min(n - 1, floor(my1 * n).toInt() + 2)
        val capacity = 1 shl ((Grid.Z - level) * 2)
        val r = max(1.2, cellPx * 1.05).toFloat()
        clearPaint.color = 0xFF000000.toInt()
        clearPaint.maskFilter = if (r >= 3) BlurMaskFilter(r * 0.7f, BlurMaskFilter.Blur.NORMAL) else null
        val kx = ax / scale; val ky = ay / scale
        fog.visit(level, x0, y0, x1, y1) { x, y, count ->
            val strength = if (level == Grid.Z) 1.0 else min(1.0, 0.3 + sqrt(count.toDouble() / capacity) * 1.15)
            clearPaint.alpha = (strength * 255).toInt()
            fc.drawCircle((((x + 0.5) / n - mx0) * kx).toFloat(), (((y + 0.5) / n - my0) * ky).toFloat(), r, clearPaint)
        }
    }

    /**
     * The districts: dashed borders of the zoom-14 squares, the one underfoot bolder; sealed ones glow gold with their
     * spirit's seal in the middle; home has a little door.
     */
    private fun drawDistricts(c: Canvas, s: Scene, mx0: Double, mx1: Double, my0: Double, my1: Double) {
        val n = 1 shl Grid.SECTOR_Z
        val sizePx = ax / n
        if (sizePx < 24 * density) return
        val x0 = floor(mx0 * n).toInt(); val x1 = floor(mx1 * n).toInt()
        val y0 = floor(my0 * n).toInt(); val y1 = floor(my1 * n).toInt()
        if ((x1 - x0 + 1) * (y1 - y0 + 1) > 400) return
        for (x in x0..x1) for (y in y0..y1) {
            val k = Grid.key(x, y)
            val l = (ax * x / n + bx).toFloat(); val t = (ay * y / n + by).toFloat()
            val r = (ax * (x + 1) / n + bx).toFloat(); val b = (ay * (y + 1) / n + by).toFloat()
            val seal = s.seals[k]
            // a sealed district wears the colour of the spirit that sealed it
            val kind = seal?.let { n -> app.roamling.domain.Spirit.entries.firstOrNull { it.seal == n } } ?: s.kinds[k]?.let { app.roamling.domain.Spirit.entries[it] }
            val col = kind?.color?.toInt() ?: 0xFF7A4A22.toInt()
            fill.shader = null; fill.maskFilter = null
            // each district wears its kind's colour over the map: green parks, blue water, brick yards, slate hills, amber crossroads
            if (kind != null) { fill.color = (col and 0x00FFFFFF) or (if (seal != null) 0x52000000 else 0x3A000000); c.drawRect(l, t, r, b, fill) }
            if (seal != null) { fill.color = 0x22FFD27A; c.drawRect(l, t, r, b, fill) }
            val here = k == s.hereDistrict
            line.color = (col and 0x00FFFFFF) or (if (here || seal != null) 0xDD000000.toInt() else 0x88000000.toInt())
            line.strokeWidth = (if (here) 3f else 1.6f) * density
            line.pathEffect = android.graphics.DashPathEffect(floatArrayOf(10 * density, 7 * density), 0f)
            c.drawRect(l, t, r, b, line)
            line.pathEffect = null
            val cx = (l + r) / 2; val cy = (t + b) / 2
            // the district's badge: its seal when sealed, its spirit asleep when known, a question mark when not
            val badge = when {
                seal != null -> bmp("seal_$seal")
                kind != null -> bmp("ic_${kind.icon}")
                else -> null
            }
            fill.color = 0xE6FBF5E4.toInt()
            c.drawCircle(cx, cy, 26 * density, fill)
            line.color = col; line.strokeWidth = 2.5f * density; c.drawCircle(cx, cy, 26 * density, line)
            if (badge != null) drawSprite(c, badge, cx, cy + (if (seal != null) 20 else 16) * density, (if (seal != null) 40 else 32) * density, 1f)
            else { fill.color = 0xFF857A62.toInt(); fill.textSize = 26 * density; fill.textAlign = Paint.Align.CENTER; fill.isFakeBoldText = true; c.drawText("?", cx, cy + 9 * density, fill) }
            if (k == s.homeDistrict) bmp("ic_door")?.let { drawSprite(c, it, cx + 22 * density, cy - 8 * density, 22 * density, 1f) }
            // the kind in words, on a little label under the badge
            val label = kind?.short ?: app.roamling.domain.tr("В тумане", "In fog")
            fill.textSize = 13 * density; fill.textAlign = Paint.Align.CENTER; fill.isFakeBoldText = true
            val tw = fill.measureText(label) / 2 + 8 * density
            fill.color = col; c.drawRoundRect(cx - tw, cy + 30 * density, cx + tw, cy + 50 * density, 10 * density, 10 * density, fill)
            fill.color = 0xFFFFFFFF.toInt(); c.drawText(label, cx, cy + 45 * density, fill)
            hits += Pick.District(k) to floatArrayOf(cx, cy, 30 * density)
        }
    }

    private fun drawTrail(c: Canvas, s: Scene) {
        if (s.trail.size < 2) return
        path.reset()
        s.trail.forEachIndexed { i, (la, lo) -> if (i == 0) path.moveTo(sx(lo), sy(la)) else path.lineTo(sx(lo), sy(la)) }
        line.pathEffect = android.graphics.DashPathEffect(floatArrayOf(2 * density, 7 * density), 0f)
        line.color = 0xCC7A4A22.toInt(); line.strokeWidth = 4 * density
        c.drawPath(path, line)
        line.pathEffect = null
    }

    private fun drawSprite(c: Canvas, b: Bitmap, x: Float, y: Float, height: Float, alpha: Float, paint: Paint = sprite, flip: Boolean = false) {
        val wpx = height * b.width / b.height
        dst.set(x - wpx / 2, y - height, x + wpx / 2, y)
        paint.alpha = (alpha * 255).toInt()
        if (flip) { c.save(); c.scale(-1f, 1f, x, y) }
        c.drawBitmap(b, null, dst, paint)
        if (flip) c.restore()
    }

    private fun drawScent(c: Canvas, sc: Triple<Double, Double, String>, kind: String, t: Long) {
        val x = sx(sc.second); val y = sy(sc.first)
        val pulse = (sin(t / 400.0) * .5 + .5).toFloat()
        fill.shader = null; fill.maskFilter = BlurMaskFilter(14 * density, BlurMaskFilter.Blur.NORMAL)
        fill.color = Color.argb((90 + pulse * 80).toInt(), 255, 214, 120)
        c.drawCircle(x, y, 26 * density, fill)
        fill.maskFilter = null
        // a little dotted ring for the reach
        line.color = 0xAA8A5A20.toInt(); line.strokeWidth = 2 * density
        line.pathEffect = android.graphics.DashPathEffect(floatArrayOf(4 * density, 5 * density), 0f)
        c.drawCircle(x, y, 35 * metersPx(sc.first), line)
        line.pathEffect = null
        // the place stands in the middle of its ring, its find floats above it
        val place = bmp("place_$kind")
        if (place != null) drawSprite(c, place, x, y + 22 * density, 44 * density, 1f)
        bmp("find_${sc.third}")?.let { drawSprite(c, it, x + 18 * density, y - 12 * density - pulse * 4 * density, 24 * density, 1f) }
        hits += Pick.Scent to floatArrayOf(x, y, 30 * density)
    }

    private fun drawShadow(c: Canvas, sh: Shadow, t: Long) {
        val x = sx(sh.lon); val y = sy(sh.lat)
        val b = bmp("cloud_${Math.floorMod(app.roamling.domain.hash(sh.species), 6)}") ?: return
        val bob = (sin(t / 500.0 + sh.lat * 1e4) * 3 * density).toFloat()
        val pulse = (sin(t / 350.0) * .5 + .5).toFloat()
        fill.shader = null; fill.maskFilter = BlurMaskFilter(16 * density, BlurMaskFilter.Blur.NORMAL)
        fill.color = Color.argb((60 + pulse * 60).toInt(), 120, 110, 160)
        c.drawCircle(x, y - 16 * density, 30 * density, fill)
        fill.maskFilter = null
        drawSprite(c, b, x, y + bob, 34 * density, .95f, shadowPaint)
        fill.color = 0xFFFFF4DC.toInt(); fill.textSize = 20 * density; fill.textAlign = Paint.Align.CENTER; fill.isFakeBoldText = true
        c.drawText("?", x, y - 9 * density + bob, fill)
        hits += Pick.OfShadow(sh) to floatArrayOf(x, y - 22 * density, 32 * density)
    }

    private fun drawRainbow(c: Canvas, p: Pair<Double, Double>, t: Long) {
        val x = sx(p.second); val y = sy(p.first)
        val pulse = (sin(t / 400.0) * .5 + .5).toFloat()
        fill.shader = null; fill.maskFilter = BlurMaskFilter(16 * density, BlurMaskFilter.Blur.NORMAL)
        fill.color = Color.argb((80 + pulse * 70).toInt(), 255, 236, 160)
        c.drawCircle(x, y, 30 * density, fill)
        fill.maskFilter = null
        bmp("place_rainbow")?.let { drawSprite(c, it, x, y + 24 * density, 48 * density, 1f) }
        hits += Pick.Rainbow to floatArrayOf(x, y, 32 * density)
    }

    /** A big pale shape standing in the fog, at the corner of the district underfoot. Not tappable: only seen. */
    private fun drawKeeper(c: Canvas, s: Scene, t: Long) {
        val k = s.hereDistrict ?: return
        val n = 1 shl Grid.SECTOR_Z
        val x = (ax * (Grid.x(k) + .82) / n + bx).toFloat(); val y = (ay * (Grid.y(k) + .2) / n + by).toFloat()
        val b = bmp("pet_fogkeeper_2") ?: return
        val breathe = (sin(t / 1400.0) * .08 + .22).toFloat()
        drawSprite(c, b, x, y, 140 * density, breathe, shadowPaint)
    }

    private fun drawWalker(c: Canvas, s: Scene, t: Long) {
        val (la, lo) = s.fix ?: return
        val x = sx(lo); val y = sy(la)
        fill.shader = null; fill.maskFilter = null
        fill.color = 0x553E8EDE; c.drawCircle(x, y, 14 * density, fill)
        fill.color = 0xFFFFFFFF.toInt(); c.drawCircle(x, y, 7 * density, fill)
        fill.color = 0xFF3E8EDE.toInt(); c.drawCircle(x, y, 5 * density, fill)
        val leader = s.leader?.let { bmp(it) } ?: return
        val hop = if (s.walking) (abs(sin(t / 220.0)) * 4 * density).toFloat() else 0f
        drawSprite(c, leader, x + 22 * density, y + 6 * density - hop, 40 * density, 1f)
    }

    override fun onSingleTapConfirmed(e: MotionEvent, mapView: MapView): Boolean {
        val hit = hits.lastOrNull { (_, h) -> val dx = e.x - h[0]; val dy = e.y - h[1]; dx * dx + dy * dy <= h[2] * h[2] } ?: return false
        onPick(hit.first)
        return true
    }

    override fun onLongPress(e: MotionEvent, mapView: MapView): Boolean {
        val f = onLongPress ?: return false
        val p = mapView.projection.fromPixels(e.x.toInt(), e.y.toInt())
        f(p.latitude, p.longitude)
        return true
    }

    override fun onDetach(mapView: MapView?) {
        fogBitmap?.recycle(); fogBitmap = null
        super.onDetach(mapView)
    }
}
