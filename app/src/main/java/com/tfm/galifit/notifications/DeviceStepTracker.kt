package com.tfm.galifit.notifications

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
import android.os.PowerManager
import androidx.core.content.ContextCompat
import com.tfm.galifit.util.GalifitFlowLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

object DeviceStepTracker {

    private const val PREFS_NAME = "device_step_tracker_prefs"
    private const val KEY_HISTORY = "step_history"
    private const val HISTORY_RETENTION_MS = 24L * 60 * 60 * 1000
    private const val SENSOR_TIMEOUT_MS = 10_000L

    private const val HISTORY_FALLBACK_MAX_AGE_MS = 25L * 60 * 1000

    fun hasActivityRecognitionPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACTIVITY_RECOGNITION
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun hasStepCounterSensor(context: Context): Boolean {
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            ?: return false
        return manager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null
    }

    suspend fun recordAndGetStepsInWindow(
        context: Context,
        windowMinutes: Long
    ): Long? {
        if (!hasActivityRecognitionPermission(context)) {
            GalifitFlowLog.sedentary("Skip sensor: sin permiso ACTIVITY_RECOGNITION")
            return null
        }
        val now = System.currentTimeMillis()
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val history = loadHistory(prefs).toMutableList()

        val currentSteps = readStepCounterOnce(context)
        if (currentSteps == null) {
            val fallback = computeStepsFromRecentHistory(history, now, windowMinutes)
            if (fallback != null) {
                GalifitFlowLog.sedentary(
                    "Sensor: lectura en vivo falló; usando histórico reciente → $fallback pasos en ${windowMinutes}min"
                )
                return fallback
            }
            GalifitFlowLog.sedentary(
                "Skip sensor: lectura null (timeout ${SENSOR_TIMEOUT_MS}ms) y sin histórico reciente suficiente"
            )
            return null
        }

        val lastReading = history.lastOrNull()
        if (lastReading != null && currentSteps < lastReading.steps) {
            history.clear()
        }

        history.add(StepReading(now, currentSteps))

        val retentionCutoff = now - HISTORY_RETENTION_MS
        history.removeAll { it.timestamp < retentionCutoff }
        saveHistory(prefs, history)

        val stepsInWindow = computeStepsInWindow(history, now, windowMinutes)
        GalifitFlowLog.sedentary(
            "Sensor: contador=$currentSteps, lecturas=${history.size}, pasos en ${windowMinutes}min=$stepsInWindow"
        )
        return stepsInWindow
    }

    private suspend fun readStepCounterOnce(context: Context): Long? {
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            ?: run {
                GalifitFlowLog.sedentary("Skip sensor: SensorManager no disponible")
                return null
            }
        val sensor = manager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) ?: run {
            GalifitFlowLog.sedentary("Skip sensor: sin TYPE_STEP_COUNTER")
            return null
        }

        return withContext(Dispatchers.Main) {
            val wakeLock = acquireSensorWakeLock(context)
            try {
                withTimeoutOrNull(SENSOR_TIMEOUT_MS) {
                    suspendCancellableCoroutine { cont ->
                        val mainHandler = Handler(Looper.getMainLooper())
                        val listener = object : SensorEventListener {
                            override fun onSensorChanged(event: SensorEvent) {
                                manager.unregisterListener(this)
                                if (cont.isActive) {
                                    cont.resume(event.values[0].toLong())
                                }
                            }

                            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
                        }

                        val registered = manager.registerListener(
                            listener,
                            sensor,
                            SensorManager.SENSOR_DELAY_FASTEST,
                            mainHandler
                        )
                        if (!registered) {
                            GalifitFlowLog.sedentary("Skip sensor: registerListener devolvió false")
                            if (cont.isActive) cont.resume(null)
                            return@suspendCancellableCoroutine
                        }

                        cont.invokeOnCancellation {
                            manager.unregisterListener(listener)
                        }
                    }
                }
            } finally {
                releaseSensorWakeLock(wakeLock)
            }
        }
    }

    private fun computeStepsFromRecentHistory(
        history: List<StepReading>,
        now: Long,
        windowMinutes: Long
    ): Long? {
        if (history.size < 2) return null
        val sorted = history.sortedBy { it.timestamp }
        val latest = sorted.last()
        if (now - latest.timestamp > HISTORY_FALLBACK_MAX_AGE_MS) return null
        return computeStepsInWindow(sorted, now, windowMinutes)
    }

    private fun acquireSensorWakeLock(context: Context): PowerManager.WakeLock? {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return null
        return pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "galifit:step_counter_read"
        ).apply {
            setReferenceCounted(false)
            acquire(SENSOR_TIMEOUT_MS + 2_000L)
        }
    }

    private fun releaseSensorWakeLock(wakeLock: PowerManager.WakeLock?) {
        runCatching {
            if (wakeLock?.isHeld == true) wakeLock.release()
        }
    }

    suspend fun sampleIfDeviceSensorEnabled(context: Context, windowMinutes: Long = 2L) {
        val config = SedentaryNotificationScheduler.readWorkerConfig(context)
        if (!config.enabled || config.dataSource != SedentaryDataSource.DEVICE_SENSOR) return
        recordAndGetStepsInWindow(context, windowMinutes)
    }

    private fun computeStepsInWindow(
        history: List<StepReading>,
        now: Long,
        windowMinutes: Long
    ): Long {
        val windowStart = now - windowMinutes * 60_000
        val sorted = history.sortedBy { it.timestamp }
        val latest = sorted.lastOrNull() ?: return 0L

        val baseline = sorted
            .filter { it.timestamp <= windowStart }
            .maxByOrNull { it.timestamp }
            ?.steps
            ?: sorted.firstOrNull { it.timestamp >= windowStart }?.steps
            ?: latest.steps

        return (latest.steps - baseline).coerceAtLeast(0L)
    }

    private data class StepReading(val timestamp: Long, val steps: Long)

    private fun loadHistory(prefs: android.content.SharedPreferences): List<StepReading> {
        val raw = prefs.getString(KEY_HISTORY, null) ?: return emptyList()
        return raw.split('|')
            .mapNotNull { entry ->
                val parts = entry.split(':')
                if (parts.size != 2) return@mapNotNull null
                val ts = parts[0].toLongOrNull() ?: return@mapNotNull null
                val steps = parts[1].toLongOrNull() ?: return@mapNotNull null
                StepReading(ts, steps)
            }
    }

    private fun saveHistory(
        prefs: android.content.SharedPreferences,
        history: List<StepReading>
    ) {
        val serialized = history.joinToString("|") { "${it.timestamp}:${it.steps}" }
        prefs.edit().putString(KEY_HISTORY, serialized).apply()
    }
}
