package com.tfm.galifit.notifications

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.health.connect.client.HealthConnectClient
import androidx.lifecycle.lifecycleScope
import com.tfm.galifit.util.GalifitFlowLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SedentaryPermissionsHelper(
    private val activity: ComponentActivity
) {

    private var pendingCallback: ((SedentaryPermissionOutcome) -> Unit)? = null
    private var pendingDataSource: String? = null

    private val notificationsLauncher = activity.registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            activity.window.decorView.post { requestSourcePermissions() }
        } else {
            complete(SedentaryPermissionOutcome.NotificationsDenied)
        }
    }

    private val healthConnectLauncher = activity.registerForActivityResult(
        SedentaryNotificationScheduler.healthConnectPermissionContract()
    ) { granted ->
        val outcome = if (granted.containsAll(SedentaryNotificationScheduler.healthPermissions)) {
            SedentaryPermissionOutcome.Granted
        } else {
            SedentaryPermissionOutcome.Denied
        }
        complete(outcome)
    }

    private val activityRecognitionLauncher = activity.registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val outcome = if (granted) {
            SedentaryPermissionOutcome.Granted
        } else {
            SedentaryPermissionOutcome.SensorDenied
        }
        complete(outcome)
    }

    fun requestForDataSource(
        dataSource: String,
        onComplete: (SedentaryPermissionOutcome) -> Unit
    ) {
        pendingCallback = onComplete
        pendingDataSource = dataSource
        if (needsPostNotificationsPermission()) {
            notificationsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        requestSourcePermissions()
    }

    private fun needsPostNotificationsPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
        return ContextCompat.checkSelfPermission(
            activity,
            Manifest.permission.POST_NOTIFICATIONS
        ) != PackageManager.PERMISSION_GRANTED
    }

    private fun requestSourcePermissions() {
        when (pendingDataSource) {
            SedentaryDataSource.DEVICE_SENSOR -> requestDeviceSensorPermissions()
            else -> requestHealthConnectPermissions()
        }
    }

    private fun requestHealthConnectPermissions() {
        val sdkStatus = SedentaryNotificationScheduler.getSdkStatusCode(activity)
        GalifitFlowLog.debug("Sedentary: Health Connect SDK status=$sdkStatus")

        when (sdkStatus) {
            HealthConnectClient.SDK_UNAVAILABLE -> {
                complete(SedentaryPermissionOutcome.Unavailable)
                return
            }
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> {
                SedentaryNotificationScheduler.openHealthConnectInPlayStore(activity)
                complete(SedentaryPermissionOutcome.ProviderMissingOrOutdated)
                return
            }
        }

        if (sdkStatus != HealthConnectClient.SDK_AVAILABLE) {
            complete(SedentaryPermissionOutcome.Unavailable)
            return
        }

        activity.lifecycleScope.launch {
            try {
                val client = HealthConnectClient.getOrCreate(activity)
                val granted = client.permissionController.getGrantedPermissions()
                if (granted.containsAll(SedentaryNotificationScheduler.healthPermissions)) {
                    complete(SedentaryPermissionOutcome.Granted)
                    return@launch
                }
                withContext(Dispatchers.Main.immediate) {
                    healthConnectLauncher.launch(SedentaryNotificationScheduler.healthPermissions)
                }
            } catch (t: Throwable) {
                GalifitFlowLog.warn("Sedentary: permisos Health Connect error: ${t.message}")
                complete(SedentaryPermissionOutcome.Error(t.message))
            }
        }
    }

    private fun requestDeviceSensorPermissions() {
        if (!DeviceStepTracker.hasStepCounterSensor(activity)) {
            complete(SedentaryPermissionOutcome.SensorUnavailable)
            return
        }
        if (DeviceStepTracker.hasActivityRecognitionPermission(activity)) {
            complete(SedentaryPermissionOutcome.Granted)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            activityRecognitionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
        } else {
            complete(SedentaryPermissionOutcome.Granted)
        }
    }

    private fun complete(outcome: SedentaryPermissionOutcome) {
        val callback = pendingCallback
        pendingCallback = null
        pendingDataSource = null
        callback?.invoke(outcome)
    }
}
