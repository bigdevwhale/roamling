package app.roamling.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.roamling.BuildConfig
import app.roamling.domain.tr
import app.roamling.ui.art.Ico
import app.roamling.ui.theme.C
import app.roamling.ui.theme.T
import kotlin.math.PI
import kotlin.math.sin

/**
 * The dock as a strip of a hiking map: a torn paper edge on top, faint contour lines, and a dashed trail
 * running through the four places like stops on a route; the chosen stop gets a little red flag.
 * In the middle a brass compass – «Walk»; it glows while a walk is on.
 */
@Composable
fun Dock(sel: Int, walking: Boolean, onSel: (Int) -> Unit, onWalk: () -> Unit) {
    val tabs = listOf(tr("Привал", "Rest stop") to "door", tr("Карта", "Map") to "map", tr("Бестиарий", "Bestiary") to "book", tr("Дневник", "Journal") to "scroll")
    val paper = C.card; val ink = C.ink; val line = C.line; val trail = C.wood; val acc = C.acc
    val t by rememberInfiniteTransition(label = "dock").animateFloat(0f, 1f, infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Restart), label = "p")
    val pulse = if (BuildConfig.SHOTS || !walking) 0f else t
    Box(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(top = 18.dp).drawBehind {
            // paper with a torn top edge
            val edge = Path().apply {
                moveTo(0f, 6.dp.toPx())
                var x = 0f; var k = 0
                while (x < size.width) { x += 9.dp.toPx(); lineTo(x, (if (k++ % 2 == 0) 2.dp else 7.dp).toPx()) }
                lineTo(size.width, size.height); lineTo(0f, size.height); close()
            }
            drawPath(edge, paper)
            drawPath(edge, Color(0x22000000), style = Stroke(1.dp.toPx()))
            // contour lines of a hill
            for (i in 0..2) {
                val c = Path(); val y0 = size.height * (.55f + i * .14f)
                c.moveTo(0f, y0)
                var x = 0f
                while (x <= size.width) { c.lineTo(x, y0 + sin(x / size.width * 2 * PI * 1.5 + i).toFloat() * 6.dp.toPx()); x += 6f }
                drawPath(c, line.copy(alpha = .55f), style = Stroke(1.dp.toPx()))
            }
            // the dashed trail through the stops
            val y = 30.dp.toPx()
            val p = Path(); p.moveTo(0f, y)
            var x = 0f
            while (x <= size.width) { p.lineTo(x, y + sin(x / size.width * 2 * PI * 2).toFloat() * 4.dp.toPx()); x += 4f }
            drawPath(p, trail.copy(alpha = .7f), style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(7.dp.toPx(), 6.dp.toPx()))))
        }.navigationBarsPadding().padding(top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.Top) {
            tabs.forEachIndexed { i, (label, ic) ->
                if (i == 2) Box(Modifier.weight(1.1f))
                val on = i == sel
                Column(Modifier.weight(1f).clickable(remember { MutableInteractionSource() }, null) { onSel(i) }, horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) {
                        Canvas(Modifier.size(46.dp)) {
                            drawCircle(paper, size.minDimension / 2)
                            drawCircle(if (on) acc else line, size.minDimension / 2, style = Stroke((if (on) 2.5f else 1.5f).dp.toPx()))
                        }
                        Ico(ic, if (on) 34.dp else 30.dp, dim = !on)
                        if (on) Canvas(Modifier.size(52.dp).offset(x = 14.dp, y = (-18).dp)) {
                            // a little red flag on a pin
                            val px = size.width * .5f; val top = size.height * .2f; val foot = size.height * .62f
                            drawLine(ink, Offset(px, top), Offset(px, foot), 1.5.dp.toPx())
                            val f = Path().apply { moveTo(px, top); lineTo(px + 11.dp.toPx(), top + 4.dp.toPx()); lineTo(px, top + 8.dp.toPx()); close() }
                            drawPath(f, Color(0xFFD0503C))
                        }
                    }
                    Text(label, style = T.tiny.copy(color = if (on) ink else C.mute, fontWeight = if (on) FontWeight.ExtraBold else FontWeight.SemiBold, fontSize = 11.sp), maxLines = 1)
                }
            }
        }
        // the compass
        Column(Modifier.align(Alignment.TopCenter).clickable(remember { MutableInteractionSource() }, null, onClick = onWalk), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(70.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(70.dp)) {
                    val r = size.minDimension / 2
                    if (pulse > 0f) drawCircle(acc.copy(alpha = .45f * (1 - pulse)), r * (.85f + .25f * pulse))
                    drawCircle(Color(0xFF8A5A22), r * .86f)
                    drawCircle(Color(0xFFE2B55A), r * .80f)
                    drawCircle(if (walking) acc else Color(0xFF2F3A4E), r * .68f)
                    for (k in 0 until 8) {
                        val a = k * PI / 4
                        val o = Offset(center.x + (r * .74f * sin(a)).toFloat(), center.y - (r * .74f * kotlin.math.cos(a)).toFloat())
                        drawCircle(Color(0xFF8A5A22), (if (k % 2 == 0) 2.2f else 1.3f).dp.toPx(), o)
                    }
                }
                Ico("boots", 38.dp)
            }
            Text(if (walking) tr("Идём", "Walking") else tr("Гулять", "Walk"), style = T.tiny.copy(color = if (walking) acc else ink, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp),
                modifier = Modifier.offset(y = (-4).dp))
        }
    }
}
