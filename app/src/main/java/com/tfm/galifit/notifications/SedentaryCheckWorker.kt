package com.tfm.galifit.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import com.tfm.galifit.notifications.SedentaryCheckWorker.Companion.WINDOW_MINUTES
import com.tfm.galifit.util.GalifitFlowLog
import java.time.Instant
import java.time.temporal.ChronoUnit

class SedentaryCheckWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val forceTest = inputData.getBoolean(KEY_FORCE_TEST, false)
            GalifitFlowLog.sedentary(
                "Worker iniciado (id=$id${if (forceTest) ", prueba manual" else ""})"
            )

            val config = SedentaryNotificationScheduler.readWorkerConfig(applicationContext)
            if (!config.enabled) {
                GalifitFlowLog.sedentary("Skip: notificaciones desactivadas en config local")
                return Result.success()
            }

            val hour = java.time.ZonedDateTime.now().hour
            if (!forceTest && hour !in ACTIVE_HOUR_START until ACTIVE_HOUR_END) {
                GalifitFlowLog.sedentary(
                    "Skip: fuera de horario activo (hora=$hour, ventana=$ACTIVE_HOUR_START-${ACTIVE_HOUR_END - 1}h)"
                )
                return Result.success()
            }

            GalifitFlowLog.sedentary(
                "Comprobando pasos — fuente=${config.dataSource}, ventana=${WINDOW_MINUTES}min, umbral=$STEPS_THRESHOLD"
            )

            val totalSteps = when (config.dataSource) {
                SedentaryDataSource.DEVICE_SENSOR -> readStepsFromDeviceSensor()
                else -> readStepsFromHealthConnect()
            }
            if (totalSteps == null) {
                if (forceTest) {
                    GalifitFlowLog.sedentary("Prueba manual: no se pudieron leer pasos; se envía aviso de prueba")
                    SedentaryNotificationHelper.showSedentaryAlert(
                        applicationContext,
                        minutesIdle = WINDOW_MINUTES,
                        recentSteps = 0L,
                        isTest = true,
                        stepsUnreadable = true
                    )
                    return Result.success()
                }
                GalifitFlowLog.sedentary("Skip: no se pudieron leer pasos (ver mensajes anteriores)")
                return Result.success()
            }

            GalifitFlowLog.sedentary("Pasos en ventana: $totalSteps (umbral $STEPS_THRESHOLD)")

            if (forceTest) {
                GalifitFlowLog.sedentary("NOTIFY (prueba manual): $totalSteps pasos en ${WINDOW_MINUTES}min")
                SedentaryNotificationHelper.showSedentaryAlert(
                    applicationContext,
                    minutesIdle = WINDOW_MINUTES,
                    recentSteps = totalSteps,
                    isTest = true
                )
                return Result.success()
            }

            if (totalSteps >= STEPS_THRESHOLD) {
                GalifitFlowLog.sedentary("Skip: actividad suficiente, no se envía aviso")
                return Result.success()
            }

            val notifyCooldown = notifyCooldownRemainingMs()
            if (notifyCooldown > 0L) {
                GalifitFlowLog.sedentary(
                    "Skip: anti-spam activo (faltan ${notifyCooldown / 1000}s para poder avisar de nuevo)"
                )
                return Result.success()
            }

            GalifitFlowLog.sedentary("NOTIFY: sedentarismo detectado ($totalSteps pasos en ${WINDOW_MINUTES}min)")
            SedentaryNotificationHelper.showSedentaryAlert(
                applicationContext,
                minutesIdle = WINDOW_MINUTES,
                recentSteps = totalSteps
            )
            markNotified()
            Result.success()
        } catch (t: Throwable) {
            GalifitFlowLog.warn("SedentaryWorker error: ${t.message}")
            Result.success()
        }
    }

    private suspend fun readStepsFromDeviceSensor(): Long? {
        if (!DeviceStepTracker.hasActivityRecognitionPermission(applicationContext)) {
            GalifitFlowLog.sedentary("Skip sensor: sin permiso ACTIVITY_RECOGNITION")
            return null
        }
        if (!DeviceStepTracker.hasStepCounterSensor(applicationContext)) {
            GalifitFlowLog.sedentary("Skip sensor: dispositivo sin TYPE_STEP_COUNTER")
            return null
        }
        return DeviceStepTracker.recordAndGetStepsInWindow(
            applicationContext,
            WINDOW_MINUTES
        )
    }

    private suspend fun readStepsFromHealthConnect(): Long? {
        if (!SedentaryNotificationScheduler.isHealthConnectAvailable(applicationContext)) {
            GalifitFlowLog.sedentary("Skip HC: Health Connect no disponible")
            return null
        }

        val client = androidx.health.connect.client.HealthConnectClient
            .getOrCreate(applicationContext)
        val granted = client.permissionController.getGrantedPermissions()
        if (!granted.containsAll(SedentaryNotificationScheduler.healthPermissions)) {
            GalifitFlowLog.sedentary("Skip HC: faltan permisos de lectura de pasos")
            return null
        }

        val now = Instant.now()
        val windowStart = now.minus(WINDOW_MINUTES, ChronoUnit.MINUTES)
        val response = client.readRecords(
            androidx.health.connect.client.request.ReadRecordsRequest(
                recordType = androidx.health.connect.client.records.StepsRecord::class,
                timeRangeFilter = androidx.health.connect.client.time.TimeRangeFilter
                    .between(windowStart, now)
            )
        )
        val total = response.records.sumOf { it.count }
        GalifitFlowLog.sedentary(
            "HC: ${response.records.size} registros en ventana, total=$total pasos"
        )
        return total
    }

    private fun notifyCooldownRemainingMs(): Long {
        val prefs = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val last = prefs.getLong(KEY_LAST_NOTIFY, 0L)
        if (last == 0L) return 0L
        val elapsed = System.currentTimeMillis() - last
        return (MIN_NOTIFY_INTERVAL_MS - elapsed).coerceAtLeast(0L)
    }

    private fun markNotified() {
        val prefs = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putLong(KEY_LAST_NOTIFY, System.currentTimeMillis()).apply()
    }

    companion object {
        const val KEY_FORCE_TEST = "force_test"

        private const val PREFS_NAME = "sedentary_worker_prefs"
        private const val KEY_LAST_NOTIFY = "last_notify_ms"

        fun testInputData(): Data = Data.Builder()
            .putBoolean(KEY_FORCE_TEST, true)
            .build()

        private const val WINDOW_MINUTES = 90L

        private const val STEPS_THRESHOLD = 250L

        private const val MIN_NOTIFY_INTERVAL_MS = 2L * 60 * 60 * 1000

        private const val ACTIVE_HOUR_START = 9

        private const val ACTIVE_HOUR_END = 22
    }
}
