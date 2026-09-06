package com.tfm.galifit.notifications

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.tfm.galifit.notifications.SedentaryNotificationScheduler.WORK_NAME
import com.tfm.galifit.notifications.SedentaryNotificationScheduler.applyPreference
import com.tfm.galifit.notifications.SedentaryNotificationScheduler.healthConnectPermissionContract
import com.tfm.galifit.notifications.SedentaryNotificationScheduler.healthPermissions
import com.tfm.galifit.util.GalifitFlowLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

object SedentaryNotificationScheduler {

    private val bootstrapScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    const val WORK_NAME = "sedentary_check_periodic"

    private const val TEST_WORK_NAME = "sedentary_check_test"

    const val HEALTH_CONNECT_PACKAGE = "com.google.android.apps.healthdata"

    private const val POST_NOTIFICATIONS_REQUEST_CODE = 9101
    private const val CONFIG_PREFS = "sedentary_worker_config"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_DATA_SOURCE = "data_source"

    data class SedentaryWorkerConfig(
        val enabled: Boolean,
        val dataSource: String
    )

    val healthPermissions: Set<String> = setOf(
        HealthPermission.getReadPermission(StepsRecord::class)
    )

    fun syncWorkerConfig(
        context: Context,
        enabled: Boolean,
        dataSource: String = SedentaryDataSource.HEALTH_CONNECT
    ) {
        val resolvedSource = if (enabled) {
            SedentaryDataSource.defaultForLegacy(true, dataSource)
        } else {
            ""
        }
        context.getSharedPreferences(CONFIG_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENABLED, enabled)
            .putString(KEY_DATA_SOURCE, resolvedSource)
            .apply()
        GalifitFlowLog.sedentary(
            "Config local actualizada — enabled=$enabled, fuente=$resolvedSource"
        )
    }

    fun applyPreference(
        context: Context,
        enabled: Boolean,
        dataSource: String = SedentaryDataSource.HEALTH_CONNECT
    ) {
        SedentaryNotificationHelper.ensureChannel(context)
        val resolvedSource = if (enabled) {
            SedentaryDataSource.defaultForLegacy(true, dataSource)
        } else {
            ""
        }
        syncWorkerConfig(context, enabled, dataSource)
        if (enabled) {
            GalifitFlowLog.sedentary("Programando worker — fuente=$resolvedSource")
            schedule(context)
            if (resolvedSource == SedentaryDataSource.DEVICE_SENSOR) {
                bootstrapScope.launch {
                    DeviceStepTracker.sampleIfDeviceSensorEnabled(context.applicationContext)
                }
            }
        } else {
            GalifitFlowLog.sedentary("Cancelando worker de sedentarismo")
            cancel(context)
        }
    }

    fun readWorkerConfig(context: Context): SedentaryWorkerConfig {
        val prefs = context.getSharedPreferences(CONFIG_PREFS, Context.MODE_PRIVATE)
        val enabled = prefs.getBoolean(KEY_ENABLED, false)
        val dataSource = SedentaryDataSource.defaultForLegacy(
            enabled = enabled,
            stored = prefs.getString(KEY_DATA_SOURCE, null)
        )
        return SedentaryWorkerConfig(enabled, dataSource)
    }

    fun schedule(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .build()
        val request = PeriodicWorkRequestBuilder<SedentaryCheckWorker>(
            15, TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    fun ensureWorkerFromLocalConfig(context: Context) {
        val config = readWorkerConfig(context)
        if (!config.enabled) return
        SedentaryNotificationHelper.ensureChannel(context)
        schedule(context)
    }

    fun runTestNow(context: Context) {
        SedentaryNotificationHelper.ensureChannel(context)
        val request = OneTimeWorkRequestBuilder<SedentaryCheckWorker>()
            .setInputData(SedentaryCheckWorker.testInputData())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            TEST_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
        GalifitFlowLog.sedentary("Prueba manual de sedentarismo encolada")
    }

    fun getSdkStatusCode(context: Context): Int {
        return try {
            HealthConnectClient.getSdkStatus(context, HEALTH_CONNECT_PACKAGE)
        } catch (t: Throwable) {
            GalifitFlowLog.warn("Sedentary: getSdkStatus error: ${t.message}")
            HealthConnectClient.SDK_UNAVAILABLE
        }
    }

    fun isHealthConnectAvailable(context: Context): Boolean {
        return getSdkStatusCode(context) == HealthConnectClient.SDK_AVAILABLE
    }

    fun openHealthConnectInPlayStore(activity: Activity) {
        val uriString =
            "market://details?id=$HEALTH_CONNECT_PACKAGE&url=healthconnect%3A%2F%2Fonboarding"
        runCatching {
            activity.startActivity(
                Intent(Intent.ACTION_VIEW).apply {
                    setPackage("com.android.vending")
                    data = Uri.parse(uriString)
                    putExtra("overlay", true)
                    putExtra("callerId", activity.packageName)
                }
            )
        }
    }

    fun openHealthConnectSettings(context: Context) {
        runCatching {
            val intent = HealthConnectClient.getHealthConnectManageDataIntent(context)
            if (context !is Activity) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }.onFailure {
            runCatching {
                val fallback = Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS).apply {
                    if (context !is Activity) {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                }
                context.startActivity(fallback)
            }
        }
    }

    fun requestPermissionsIfNeeded(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                activity,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                ActivityCompat.requestPermissions(
                    activity,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    POST_NOTIFICATIONS_REQUEST_CODE
                )
            }
        }
    }

    fun healthConnectPermissionContract() =
        PermissionController.createRequestPermissionResultContract()
}
