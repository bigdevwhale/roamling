package app.roamling.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.roamling.domain.Rand
import app.roamling.ui.art.Art
import app.roamling.ui.art.Pic
import kotlin.math.roundToInt

/**
 * The goal, played rather than told: a town street under fog that the finger wipes away. Past [goal] of the picture
 * the district's spirit comes out of the fog and presses its seal. [onProgress] gets the share cleared, 0..1.
 */
@Composable
fun FogScratch(pic: String, goal: Float, modifier: Modifier = Modifier, onProgress: (Float) -> Unit) {
    val ctx = LocalContext.current
    val img = Art.get(ctx, pic)
    val strokes = remember { mutableStateListOf<Offset>() }
    // a 20 × 15 grid tells how much of the picture is clear
    val cols = 20; val rows = 15
    val cleared = remember { BooleanArray(cols * rows) }
    var share by remember { mutableStateOf(0f) }
    val woke = share >= goal
    val spirit by animateFloatAsState(if (woke) 1f else 0f, tween(900), label = "spirit")
    Box(modifier) {
        Canvas(Modifier.fillMaxSize().graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen).pointerInput(Unit) {
            detectDragGestures(onDragStart = { strokes.add(it) }) { ch, _ ->
                val p = ch.position
                strokes.add(p)
                val r = size.width * .09f
                for (cx in 0 until cols) for (cy in 0 until rows) {
                    val x = (cx + .5f) * size.width / cols; val y = (cy + .5f) * size.height / rows
                    if ((x - p.x) * (x - p.x) + (y - p.y) * (y - p.y) < r * r) cleared[cy * cols + cx] = true
                }
                share = cleared.count { it } / cleared.size.toFloat()
                onProgress(share)
            }
        }) {
            val w = size.width; val h = size.height
            if (img != null) drawCropped(img)
            // the fog: a pale wash with darker drifts, wiped away where the finger went; once the spirit wakes it thins everywhere
            val fogAlpha = 1f - spirit * .75f
            drawRect(Color(0xFFD9DDDF).copy(alpha = .97f * fogAlpha))
            val r = Rand(5)
            repeat(26) {
                val c = Offset(r.next() * w, r.next() * h); val rad = w * (.08f + r.next() * .12f)
                drawCircle(Brush.radialGradient(listOf(Color(0x55707C88).copy(alpha = .33f * fogAlpha), Color.Transparent), c, rad), rad, c)
            }
            val rad = w * .09f
            strokes.forEach { p -> drawCircle(Brush.radialGradient(listOf(Color.Black, Color.Black, Color.Transparent), p, rad), rad, p, blendMode = BlendMode.DstOut) }
        }
        // the spirit comes out of the fog and stamps the seal
        if (spirit > 0f) Box(Modifier.align(Alignment.BottomCenter).graphicsLayer(alpha = spirit, translationY = (1 - spirit) * 60f)) {
            Pic("spirit_cross_5", 150.dp)
        }
        if (spirit > .6f) Pic("seal_4", 64.dp, Modifier.align(Alignment.TopEnd).offset((-12).dp, 12.dp).graphicsLayer(alpha = (spirit - .6f) / .4f, scaleX = 2f - spirit, scaleY = 2f - spirit))
    }
}
