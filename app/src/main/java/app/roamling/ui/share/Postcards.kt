package app.roamling.ui.share

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import app.roamling.R
import app.roamling.data.Pet
import app.roamling.data.Walk
import app.roamling.data.Waypoint
import app.roamling.data.parseMap
import app.roamling.domain.Catalog
import app.roamling.domain.Finds
import app.roamling.domain.Growth
import app.roamling.domain.SPECIES
import app.roamling.domain.Sky
import app.roamling.domain.Spirit
import app.roamling.domain.Taste
import app.roamling.domain.count
import app.roamling.domain.dayOf
import app.roamling.domain.dur
import app.roamling.domain.fmt
import app.roamling.domain.fmt1
import app.roamling.domain.fmtDayYear
import app.roamling.domain.tr
import app.roamling.domain.weekStart
import app.roamling.geo.Grid
import app.roamling.container
import app.roamling.ui.Data
import app.roamling.ui.beasts.habitatOf
import app.roamling.ui.components.Btn
import app.roamling.ui.components.Sheet
import app.roamling.ui.theme.C
import app.roamling.ui.theme.T
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

/** What a postcard shows. */
sealed class Card(val key: String) {
    data object Stats : Card("stats")
    data object Team : Card("team")
    data class Buddy(val id: Long) : Card("pet:$id")
    data class Trip(val id: Long) : Card("walk:$id")
    data object Beasts : Card("beasts")
    data object Stamps : Card("stamps")
    data object Den : Card("den")
    data object Chronicle : Card("chronicle")

    companion object {
        fun of(key: String): Card? = when {
            key.startsWith("pet:") -> key.drop(4).toLongOrNull()?.let { Buddy(it) }
            key.startsWith("walk:") -> key.drop(5).toLongOrNull()?.let { Trip(it) }
            else -> listOf(Stats, Team, Beasts, Stamps, Den, Chronicle).firstOrNull { it.key == key }
        }
    }
}

/** Every card the progress can make now; [start] goes first when it is not among them. */
fun cardsFor(d: Data, start: String): List<Card> {
    val all = buildList {
        add(Card.Stats)
        if (d.team.isNotEmpty()) add(Card.Team)
        (d.team.filter { it.stage > 0 } + d.den.filter { it.stage == 3 }.sortedByDescending { it.meters }).distinctBy { it.id }.take(8).forEach { add(Card.Buddy(it.id)) }
        d.walks.firstOrNull()?.let { add(Card.Trip(it.id)) }
        d.walks.maxByOrNull { it.meters }?.takeIf { it.id != d.walks.firstOrNull()?.id }?.let { add(Card.Trip(it.id)) }
        if (d.met.isNotEmpty()) add(Card.Beasts)
        if (d.stamps.isNotEmpty()) add(Card.Stamps)
        if (d.den.isNotEmpty()) add(Card.Den)
        if (d.s.seals.isNotEmpty() || d.letters > 0) add(Card.Chronicle)
    }
    val first = Card.of(start) ?: return all
    return if (all.any { it.key == first.key }) all else listOf(first) + all
}

/** Paints the 1080×1350 postcards: a trail map's paper with a dashed path round it, pixel letters and the companions' own pictures. */
class Painter(private val ctx: Context, private val d: Data, private val routes: Map<Long, List<Waypoint>>) {
    private val W = 1080; private val H = 1350
    private val pixel: Typeface = ResourcesCompat.getFont(ctx, R.font.press_start) ?: Typeface.MONOSPACE
    private val bold: Typeface = ResourcesCompat.getFont(ctx, R.font.nunito_bold) ?: Typeface.DEFAULT_BOLD
    private val body: Typeface = ResourcesCompat.getFont(ctx, R.font.nunito_regular) ?: Typeface.DEFAULT
    private val hand: Typeface = ResourcesCompat.getFont(ctx, R.font.caveat_bold) ?: Typeface.DEFAULT
    private val ink = 0xFF2E2618.toInt(); private val mute = 0xFF857A62.toInt(); private val moss = 0xFF4F7A45.toInt(); private val gold = 0xFFC98A2E.toInt()
    private val paper = 0xFFF6EEDA.toInt(); private val paper2 = 0xFFEDE1C4.toInt(); private val trail = 0xFF8A5A34.toInt(); private val line = 0xFFC9B48A.toInt()

    @SuppressLint("DiscouragedApi")
    private fun bmp(name: String): Bitmap? {
        val id = ctx.resources.getIdentifier(name, "drawable", ctx.packageName)
        return if (id == 0) null else BitmapFactory.decodeResource(ctx.resources, id, BitmapFactory.Options().apply { inScaled = false })
    }

    private fun petName(p: Pet) = if (p.stage == 0) "egg_" + p.eggTaste.lowercase().ifEmpty { "green" } else "pet_${p.species}_${Growth.frame(p)}"

    /** A picture standing on [bottom] (or hanging from it with [top]), [h] tall. */
    private fun Canvas.pic(name: String, cx: Float, bottom: Float, h: Float, alpha: Int = 255, top: Boolean = false, shadow: Boolean = false, flip: Boolean = false) {
        val b = bmp(name) ?: return
        val w = h * b.width / b.height
        val y = if (top) bottom else bottom - h
        val p = Paint().apply { isFilterBitmap = true; this.alpha = alpha
            if (shadow) colorFilter = PorterDuffColorFilter(0x40574A33, PorterDuff.Mode.SRC_IN) }
        if (flip) { save(); scale(-1f, 1f, cx, 0f) }
        drawBitmap(b, null, RectF(cx - w / 2, y, cx + w / 2, y + h), p)
        if (flip) restore()
    }

    private fun Canvas.text(s: String, x: Float, y: Float, size: Float, color: Int, face: Typeface, align: Paint.Align = Paint.Align.LEFT, maxW: Float = 0f) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = size; this.color = color; typeface = face; textAlign = align }
        if (maxW > 0) while (p.measureText(s) > maxW && p.textSize > 14f) p.textSize -= 1f
        drawText(s, x, y, p)
    }

    private fun Canvas.block(s: String, x: Float, y: Float, width: Int, size: Float, color: Int, face: Typeface, center: Boolean = false): Float {
        val p = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = size; this.color = color; typeface = face }
        val l = StaticLayout.Builder.obtain(s, 0, s.length, p, width).setAlignment(if (center) Layout.Alignment.ALIGN_CENTER else Layout.Alignment.ALIGN_NORMAL).setLineSpacing(0f, 1.08f).build()
        save(); translate(x, y); l.draw(this); restore()
        return y + l.height
    }

    private fun Canvas.panel(r: RectF, fill: Int = 0xFFFBF5E4.toInt()) {
        drawRoundRect(r, 18f, 18f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fill })
        drawRoundRect(r, 18f, 18f, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 4f; color = line })
    }

    /** The paper every card shares: a dashed trail round the edge, a compass in the corner, the signature at the bottom. */
    private fun frame(c: Canvas, title: String, sub: String?) {
        c.drawColor(paper)
        // faint map lines, as on the dock's strip
        val faint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x14000000; strokeWidth = 2f }
        var x = -200f; while (x < W) { c.drawLine(x, 0f, x + 400f, H.toFloat(), faint); x += 140f }
        val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 7f; color = trail; pathEffect = DashPathEffect(floatArrayOf(26f, 18f), 0f); strokeCap = Paint.Cap.ROUND }
        c.drawRoundRect(RectF(28f, 28f, W - 28f, H - 28f), 34f, 34f, edge)
        c.pic("ic_compass", W - 92f, 44f, 92f, top = true)
        c.text(title, 70f, 118f, 46f, ink, pixel, maxW = W - 230f)
        if (sub != null) c.text(sub, 72f, 172f, 34f, moss, bold, maxW = W - 230f)
        c.pic("ic_boots", 92f, H - 58f, 52f)
        c.text("ROAMLING", 128f, H - 70f, 22f, mute, pixel)
        c.text(tr("Попутчики · прогулки сквозь туман", "Roamling · walks through the fog"), W - 70f, H - 68f, 28f, mute, body, Paint.Align.RIGHT)
    }

    /** A picture with a painted sky behind its see-through top, cropped from [from] down to its bottom. */
    private fun Canvas.scene(name: String, r: RectF, from: Float = 0f, side: Float = 0f) {
        save(); clipPath(Path().apply { addRoundRect(r, 22f, 22f, Path.Direction.CW) })
        drawRect(r, Paint().apply { shader = LinearGradient(0f, r.top, 0f, r.bottom, 0xFF8EC3E6.toInt(), 0xFFF3E2C0.toInt(), Shader.TileMode.CLAMP) })
        bmp(name)?.let { b ->
            val src = Rect((b.width * side).roundToInt(), (b.height * from).roundToInt(), (b.width * (1 - side)).roundToInt(), b.height)
            val k = r.width() / src.width()
            val h = src.height() * k
            drawBitmap(b, src, RectF(r.left, r.bottom - h, r.right, r.bottom), Paint().apply { isFilterBitmap = true })
        }
        restore()
        drawRoundRect(r, 22f, 22f, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 6f; color = trail })
    }

    private fun Canvas.hearts(n: Double, cx: Float, y: Float, size: Float) {
        val full = (n / 2).roundToInt().coerceIn(0, 5)
        val x0 = cx - (5 * size + 4 * 8f) / 2 + size / 2
        for (i in 0 until 5) pic("ic_heart", x0 + i * (size + 8f), y, size, alpha = if (i < full) 255 else 60, top = true)
    }

    suspend fun render(card: Card): Bitmap = withContext(Dispatchers.Default) {
        val b = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        when (card) {
            Card.Stats -> stats(c)
            Card.Team -> team(c)
            is Card.Buddy -> d.byId[card.id]?.let { companion(c, it) } ?: stats(c)
            is Card.Trip -> d.walks.firstOrNull { it.id == card.id }?.let { trip(c, it) } ?: stats(c)
            Card.Beasts -> beasts(c)
            Card.Stamps -> stamps(c)
            Card.Den -> den(c)
            Card.Chronicle -> chronicle(c)
        }
        b
    }

    // ------------------------------------------------------------------ the cards

    private fun stats(c: Canvas) {
        val first = d.walks.minOfOrNull { dayOf(it.start) } ?: d.today
        val days = (d.today - first + 1).toInt()
        frame(c, tr("ИТОГИ ПУТИ", "THE ROAD SO FAR"), tr("${count(days, "день", "дня", "дней", "day", "days")} в пути · с ${fmtDayYear(first)}", "${count(days, "day", "days", "days", "day", "days")} on the road · since ${fmtDayYear(first)}"))
        val steps = d.walks.sumOf { it.steps.toLong() }
        val tiles = listOf(
            Triple("boots", fmt1(d.km), tr("км пройдено", "km walked")),
            Triple("map", fmt(d.walks.size), tr("прогулок", "walks")),
            Triple("cloud", fmt(d.patches), tr("клочков тумана", "fog patches")),
            Triple("paw", fmt(steps), tr("шагов", "steps")),
            Triple("book", "${d.met.size}/${SPECIES.size}", tr("видов встречено", "species met")),
            Triple("gift", fmt(d.finds.size), tr("находок", "finds")),
        )
        val tw = (W - 140f - 2 * 20f) / 3; val th = 200f
        tiles.forEachIndexed { i, (ic, v, l) ->
            val x = 70f + (i % 3) * (tw + 20f); val y = 220f + (i / 3) * (th + 20f)
            c.panel(RectF(x, y, x + tw, y + th))
            c.pic("ic_$ic", x + tw / 2, y + 20f, 66f, top = true)
            c.text(v, x + tw / 2, y + 138f, 38f, ink, pixel, Paint.Align.CENTER, tw - 24f)
            c.text(l, x + tw / 2, y + 180f, 28f, mute, body, Paint.Align.CENTER, tw - 20f)
        }
        // twelve weeks of walking: a cell a day, greener when longer
        val weeks = 12; val cell = 56f; val gap = 10f; val ch = 26f
        val x0 = (W - (weeks * cell + (weeks - 1) * gap)) / 2; val y0 = 712f
        c.text(tr("12 недель прогулок", "12 weeks of walks"), W / 2f, y0 - 20f, 30f, mute, body, Paint.Align.CENTER)
        val start = weekStart(d.today) - (weeks - 1) * 7L
        val byDay = d.days.associate { it.day to it.meters }
        val greens = intArrayOf(0xFFE4D9BE.toInt(), 0xFFC9DDB6.toInt(), 0xFF9CC27F.toInt(), 0xFF6E9F55.toInt(), 0xFF4A7A3A.toInt())
        for (w in 0 until weeks) for (k in 0..6) {
            val day = start + w * 7 + k; if (day > d.today) continue
            val m = byDay[day] ?: 0.0
            val lvl = when { m < 50 -> 0; m < 1500 -> 1; m < 3500 -> 2; m < 7000 -> 3; else -> 4 }
            val top = y0 + k * (ch + 7f)
            c.drawRoundRect(RectF(x0 + w * (cell + gap), top, x0 + w * (cell + gap) + cell, top + ch), 6f, 6f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = greens[lvl] })
        }
        // the backpack, standing in a row
        val team = d.team.ifEmpty { d.den.take(3) }
        val step = (W - 200f) / max(1, team.size)
        team.forEachIndexed { i, p ->
            val cx = 100f + step * (i + .5f)
            c.drawOval(RectF(cx - 90f, 1140f, cx + 90f, 1166f), Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x22574A33 })
            c.pic(petName(p), cx, 1156f, if (i == 0) 200f else 170f, flip = i == 2)
            c.text(p.name, cx, 1204f, 30f, ink, bold, Paint.Align.CENTER, step - 10f)
        }
    }

    private fun team(c: Canvas) {
        frame(c, tr("МОЙ РЮКЗАК", "MY BACKPACK"), tr("вожак и двое спутников", "a leader and two companions"))
        val r = RectF(70f, 210f, W - 70f, 1010f)
        val side = .1f
        c.scene("bench_bg", r, .5f, side)
        // the picture is wider than the card shows: positions as fractions of the whole picture
        val iw = r.width() / (1 - 2 * side); val ih = iw * 1.5f
        fun px(fx: Float) = r.left + (fx - side) * iw
        fun py(fy: Float) = r.bottom - (1f - fy) * ih
        val seats = listOf(.525f to .23f, .325f to .19f, .725f to .19f)
        d.eggs.take(3).forEachIndexed { i, e -> c.pic(petName(e), px(.165f + i * .05f), py(.69f), iw * .11f) }
        d.team.withIndex().sortedByDescending { it.index }.forEach { (i, p) ->
            val (fx, fs) = seats.getOrElse(i) { .5f to .2f }
            c.pic(petName(p), px(fx), py(.72f), iw * fs, flip = i == 2)
        }
        var yy = r.bottom + 70f
        val names = d.team.joinToString("  ·  ") { it.name }
        c.text(names, W / 2f, yy, 40f, ink, bold, Paint.Align.CENTER, W - 160f); yy += 56f
        val km = d.team.sumOf { it.meters } / 1000
        c.text(tr("вместе ${fmt1(d.leader?.meters?.div(1000) ?: 0.0)} км с вожаком · яиц в карманах ${d.eggs.size}", "${fmt1(d.leader?.meters?.div(1000) ?: 0.0)} km with the leader · eggs in pockets ${d.eggs.size}"),
            W / 2f, yy, 32f, mute, body, Paint.Align.CENTER, W - 160f)
        if (km > 0) c.text(tr("всего пронесли ${fmt1(km)} км", "carried ${fmt1(km)} km in all"), W / 2f, yy + 50f, 32f, moss, bold, Paint.Align.CENTER)
    }

    private fun companion(c: Canvas, p: Pet) {
        val sp = Catalog[p.species]
        val form = if (p.stage == 3) sp.forms[p.form.coerceAtLeast(0)].name else listOf(tr("яйцо", "egg"), tr("малыш", "baby"), tr("подросток", "teen"), "")[p.stage]
        frame(c, p.name.uppercase(), "${sp.name} · ${form.lowercase()}")
        val r = RectF(70f, 210f, W - 70f, 210f + (W - 140f) * .62f)
        c.scene(habitatOf(p, sp), r, .08f)
        c.drawOval(RectF(W / 2f - 200f, r.bottom - 64f, W / 2f + 200f, r.bottom - 20f), Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x33000000 })
        c.pic(petName(p), W / 2f, r.bottom - 30f, 470f)
        if (p.shiny) for (k in 0 until 6) c.pic("place_sparkle", r.left + 120f + k * 150f, r.top + 40f + (k % 2) * 70f, 60f, top = true)
        var y = r.bottom + 40f
        val days = (d.today - dayOf(p.born) + 1).toInt().coerceAtLeast(1)
        val cols = listOf(fmt1(p.meters / 1000) to tr("км вместе", "km together"), fmt(days) to count(days, "день", "дня", "дней", "day", "days").substringAfter(' '),
            (if (p.id == d.s.team.firstOrNull()) tr("вожак", "leader") else if (p.id in d.s.team) tr("в рюкзаке", "in pack") else tr("в логове", "in den")) to tr("сейчас", "now"))
        val cw = (W - 140f) / 3
        cols.forEachIndexed { i, (v, l) ->
            val x = 70f + cw * i
            c.panel(RectF(x + 8f, y, x + cw - 8f, y + 150f))
            c.text(v, x + cw / 2, y + 72f, if (i == 2) 40f else 36f, ink, if (i == 2) bold else pixel, Paint.Align.CENTER, cw - 40f)
            c.text(l, x + cw / 2, y + 120f, 28f, mute, body, Paint.Align.CENTER, cw - 30f)
        }
        y += 180f
        c.hearts(p.bond, W / 2f, y, 52f)
        y += 82f
        // the weathers it walked in most
        val skies = parseMap(p.skies).entries.mapNotNull { (k, v) -> runCatching { Sky.valueOf(k) }.getOrNull()?.let { it to v } }.sortedByDescending { it.second }.take(4)
        if (skies.isNotEmpty()) {
            val sw = 100f; val x0 = W / 2f - (skies.size * sw + (skies.size - 1) * 110f) / 2 + sw / 2
            skies.forEachIndexed { i, (sky, m) ->
                val cx = x0 + i * (sw + 110f)
                c.pic("stamp_${sky.stamp}", cx, y, sw, top = true)
                c.text("${sky.title.lowercase()} · ${fmt1(m / 1000)} ${tr("км", "km")}", cx, y + sw + 32f, 27f, mute, body, Paint.Align.CENTER, sw + 100f)
            }
        } else c.text(tr("Первые прогулки ещё впереди", "The first walks are still ahead"), W / 2f, y + 60f, 40f, mute, hand, Paint.Align.CENTER)
    }

    private fun trip(c: Canvas, w: Walk) {
        val sky = runCatching { Sky.valueOf(w.sky) }.getOrNull()
        frame(c, fmtDayYear(dayOf(w.start)).uppercase(), tr("прогулка", "a walk") + (sky?.let { " · " + it.title.lowercase() } ?: "") + (if (w.temp > -200) " · ${w.temp.roundToInt()}°" else ""))
        val r = RectF(70f, 210f, W - 70f, 900f)
        c.panel(r, paper2)
        val route = routes[w.id].orEmpty()
        if (route.size >= 2) {
            val xs = route.map { Grid.mx(it.lon) }; val ys = route.map { Grid.my(it.lat) }
            val x0 = xs.min(); val x1 = xs.max(); val y0 = ys.min(); val y1 = ys.max()
            val pad = 70f
            val k = minOf((r.width() - 2 * pad) / max(x1 - x0, 1e-12), (r.height() - 2 * pad) / max(y1 - y0, 1e-12)).toFloat()
            val ox = r.left + (r.width() - (x1 - x0).toFloat() * k) / 2; val oy = r.top + (r.height() - (y1 - y0).toFloat() * k) / 2
            fun px(i: Int) = ox + (xs[i] - x0).toFloat() * k
            fun py(i: Int) = oy + (ys[i] - y0).toFloat() * k
            // a soft cleared band under the path, like the fog melting round it
            val path = Path().apply { route.indices.forEach { if (it == 0) moveTo(px(it), py(it)) else lineTo(px(it), py(it)) } }
            c.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 46f; color = 0x66FFFFFF; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND })
            c.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 11f; color = trail; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
                pathEffect = DashPathEffect(floatArrayOf(30f, 16f), 0f) })
            c.drawCircle(px(0), py(0), 16f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF4F8A5B.toInt() })
            val last = route.lastIndex
            d.leader?.let { c.pic(petName(it), px(last), py(last) + 10f, 150f) } ?: c.drawCircle(px(last), py(last), 16f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFC4533A.toInt() })
        } else d.leader?.let { c.pic(petName(it), r.centerX(), r.bottom - 60f, 360f) }
        if (sky != null) { c.save(); c.rotate(8f, r.right - 110f, r.top + 110f); c.pic("stamp_${sky.stamp}", r.right - 110f, r.top + 20f, 170f, top = true); c.restore() }
        var y = r.bottom + 30f
        val cols = listOf(fmt1(w.meters / 1000) to tr("км", "km"), dur(((w.end - w.start) / 1000).toInt()) to tr("в пути", "on the way"),
            fmt(w.patches) to tr("новых клочков", "new patches"), fmt(w.steps) to tr("шагов", "steps"))
        val cw = (W - 140f) / 4
        cols.forEachIndexed { i, (v, l) ->
            val x = 70f + cw * i
            c.text(v, x + cw / 2, y + 60f, 36f, ink, pixel, Paint.Align.CENTER, cw - 16f)
            c.text(l, x + cw / 2, y + 106f, 27f, mute, body, Paint.Align.CENTER, cw - 10f)
        }
        y += 150f
        // what the walk tasted of
        val m = parseMap(w.tastes).filterKeys { k -> Taste.entries.any { it.name == k } }
        val total = m.values.sum()
        if (total > 0) {
            var x = 90f; val bw = W - 180f
            Taste.entries.forEach { t -> val v = m[t.name] ?: 0.0; if (v > 0) {
                val ww = (v / total * bw).toFloat()
                c.drawRect(RectF(x, y, x + ww, y + 22f), Paint().apply { color = (0xFF000000 or t.color).toInt() }); x += ww } }
            y += 50f
            val shown = Taste.entries.filter { (m[it.name] ?: 0.0) > 0 }.sortedByDescending { m[it.name] }.take(4)
            val sw = bw / shown.size
            shown.forEachIndexed { i, t ->
                val cx = 90f + sw * (i + .5f)
                c.pic("ic_${t.icon}", cx - 70f, y + 46f, 44f)
                c.text("${t.title} ${((m[t.name] ?: 0.0) / total * 100).roundToInt()}%", cx - 40f, y + 36f, 28f, ink, bold, maxW = sw - 60f)
            }
            y += 80f
        }
        val finds = d.finds.filter { it.at in w.start..w.end }.take(6)
        if (finds.isNotEmpty()) {
            c.text(tr("Находки:", "Finds:"), 90f, y + 50f, 30f, mute, body)
            finds.forEachIndexed { i, f -> c.pic("find_${f.item}", 280f + i * 100f, y + 70f, 80f) }
        }
    }

    private fun beasts(c: Canvas) {
        frame(c, tr("БЕСТИАРИЙ", "BESTIARY"), tr("${d.met.size} из ${SPECIES.size} видов · взрослых форм ${d.forms.size}", "${d.met.size} of ${SPECIES.size} species · grown forms ${d.forms.size}"))
        // the best-grown one of each species met first, then shadows of those still in the fog
        val best = d.pets.filter { it.stage > 0 }.groupBy { it.species }.mapValues { (_, l) -> l.maxWith(compareBy({ it.stage }, { it.meters })) }
        val met = SPECIES.filter { it.id in best }
        val unmet = SPECIES.filter { it.id !in best && it.group != app.roamling.domain.Group.LEGEND }
        val cols = 5; val rows = 5; val cell = (W - 140f) / cols
        val shown = (met.map { it.id to true } + unmet.map { it.id to false }).take(cols * rows)
        shown.forEachIndexed { i, (id, seen) ->
            val cx = 70f + cell * (i % cols + .5f); val top = 220f + (i / cols) * 168f
            c.drawRoundRect(RectF(cx - cell / 2 + 8f, top, cx + cell / 2 - 8f, top + 156f), 16f, 16f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (seen) 0xFFFBF5E4.toInt() else paper2 })
            if (seen) c.pic(petName(best.getValue(id)), cx, top + 146f, 136f)
            else c.pic("pet_${id}_0", cx, top + 140f, 110f, shadow = true)
        }
        val left = SPECIES.size - met.size
        c.text(if (left > 0) tr("ещё $left ждут в тумане", "$left more wait in the fog") else tr("все виды встречены!", "every species met!"), W / 2f, 1150f, 34f, moss, bold, Paint.Align.CENTER)
    }

    private fun stamps(c: Canvas) {
        frame(c, tr("МАРКИ ПОГОДЫ", "WEATHER STAMPS"), tr("${d.stamps.size} из 12 · каждая прогулка – марка своей погоды", "${d.stamps.size} of 12 · every walk gets its weather's stamp"))
        val grey = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
        val cell = (W - 140f) / 3
        Sky.entries.forEachIndexed { i, sky ->
            val cx = 70f + cell * (i % 3 + .5f); val top = 220f + (i / 3) * 255f
            val n = d.stamps[sky] ?: 0
            bmp("stamp_${sky.stamp}")?.let { b ->
                val s = 180f
                c.save(); c.rotate(((i * 37) % 11 - 5).toFloat(), cx, top + s / 2)
                c.drawBitmap(b, null, RectF(cx - s / 2, top, cx + s / 2, top + s), Paint().apply { isFilterBitmap = true; if (n == 0) { colorFilter = grey; alpha = 70 } })
                c.restore()
            }
            c.text(if (n > 0) "${sky.title} · $n" else sky.title, cx, top + 222f, 28f, if (n > 0) ink else mute, if (n > 0) bold else body, Paint.Align.CENTER, cell - 20f)
        }
    }

    private fun den(c: Canvas) {
        frame(c, tr("ЛОГОВО", "THE DEN"), tr("под старым дубом · здесь ${count(d.den.size, "попутчик", "попутчика", "попутчиков", "companion", "companions")}", "under the old oak · ${count(d.den.size, "companion", "companions", "companions", "companion", "companions")} here"))
        val r = RectF(70f, 210f, W - 70f, 1150f)
        val from = .3f
        c.scene("den_bg", r, from)
        val ih = r.width() * 1.5f
        fun py(fy: Float) = r.bottom - (1f - fy) * ih
        d.den.take(12).forEachIndexed { i, p ->
            val row = i / 4; val col = i % 4
            val s = r.width() * (.19f - row * .02f)
            val fx = .16f + col * .225f + (if (row % 2 == 1) .06f else 0f)
            val fy = listOf(.80f, .88f, .73f)[row]
            c.pic(petName(p), r.left + r.width() * fx, py(fy), s, flip = col % 2 == 1)
        }
        val grown = d.den.count { it.stage == 3 }
        c.text(tr("взрослых $grown · малышей и подростков ${d.den.size - grown}", "grown $grown · young ${d.den.size - grown}"), W / 2f, 1218f, 34f, ink, bold, Paint.Align.CENTER)
    }

    private fun chronicle(c: Canvas) {
        frame(c, tr("БОЛЬШАЯ КАРТА", "THE GREAT MAP"), tr("районов проснулось ${d.s.seals.size} · писем ${d.letters}/12", "districts awake ${d.s.seals.size} · letters ${d.letters}/12"))
        val r = RectF(70f, 210f, W - 70f, 210f + (W - 140f) * .42f)
        c.scene("prologue_map", r, .25f)
        // the five spirits of the districts: those whose district woke stand in colour, with how many they sealed
        val woke = d.s.seals.values.mapNotNull { v -> runCatching { Spirit.valueOf(v.substringBefore('|')) }.getOrNull() }.groupingBy { it }.eachCount()
        val cw = (W - 140f) / Spirit.entries.size
        var y = r.bottom + 40f
        Spirit.entries.forEachIndexed { i, sp ->
            val cx = 70f + cw * (i + .5f)
            val n = woke[sp] ?: 0
            c.pic(sp.art + "_0", cx, y + 170f, 150f, shadow = n == 0)
            if (n > 0) c.pic("seal_${sp.seal}", cx + 62f, y + 4f, 64f, top = true)
            c.block(if (n > 0) sp.title else "?", cx - cw / 2 + 4f, y + 186f, (cw - 8f).toInt(), 25f, if (n > 0) ink else mute, if (n > 0) bold else body, center = true)
        }
        y += 290f
        // the letters: an envelope for each that came
        val n = d.letters
        val ew = 104f; val gap = 24f; val x0 = W / 2f - (6 * ew + 5 * gap) / 2 + ew / 2
        for (i in 0 until 12) c.pic("find_postcard", x0 + (i % 6) * (ew + gap), y + (i / 6) * 112f + 96f, 96f, alpha = if (i < n) 255 else 50)
        c.text(tr("письма Первого путника · ${n} из 12", "the First Walker's letters · $n of 12"), W / 2f, y + 270f, 32f, moss, bold, Paint.Align.CENTER)
    }
}

/** Saves the card and opens the system share sheet. */
fun share(ctx: Context, b: Bitmap) {
    val dir = File(ctx.cacheDir, "share").apply { mkdirs() }
    val f = File(dir, "roamling.png"); f.outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
    val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
    ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), tr("Поделиться открыткой", "Share the card")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

/** The card shelf: swipe between the cards, share the one shown. */
@Composable
fun ShareSheet(start: String, d: Data, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val c = ctx.container
    val cards = remember(d, start) { cardsFor(d, start) }
    val first = cards.indexOfFirst { it.key == start }.coerceAtLeast(0)
    val pager = rememberPagerState(first) { cards.size }
    val scope = rememberCoroutineScope()
    val routes by produceState<Map<Long, List<Waypoint>>?>(null, cards) {
        value = cards.filterIsInstance<Card.Trip>().associate { it.id to c.dao.route(it.id) }
    }
    Sheet(onDone) {
        Text(tr("Открытки", "Postcards"), style = T.h2)
        Text(tr("Листайте – выберите, чем поделиться.", "Swipe to pick what to share."), style = T.small)
        val r = routes
        HorizontalPager(pager, Modifier.fillMaxWidth().padding(top = 10.dp), pageSpacing = 12.dp, contentPadding = PaddingValues(horizontal = 36.dp)) { i ->
            val img by produceState<Bitmap?>(null, cards[i], r) { if (r != null) value = Painter(ctx, d, r).render(cards[i]) }
            Box(Modifier.fillMaxWidth().aspectRatio(1080f / 1350f).clip(RoundedCornerShape(10.dp)).background(C.card2), contentAlignment = Alignment.Center) {
                img?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxWidth()) }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.Center) {
            cards.indices.forEach { i -> Box(Modifier.padding(3.dp).size(if (i == pager.currentPage) 9.dp else 6.dp).clip(CircleShape).background(if (i == pager.currentPage) C.acc else C.line)) }
        }
        Btn(tr("Поделиться", "Share"), Modifier.fillMaxWidth().padding(top = 12.dp), enabled = r != null) {
            val rr = r ?: return@Btn
            scope.launch { share(ctx, Painter(ctx, d, rr).render(cards[pager.currentPage])) }
        }
        Spacer(Modifier.height(8.dp))
    }
}
