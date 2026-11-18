package app.roamling.ui.art

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.drawText
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import app.roamling.BuildConfig
import app.roamling.data.Pet
import app.roamling.domain.Growth
import app.roamling.domain.hash
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** Pictures from res/drawable-nodpi by name, decoded once. */
object Art {
    private val cache = HashMap<String, ImageBitmap?>()
    @SuppressLint("DiscouragedApi")
    @Synchronized fun get(ctx: Context, name: String): ImageBitmap? = cache.getOrPut(name) {
        val id = ctx.resources.getIdentifier(name, "drawable", ctx.packageName)
        if (id == 0) null else BitmapFactory.decodeResource(ctx.resources, id, BitmapFactory.Options().apply { inScaled = false })?.asImageBitmap()
    }
    fun pet(ctx: Context, kind: String, frame: Int) = get(ctx, "pet_${kind}_$frame")
}

/** A colour of its own for a species, for the drawing that stands in before its picture exists. */
fun tint(kind: String) = Color(0xFF000000 or (0x404040L + (hash(kind).toLong() and 0x7F7F7FL)))

/** What a creature is doing. */
enum class Pose { IDLE, MOVE, CHEER, SLEEP }

/** How a creature is shown: whole, or only a shadow (not met yet, or a wild one on the map). */
enum class Look { FULL, DIM, SHADOW, SILHOUETTE }

private val still = BuildConfig.SHOTS

/** Evening light: a blue dimming that deepens to night. */
fun nightFilter(n: Float): ColorFilter {
    val k = 1f - .45f * n
    return ColorFilter.colorMatrix(ColorMatrix(floatArrayOf(k * .92f, 0f, 0f, 0f, 0f, 0f, k * .95f, 0f, 0f, 0f, 0f, 0f, k * 1.08f, 0f, 8f * n, 0f, 0f, 0f, 1f, 0f)))
}

private val dimFilter = ColorFilter.colorMatrix(ColorMatrix(floatArrayOf(
    .42f, 0f, 0f, 0f, 0f, 0f, .42f, 0f, 0f, 0f, 0f, 0f, .55f, 0f, 8f, 0f, 0f, 0f, 1f, 0f)))
/** The tint of an unknown creature's outline: dark on light pages, a pale grey on dark ones, so it shows in both themes. */
private val shadowFilter get() = ColorFilter.tint(if (app.roamling.ui.theme.C.dark) Color(0xFF9AA0A8) else Color(0xFF141A22), BlendMode.SrcIn)

/** A companion as it looks now: its stage, its form, shiny or not. */
@Composable
fun PetArt(p: Pet, size: Dp, modifier: Modifier = Modifier, pose: Pose = Pose.IDLE, phase: Float = 0f, flip: Boolean = false, night: Float = 0f) {
    if (p.stage == 0) { Egg(p.eggTaste, size, modifier, phase); return }
    Creature(p.species, Growth.frame(p), size, modifier, shiny = p.shiny, phase = phase, flip = flip, pose = pose, night = night)
}

/** An egg that rocks a little now and then. */
@Composable
fun Egg(taste: String, size: Dp, modifier: Modifier = Modifier, phase: Float = 0f) {
    val ctx = LocalContext.current
    val img = Art.get(ctx, "egg_" + taste.lowercase().ifEmpty { "green" })
    val t by rememberInfiniteTransition(label = "egg").animateFloat(0f, 1f, infiniteRepeatable(tween(3400, easing = LinearEasing), RepeatMode.Restart), label = "t")
    val time = if (still) 0f else (t + phase) % 1f
    Canvas(modifier.size(size)) {
        val w = this.size.width; val h = this.size.height
        drawOval(Color(0x33000000), Offset(w * .25f, h * .92f), Size(w * .5f, h * .07f))
        if (img == null) { drawOval(Color(0xFFEDE3C8), Offset(w * .28f, h * .2f), Size(w * .44f, h * .74f)); return@Canvas }
        val rock = if (time > .8f) sin((time - .8f) / .2f * 4 * PI).toFloat() * 7f else 0f
        val box = minOf(w / img.width, h * .9f / img.height) * .8f
        val bw = (img.width * box).roundToInt(); val bh = (img.height * box).roundToInt()
        val foot = Offset(w / 2, h * .96f)
        withTransform({ rotate(rock, foot) }) {
            drawImage(img, IntOffset.Zero, IntSize(img.width, img.height), IntOffset(((w - bw) / 2).roundToInt(), (foot.y - bh).roundToInt()), IntSize(bw, bh), filterQuality = FilterQuality.Medium)
        }
    }
}

/** A creature drawn in a square of [size], standing on its bottom edge. It breathes, hops when cheering, dozes asleep. */
@Composable
fun Creature(kind: String, frame: Int, size: Dp, modifier: Modifier = Modifier, look: Look = Look.FULL, shiny: Boolean = false, phase: Float = 0f, flip: Boolean = false, pose: Pose = Pose.IDLE, night: Float = 0f) {
    if (look == Look.SHADOW) { Mystery(kind, size, modifier); return }
    val silhouette = look == Look.SILHOUETTE
    val ctx = LocalContext.current
    val img = Art.pet(ctx, kind, frame)
    val t by rememberInfiniteTransition(label = "pet").animateFloat(0f, 1f, infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Restart), label = "t")
    val time = if (still) .25f else (t + phase) % 1f
    Canvas(modifier.size(size)) { drawCreature(img, kind, look, shiny, time, flip, pose, night) }
}

fun DrawScope.drawCreature(img: ImageBitmap?, kind: String, look: Look, shiny: Boolean, time: Float, flip: Boolean = false, pose: Pose = Pose.IDLE, night: Float = 0f) {
    val w = size.width; val h = size.height
    if (look != Look.SHADOW) drawOval(Color(0x33000000), Offset(w * .2f, h * .93f), Size(w * .6f, h * .07f))
    val wave = sin(time * 2 * PI).toFloat()
    val (sx, sy, dy) = when (pose) {
        Pose.CHEER -> Triple(1f, 1f, -wave * wave * h * .07f)
        Pose.SLEEP -> Triple(1f + wave * .015f, 1f + wave * .02f, 0f)
        Pose.MOVE -> Triple(1f, 1f, -abs(wave) * h * .035f)
        else -> Triple(1f + wave * .012f, 1f - wave * .016f, 0f)
    }
    if (img == null) {
        drawOval(if (look == Look.SHADOW) Color(0xFF141A22) else tint(kind), Offset(w * .2f, h * .35f + dy), Size(w * .6f, h * .6f)); return
    }
    val box = minOf(w / img.width, h * .92f / img.height)
    val bw = (img.width * box).roundToInt(); val bh = (img.height * box).roundToInt()
    val foot = Offset(w / 2, h * .965f)
    withTransform({ translate(0f, dy); scale((if (flip) -1f else 1f) * sx, sy, foot) }) {
        drawImage(img, IntOffset.Zero, IntSize(img.width, img.height), IntOffset(((w - bw) / 2).roundToInt(), (foot.y - bh).roundToInt()), IntSize(bw, bh),
            alpha = if (look == Look.SHADOW || look == Look.SILHOUETTE) .5f else 1f, filterQuality = FilterQuality.Medium,
            colorFilter = when (look) { Look.SHADOW, Look.SILHOUETTE -> shadowFilter; Look.DIM -> dimFilter; else -> if (night > .05f) nightFilter(night) else null })
    }
    // a rare one keeps its colours; little sparkles twinkle around it instead
    if (shiny && look == Look.FULL) {
        val r = app.roamling.domain.Rand(hash(kind))
        repeat(5) { i ->
            val k = ((time + i * .2f) % 1f)
            val a = (sin(k * PI).toFloat()).coerceAtLeast(0f)
            val cx = w * (.18f + r.next() * .64f); val cy = h * (.15f + r.next() * .6f)
            val sz = w * (.025f + .02f * a)
            val gold = Color(0xFFFFE08A).copy(alpha = a)
            drawLine(gold, Offset(cx - sz, cy), Offset(cx + sz, cy), w * .012f)
            drawLine(gold, Offset(cx, cy - sz), Offset(cx, cy + sz), w * .012f)
        }
    }
    if (pose == Pose.SLEEP && !still) {
        val z = (time * 3) % 1f
        drawCircle(Color(0x88FFFFFF), w * .022f * (1 + z), Offset(w * (.7f + z * .08f), h * (.4f - z * .15f)))
    }
}

/** A small still picture of a creature, a dark shape when not met yet. */
@Composable
fun Mini(kind: String, size: Dp, modifier: Modifier = Modifier, dim: Boolean = false, frame: Int = 0, shiny: Boolean = false, silhouette: Boolean = false) {
    // not met yet: a dark outline in the bestiary, a puff of fog elsewhere
    if (dim && !silhouette) { Mystery(kind + frame, size, modifier); return }
    val ctx = LocalContext.current
    val img = Art.pet(ctx, kind, frame)
    Canvas(modifier.size(size)) {
        val w = this.size.width; val h = this.size.height
        if (img == null) { drawCircle(tint(kind).copy(alpha = if (dim) .3f else 1f), w * .36f, Offset(w / 2, h / 2)); return@Canvas }
        val box = minOf(w / img.width, h / img.height) * .94f
        val dw = img.width * box; val dh = img.height * box
        drawImage(img, IntOffset.Zero, IntSize(img.width, img.height), IntOffset(((w - dw) / 2).roundToInt(), ((h - dh) / 2).roundToInt()), IntSize(dw.roundToInt(), dh.roundToInt()),
            filterQuality = FilterQuality.Medium, colorFilter = if (dim) shadowFilter else null, alpha = if (dim) .35f else 1f)
    }
}

/**
 * Someone not met yet: a puff of fog with a question mark. No outline – the shape would give the creature away.
 * [seed] picks the cloud, so different unknowns look a little different.
 */
@Composable
fun Mystery(seed: String, size: Dp, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val img = Art.get(ctx, "cloud_${Math.floorMod(hash(seed), 6)}")
    val ink = app.roamling.ui.theme.C.mute
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    val q = measurer.measure("?", app.roamling.ui.theme.T.title.copy(color = ink, fontSize = androidx.compose.ui.unit.TextUnit(size.value * .3f, androidx.compose.ui.unit.TextUnitType.Sp)))
    Canvas(modifier.size(size)) {
        val w = this.size.width; val h = this.size.height
        if (img != null) {
            val dw = w * .9f; val dh = dw * img.height / img.width
            drawImage(img, IntOffset.Zero, IntSize(img.width, img.height), IntOffset(((w - dw) / 2).roundToInt(), ((h - dh) / 2).roundToInt()), IntSize(dw.roundToInt(), dh.roundToInt()),
                alpha = .9f, colorFilter = ColorFilter.tint(Color(0xFFB9BEC2), BlendMode.Modulate))
        } else drawCircle(Color(0x66B9BEC2), w * .36f, Offset(w / 2, h / 2))
        drawText(q, topLeft = Offset((w - q.size.width) / 2, (h - q.size.height) / 2))
    }
}

/** Any picture of res/drawable-nodpi by its full name (egg_green, find_coin, gear_scarf…), fitted into [size]. */
@Composable
fun Pic(name: String, size: Dp, modifier: Modifier = Modifier, alpha: Float = 1f, grey: Boolean = false) {
    val ctx = LocalContext.current
    val img = Art.get(ctx, name)
    Canvas(modifier.size(size)) {
        val w = this.size.width; val h = this.size.height
        if (img == null) { drawCircle(Color(0xFF8A6A3A).copy(alpha = alpha), w * .35f, Offset(w / 2, h / 2)); return@Canvas }
        val box = minOf(w / img.width, h / img.height)
        val dw = img.width * box; val dh = img.height * box
        drawImage(img, IntOffset.Zero, IntSize(img.width, img.height), IntOffset(((w - dw) / 2).roundToInt(), ((h - dh) / 2).roundToInt()), IntSize(dw.roundToInt(), dh.roundToInt()),
            alpha = alpha, filterQuality = FilterQuality.Medium, colorFilter = if (grey) ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }) else null)
    }
}

/** A pixel icon from the icons sheet (ic_<name>); [dim] greys it out. */
@Composable
fun Ico(name: String, size: Dp, modifier: Modifier = Modifier, dim: Boolean = false) = Pic("ic_$name", size, modifier, if (dim) .55f else 1f, dim)
