package app.roamling.ui.art

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.roamling.ui.theme.C
import app.roamling.ui.theme.T

/** A painted banner at the top of a screen: the picture, a soft shade on the left, the title and a line under it. */
@Composable
fun Banner(name: String, title: String, sub: String? = null, modifier: Modifier = Modifier, height: Dp = 128.dp, stage: (@Composable androidx.compose.foundation.layout.BoxScope.() -> Unit)? = null, trailing: (@Composable () -> Unit)? = null) {
    val img = Art.get(LocalContext.current, name)
    Box(modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(12.dp)).background(C.card2).border(1.5.dp, C.frame, RoundedCornerShape(12.dp))) {
        if (img != null) Image(img, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, filterQuality = FilterQuality.Low)
        stage?.invoke(this)
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color(0xCC1B1710), Color(0x661B1710), Color.Transparent, Color.Transparent))))
        val shadow = Shadow(Color(0xAA000000), Offset(0f, 3f), 6f)
        Column(Modifier.align(Alignment.CenterStart).padding(start = 16.dp, end = 70.dp)) {
            Text(title, style = T.title.copy(color = Color(0xFFFFF4DC), shadow = shadow))
            if (sub != null) Text(sub, style = T.smallB.copy(color = Color(0xFFF1E3C4), shadow = shadow), modifier = Modifier.padding(top = 4.dp))
        }
        if (trailing != null) Box(Modifier.align(Alignment.TopEnd).padding(8.dp)) { trailing() }
    }
}
