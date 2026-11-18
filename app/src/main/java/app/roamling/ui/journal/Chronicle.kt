package app.roamling.ui.journal

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.roamling.R
import app.roamling.domain.District
import app.roamling.domain.Spirit
import app.roamling.domain.Story
import app.roamling.domain.tr
import app.roamling.ui.Data
import app.roamling.ui.art.Ico
import app.roamling.ui.art.Mini
import app.roamling.ui.art.Pic
import app.roamling.ui.components.Btn
import app.roamling.ui.components.Meter
import app.roamling.ui.components.Popup
import app.roamling.ui.components.Section
import app.roamling.ui.components.card
import app.roamling.ui.theme.C
import app.roamling.ui.theme.T

/** The spirit's picture for a district: asleep in fog until sealed, then as it was the season it woke. */
fun spiritPic(sp: Spirit, seal: String?): String {
    if (seal == null) return "${sp.art}_0"
    val season = seal.split('|').getOrNull(1)?.toIntOrNull() ?: 2
    return "${sp.art}_${1 + (season + 3) % 4}"
}

/**
 * The chronicle: the town as chapters of the Valley's Great Map. Home and the district underfoot first, then the
 * sealed ones; under them the letters of the First Walker.
 */
@Composable
fun Chronicle(d: Data, here: Long?) {
    var letter by rememberSaveable { mutableIntStateOf(-1) }
    Text(tr("Город лежит в Пелене. Каждый район – глава: пройдите половину его клочков и встретьте там троих его обитателей – проснётся Дух места и поставит печать на Большую карту Долины. Первая глава легче: 40% и двое.",
        "The town lies under the Veil. Each district is a chapter: walk half its patches and meet three of its dwellers there – its spirit wakes and seals the Valley's Great Map. The first chapter is lighter: 40% and two."),
        style = T.small, modifier = Modifier.padding(top = 10.dp))
    if (d.s.finale > 0) Row(Modifier.fillMaxWidth().padding(top = 10.dp).card(color = C.goldSoft).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Mini("fogkeeper", 54.dp, frame = 2)
        Text(tr("Хранитель тумана вышел к вам. Туман вокруг стал тёплым.", "The Keeper of the fog came out to you. The fog around has grown warm."), style = T.bodyB, modifier = Modifier.padding(start = 10.dp))
    }
    val shown = (listOfNotNull(d.home, here) + d.s.seals.keys).distinct()
    if (shown.isEmpty()) Text(tr("Первая глава начнётся с первой прогулки.", "The first chapter begins with the first walk."), style = T.bodyB, modifier = Modifier.padding(top = 12.dp))
    var tale by rememberSaveable { mutableStateOf<String?>(null) }
    shown.forEach { k ->
        val miss = if (k == d.missing && !d.missedDone) d.missWalked else -1
        DistrictCard(d.district(k), d.s.seals[k], k == d.home, k == here, miss) { tale = it }
    }
    tale?.let { sp -> val s = Spirit.valueOf(sp); Popup({ tale = null }) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Pic("${s.art}_5", 150.dp) }
        Text(s.title, style = T.h2)
        Text("«${s.tale}»", style = T.h3.copy(fontFamily = FontFamily(androidx.compose.ui.text.font.Font(R.font.caveat_medium)), fontSize = T.h2.fontSize), modifier = Modifier.padding(top = 8.dp))
        Btn(tr("Понятно", "OK"), Modifier.fillMaxWidth().padding(top = 12.dp)) { tale = null }
    } }

    Section(tr("Письма Первого путника · ${d.letters}/12", "Letters of the First Walker · ${d.letters}/12"))
    Text(tr("Приходят за вехи пути: первые прогулки и находки, километры, свой район. Когда соберутся все двенадцать, а дом будет под печатью, кто-то выйдет из тумана.",
        "They come for milestones of the way: first walks and finds, kilometres, your own district. When all twelve are here and home is sealed, someone will come out of the fog."), style = T.small)
    (0 until 12).chunked(4).forEach { row ->
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEach { i ->
                val got = i < d.letters
                Column(Modifier.weight(1f).card().clickable(enabled = got) { letter = i }.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Pic("find_postcard", 44.dp, alpha = if (got) 1f else .3f, grey = !got)
                    Text(if (got) tr(Story.letterTitles[i].first, Story.letterTitles[i].second) else "${i + 1}", style = T.tiny, textAlign = TextAlign.Center, maxLines = 2)
                }
            }
        }
    }
    if (letter >= 0) Popup({ letter = -1 }) {
        Text(tr(Story.letterTitles[letter].first, Story.letterTitles[letter].second), style = T.h2)
        Text(tr(Story.letters[letter].first, Story.letters[letter].second), style = T.h3.copy(fontFamily = FontFamily(androidx.compose.ui.text.font.Font(R.font.caveat_medium)), fontSize = T.h2.fontSize * 1.05f),
            modifier = Modifier.padding(top = 10.dp))
        Text("— " + tr("Первый путник", "The First Walker"), style = T.small, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), textAlign = TextAlign.End)
        Btn(tr("Сложить письмо", "Fold the letter"), Modifier.fillMaxWidth().padding(top = 12.dp)) { letter = -1 }
    }
}

@Composable
private fun DistrictCard(dist: District, seal: String?, home: Boolean, here: Boolean, miss: Int, onTale: (String) -> Unit) {
    val sealed = seal != null
    // a sealed district keeps the spirit that sealed it
    val sp = seal?.split('|')?.firstOrNull()?.let { runCatching { Spirit.valueOf(it) }.getOrNull() } ?: dist.spirit
    Column(Modifier.fillMaxWidth().padding(top = 12.dp).card(color = if (sealed) C.goldSoft else null).clickable(enabled = sealed) { onTale(sp.name) }.padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(92.dp).clip(RoundedCornerShape(10.dp)).background(if (sealed) C.card else androidx.compose.ui.graphics.Color(0xFFCDD2D4)), contentAlignment = Alignment.Center) {
                Pic(spiritPic(sp, seal), 86.dp, alpha = if (sealed) 1f else .75f)
            }
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(listOfNotNull(if (home) tr("Домашний район", "Home district") else null, if (here) tr("вы здесь", "you are here") else null).joinToString(" · ").ifEmpty { tr("Район", "District") }, style = T.tiny)
                Text(sp.title, style = T.bodyB)
                if (sealed) Text(sp.about, style = T.small) else {
                    Text(tr("спит в тумане", "asleep in the fog"), style = T.small)
                    Meter(dist.share / dist.needShare, Modifier.padding(top = 6.dp), color = C.acc, h = 7.dp)
                    Text(tr("Пройдено клочков: ${(dist.share * 100).toInt()}% из ${(dist.needShare * 100).toInt()}%", "Patches walked: ${(dist.share * 100).toInt()}% of ${(dist.needShare * 100).toInt()}%"), style = T.tiny)
                }
            }
            if (sealed) Pic("seal_${sp.seal}", 44.dp)
        }
        if (miss >= 0) Row(Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(8.dp)).background(C.card).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Ico("heart", 22.dp)
            Text(tr("${sp.title} скучает по вам. Пройдите здесь 5 клочков на этой неделе – будет подарок. Уже ${miss.coerceAtMost(5)} из 5.",
                "${sp.title} misses you. Walk 5 patches here this week for a gift. ${miss.coerceAtMost(5)} of 5 so far."), style = T.small.copy(color = C.ink), modifier = Modifier.padding(start = 8.dp))
        }
        if (sealed) Text(tr("Тапните, чтобы услышать, что сказал Дух", "Tap to hear what the spirit said"), style = T.tiny, modifier = Modifier.padding(top = 6.dp))
        Text(tr("Обитатели · встречено здесь ${dist.metCount}/${dist.needMet}", "Dwellers · met here ${dist.metCount}/${dist.needMet}"), style = T.smallB, modifier = Modifier.padding(top = 10.dp))
        Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            dist.residents.forEach { sp ->
                val met = sp.id in dist.met
                Box(Modifier.size(52.dp).clip(RoundedCornerShape(8.dp)).background(C.card2).border(1.5.dp, if (met) C.ok else C.line, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                    Mini(sp.id, 46.dp, dim = !met)
                    if (sp.wild != null) Ico("moon", 14.dp, Modifier.align(Alignment.TopEnd))
                }
            }
        }
        Spacer(Modifier.height(2.dp))
    }
}
