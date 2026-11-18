package app.roamling.explore

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat

/**
 * The phone's own step counter, listened to only while a walk is on: the steps of the walk, nothing else.
 * The sensor counts since the phone started, so only the difference between readings is passed on.
 */
class StepCounter(private val context: Context, private val walker: Walker) {
    private val sm = context.getSystemService(SensorManager::class.java)
    private val sensor: Sensor? = sm?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
    private var last = -1L
    private var users = 0
    private var listening = false
    private var pending = 0
    private val main = Handler(Looper.getMainLooper())
    private val late = Runnable { flush() }
    @Volatile var enabled = true

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(e: SensorEvent) {
            val total = e.values[0].toLong()
            // the first reading only sets the mark; a lower one means the phone restarted
            if (last >= 0 && total > last) {
                walker.stepped(System.currentTimeMillis())
                if (pending == 0) main.postDelayed(late, 10_000)
                pending += (total - last).toInt()
            }
            last = total
            if (pending >= 20) flush()
        }

        override fun onAccuracyChanged(s: Sensor?, accuracy: Int) {}
    }

    val available: Boolean get() = sensor != null

    fun permitted(): Boolean = Build.VERSION.SDK_INT < 29 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED

    /** Main thread only. */
    fun acquire() { users++; refresh() }
    fun release() { if (users > 0) users--; refresh() }

    fun refresh() {
        val want = users > 0 && enabled && sensor != null && permitted()
        if (want == listening) return
        listening = want
        walker.pedometerRestarted()
        if (want) { last = -1; sm?.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL) }
        else { sm?.unregisterListener(listener); flush() }
    }

    private fun flush() {
        main.removeCallbacks(late)
        if (pending == 0) return
        walker.addSteps(pending, System.currentTimeMillis())
        pending = 0
    }
}
