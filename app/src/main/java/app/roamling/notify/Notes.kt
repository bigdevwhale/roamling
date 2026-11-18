package app.roamling.notify

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.roamling.MainActivity
import app.roamling.R
import app.roamling.container
import app.roamling.domain.Catalog
import app.roamling.domain.Finds
import app.roamling.domain.dayOf
import app.roamling.domain.fmt
import app.roamling.domain.fmt1
import app.roamling.domain.hourOf
import app.roamling.domain.tr
import app.roamling.explore.Event
import app.roamling.explore.Live
import app.roamling.explore.WalkService
import java.util.concurrent.TimeUnit

object Notes {
    const val ID_WALK = 1
    private const val CH_WALK = "walk"
    private const val CH_NEWS = "news"

    fun channels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_WALK, tr("Прогулка", "Walk"), NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel(CH_NEWS, tr("Попутчики", "Companions"), NotificationManager.IMPORTANCE_DEFAULT))
    }

    private fun open(ctx: Context) = PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    fun walk(ctx: Context, l: Live): Notification {
        val w = l.walk
        val text = if (w == null) tr("Ищу спутники…", "Finding satellites…")
        else tr("${fmt1(w.meters / 1000)} км · новых клочков: ${w.patches}", "${fmt1(w.meters / 1000)} km · new patches: ${w.patches}")
        return NotificationCompat.Builder(ctx, CH_WALK).setSmallIcon(R.drawable.ic_stat).setContentTitle(tr("Гуляем", "Out walking"))
            .setContentText(text).setOngoing(true).setOnlyAlertOnce(true).setContentIntent(open(ctx))
            .addAction(0, tr("Закончить", "Finish"), PendingIntent.getService(ctx, 1, WalkService.stopIntent(ctx), PendingIntent.FLAG_IMMUTABLE))
            .build()
    }

    fun updateWalk(ctx: Context, l: Live) {
        if (!allowed(ctx)) return
        try { NotificationManagerCompat.from(ctx).notify(ID_WALK, walk(ctx, l)) } catch (_: SecurityException) {}
    }

    private fun allowed(ctx: Context) = ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun post(ctx: Context, id: Int, title: String, text: String) {
        if (!allowed(ctx)) return
        try {
            NotificationManagerCompat.from(ctx).notify(id, NotificationCompat.Builder(ctx, CH_NEWS).setSmallIcon(R.drawable.ic_stat)
                .setContentTitle(title).setContentText(text).setStyle(NotificationCompat.BigTextStyle().bigText(text)).setAutoCancel(true).setContentIntent(open(ctx)).build())
        } catch (_: SecurityException) {}
    }

    suspend fun onEvent(ctx: Context, e: Event) {
        if (!ctx.container.settings.now().notify) return
        when (e) {
            is Event.Egg -> post(ctx, 10, tr("Яйцо!", "An egg!"), tr("В тумане нашлось яйцо. Оно греется в кармане рюкзака.", "An egg turned up in the fog. It's warming in a backpack pocket."))
            is Event.Hatched -> post(ctx, 11, tr("Вылупился!", "Hatched!"), tr("${Catalog[e.pet.species].name} – новый попутчик.", "${Catalog[e.pet.species].name} – a new companion."))
            is Event.Grew -> post(ctx, 12, tr("${e.pet.name} подрос", "${e.pet.name} has grown"), if (e.pet.stage == 3) tr("Взрослая форма: ${Catalog[e.pet.species].forms[e.pet.form.coerceAtLeast(0)].name}", "Grown form: ${Catalog[e.pet.species].forms[e.pet.form.coerceAtLeast(0)].name}") else tr("Теперь подросток – ест то, где вы гуляете.", "A teen now – it eats where you walk."))
            is Event.Joined -> post(ctx, 13, tr("Новый попутчик", "A new companion"), tr("${Catalog[e.pet.species].name} пошёл с вами.", "${Catalog[e.pet.species].name} came along."))
            is Event.Found -> post(ctx, 14, tr("Находка", "A find"), Finds.name(e.find.item))
            is Event.ShadowNear -> post(ctx, 15, tr("Кто-то рядом", "Someone's near"), tr("Тень на карте в ${e.meters} м. Подойдите ближе.", "A shadow on the map ${e.meters} m away. Come closer."))
            is Event.CallDone -> post(ctx, 16, tr("Зов исполнен", "Wish come true"), e.call.title)
            Event.Rainbow -> post(ctx, 17, tr("Радуга!", "A rainbow!"), tr("У вас 40 минут, чтобы дойти до её конца.", "You have 40 minutes to reach its end."))
            Event.Gift -> post(ctx, 18, tr("Цель шагов!", "Step goal!"), tr("В кармане рюкзака подарок.", "There's a gift in the backpack pocket."))
            is Event.Chapter -> post(ctx, 20, tr("Район проснулся!", "A district woke up!"), tr("${e.spirit.title} вышел из тумана и поставил печать на Большую карту.", "${e.spirit.title} came out of the fog and sealed the Great Map."))
            Event.Finale -> post(ctx, 21, tr("Хранитель тумана", "The Keeper of the fog"), tr("Он вышел к вашему привалу.", "It has come out to your rest stop."))
        }
    }
}

/**
 * Every half hour: Health Connect's steps, the weather (for a rainbow) and «the last little step» in the evening.
 */
class Watch(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val c = applicationContext.container
        c.walker.awaitReady()
        c.syncHealth()
        val s = c.settings.now()
        if (s.lastLat != 0.0) c.walker.weather(s.lastLat, s.lastLon)
        val today = dayOf(System.currentTimeMillis())
        c.walker.feedSteps(today)
        val row = c.db.dao().day(today)
        val left = s.stepGoal - (row?.steps ?: 0)
        if (s.notify && left in 1..300 && hourOf(System.currentTimeMillis()) >= 17 && "near:$today" !in s.told) {
            c.settings.update { it.copy(told = it.told + "near:$today") }
            val lead = s.team.firstOrNull()?.let { c.db.dao().pet(it) }
            Notes.post(applicationContext, 19, tr("Последний шажок", "The last little step"),
                tr("${lead?.name ?: "Попутчик"} тянет за рукав: до цели ${fmt(left)} шагов.", "${lead?.name ?: "Your companion"} tugs your sleeve: ${fmt(left)} steps to the goal."))
        }
        return Result.success()
    }

    companion object {
        fun schedule(ctx: Context) {
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork("watch", ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<Watch>(30, TimeUnit.MINUTES).build())
        }
    }
}
