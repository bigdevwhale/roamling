package app.roamling.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.roamling.BuildConfig
import app.roamling.domain.Catalog
import app.roamling.domain.Rand
import app.roamling.ui.art.Art
import app.roamling.ui.art.Creature
import app.roamling.ui.art.Pose
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/** The colour of [img] at a point (fractions of its size). */
fun sample(img: ImageBitmap, fx: Float, fy: Float): Color {
    val px = IntArray(1)
    img.readPixels(px, (fx * (img.width - 1)).roundToInt(), (fy * (img.height - 1)).roundToInt(), 1, 1)
    return Color(px[0])
}

/** Draws [img] to fill the whole canvas, cropping what doesn't fit (the middle stays). */
fun DrawScope.drawCropped(img: ImageBitmap) {
    val sc = max(size.width / img.width, size.height / img.height)
    val sw = (size.width / sc).roundToInt(); val sh = (size.height / sc).roundToInt()
    val sx = ((img.width - sw) / 2).coerceAtLeast(0); val sy = ((img.height - sh) / 2).coerceAtLeast(0)
    drawImage(img, IntOffset(sx, sy), IntSize(sw.coerceAtMost(img.width), sh.coerceAtMost(img.height)), IntOffset.Zero,
        IntSize(size.width.roundToInt(), size.height.roundToInt()), filterQuality = FilterQuality.Low)
}

/**
 * The Valley, alive: the picture cropped to fill, fog drifting in from the edges, fireflies over the meadow,
 * windows flickering, and the three companions walking the path across it.
 */
@Composable
fun LivingValley(modifier: Modifier, sky: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit = {}) {
    val ctx = LocalContext.current
    val img = Art.get(ctx, "prologue_valley")
    val tr by rememberInfiniteTransition(label = "valley").animateFloat(0f, 1f, infiniteRepeatable(tween(24_000, easing = LinearEasing), RepeatMode.Restart), label = "t")
    val t = if (BuildConfig.SHOTS) .3f else tr
    BoxWithConstraints(modifier) {
        val W = maxWidth; val H = maxHeight
        // where a point of the picture lands on screen after cropping
        // the whole picture across the width, standing on the bottom; above it the sky goes on in the picture's own colours
        val iw = img?.width?.toFloat() ?: 4f; val ih = img?.height?.toFloat() ?: 3f
        val sc = W.value / iw
        val top = H.value - ih * sc
        fun px(fx: Float) = (fx * iw * sc).dp
        fun py(fy: Float) = (top + fy * ih * sc).dp
        val clouds = remember { (0..5).map { Art.get(ctx, "cloud_$it") } }
        val skyTop = remember(img) { img?.let { sample(it, .5f, .01f) } ?: Color(0xFFE0606E) }
        Canvas(Modifier.fillMaxSize()) {
            val t0 = top.dp.toPx()
            drawRect(Brush.verticalGradient(listOf(skyTop.copy(red = skyTop.red * .85f, blue = (skyTop.blue * 1.15f).coerceAtMost(1f)), skyTop), 0f, t0 + 2f), size = androidx.compose.ui.geometry.Size(size.width, t0 + 2f))
            // clouds drifting over the sky
            listOf(0, 3, 1).forEachIndexed { i, n ->
                val cl = clouds[n] ?: return@forEachIndexed
                val cw = size.width * (.42f - i * .08f); val ch = cw * cl.height / cl.width
                val x = ((t * (1.4f + i * .5f) + i * .37f) % 1f) * (size.width + cw) - cw
                val y = t0 * (.38f + i * .17f)
                drawImage(cl, IntOffset.Zero, IntSize(cl.width, cl.height), IntOffset(x.roundToInt(), y.roundToInt()), IntSize(cw.roundToInt(), ch.roundToInt()),
                    alpha = .55f, colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(Color(0xFFFFD9C8), androidx.compose.ui.graphics.BlendMode.Modulate))
            }
            if (img != null) drawImage(img, IntOffset.Zero, IntSize(img.width, img.height), IntOffset(0, t0.roundToInt()), IntSize(size.width.roundToInt(), (img.height * size.width / img.width).roundToInt() + 2), filterQuality = FilterQuality.Low)
            val r = Rand(3)
            // fireflies
            repeat(18) { i ->
                val x0 = r.next(); val y0 = .55f + r.next() * .35f; val ph = r.next()
                val k = ((t * 6 + ph) % 1f)
                val a = sin(k * PI).toFloat().coerceAtLeast(0f)
                val x = size.width * (x0 + .02f * sin((t * 20 + i) * 1.3).toFloat()); val y = size.height * (y0 - .03f * k)
                drawCircle(Brush.radialGradient(listOf(Color(0xFFFFF2A0).copy(alpha = .9f * a), Color.Transparent), Offset(x, y), 9f), 9f, Offset(x, y))
            }
        }
        // the three companions walking the path, one after another
        Catalog.starters.forEachIndexed { i, sp ->
            val k = ((t * 2 + i * .2f) % 1f)
            val (fx, fy) = alongPath(k)
            val s = W * .095f
            // they appear at the cabin and fade out at the tea house, never stepping off the cliff
            val a = (minOf(k, 1f - k) / .06f).coerceIn(0f, 1f)
            Box(Modifier.offset(px(fx) - s / 2, py(fy) - s).size(s).graphicsLayer(alpha = a)) { Creature(sp.id, 0, s, pose = Pose.MOVE, phase = i * .3f) }
        }
        Box(Modifier.fillMaxSize().padding(bottom = (H.value - top).dp)) { sky() }
    }
}

/** The path through the valley picture, cabin to tea house, as points where a walker's feet stand (fractions of the picture). */
private val PATH = listOf(.06f to .675f, .16f to .66f, .27f to .645f, .36f to .66f, .46f to .675f, .55f to .67f, .62f to .645f, .69f to .62f, .76f to .605f, .80f to .595f)

private fun alongPath(k: Float): Pair<Float, Float> {
    val f = k * (PATH.size - 1)
    val i = f.toInt().coerceAtMost(PATH.size - 2)
    val u = f - i
    val (x0, y0) = PATH[i]; val (x1, y1) = PATH[i + 1]
    return (x0 + (x1 - x0) * u) to (y0 + (y1 - y0) * u)
}
