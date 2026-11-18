package app.roamling.explore

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import app.roamling.container
import app.roamling.notify.Notes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** A walk with the screen off: GPS and the pedometer on in a foreground service, a quiet notification counting the way. */
class WalkService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var holding = false
    private var ticker: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val c = container
        if (intent?.action == ACTION_STOP) {
            scope.launch { c.walker.stopWalk(); stopSelf() }
            return START_NOT_STICKY
        }
        if (!c.feed.permitted()) { stopSelf(); return START_NOT_STICKY }
        try {
            ServiceCompat.startForeground(this, Notes.ID_WALK, Notes.walk(this, c.walker.live.value),
                if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0)
        } catch (_: Exception) { stopSelf(); return START_NOT_STICKY }
        if (!holding) {
            holding = true
            c.feed.acquire(); c.pedometer.acquire()
            ticker = scope.launch { while (true) { delay(10_000); Notes.updateWalk(this@WalkService, c.walker.live.value) } }
            scope.launch { c.walker.events.collect { e -> Notes.onEvent(this@WalkService, e) } }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        if (holding) { container.feed.release(); container.pedometer.release() }
        holding = false
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val ACTION_STOP = "app.roamling.STOP_WALK"

        fun start(context: Context) {
            if (!context.container.feed.permitted()) return
            ContextCompat.startForegroundService(context, Intent(context, WalkService::class.java))
        }

        fun stopIntent(context: Context): Intent = Intent(context, WalkService::class.java).setAction(ACTION_STOP)

        fun stop(context: Context) {
            val c = context.container
            c.scope.launch { c.walker.stopWalk() }
            context.stopService(Intent(context, WalkService::class.java))
        }
    }
}
