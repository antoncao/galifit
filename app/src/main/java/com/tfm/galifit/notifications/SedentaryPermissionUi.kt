package com.tfm.galifit.notifications

import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.tfm.galifit.R

object SedentaryPermissionUi {

    fun showOutcome(
        activity: AppCompatActivity,
        outcome: SedentaryPermissionOutcome,
        closeButtonLabelRes: Int = R.string.sedentary_permission_dialog_close,
        onClose: (() -> Unit)? = null,
        onRetry: (() -> Unit)? = null
    ) {
        if (outcome is SedentaryPermissionOutcome.Granted) return

        val messageView = TextView(activity).apply {
            setPadding(48, 24, 48, 8)
            text = outcome.userMessage()
            textSize = 16f
            movementMethod = LinkMovementMethod.getInstance()
        }

        val builder = AlertDialog.Builder(activity)
            .setTitle(R.string.sedentary_permission_dialog_title)
            .setView(messageView)
            .setCancelable(onClose == null)
            .setNegativeButton(closeButtonLabelRes) { _, _ ->
                onClose?.invoke()
            }

        when (outcome) {
            is SedentaryPermissionOutcome.Denied -> {
                builder
                    .setPositiveButton(R.string.sedentary_permission_dialog_open_hc) { _, _ ->
                        SedentaryNotificationScheduler.openHealthConnectSettings(activity)
                    }
                if (onRetry != null) {
                    builder.setNeutralButton(R.string.sedentary_permission_dialog_retry) { _, _ ->
                        onRetry()
                    }
                }
            }
            is SedentaryPermissionOutcome.NotificationsDenied,
            is SedentaryPermissionOutcome.SensorDenied -> {
                if (onRetry != null) {
                    builder.setPositiveButton(R.string.sedentary_permission_dialog_retry) { _, _ ->
                        onRetry()
                    }
                }
            }
            is SedentaryPermissionOutcome.SensorUnavailable -> {
                if (onRetry != null) {
                    builder.setNeutralButton(R.string.sedentary_permission_dialog_retry) { _, _ ->
                        onRetry()
                    }
                }
            }
            is SedentaryPermissionOutcome.ProviderMissingOrOutdated -> {
                builder.setPositiveButton(R.string.sedentary_permission_dialog_play_store) { _, _ ->
                    SedentaryNotificationScheduler.openHealthConnectInPlayStore(activity)
                }
            }
            is SedentaryPermissionOutcome.Unavailable,
            is SedentaryPermissionOutcome.Error -> {
                if (onRetry != null) {
                    builder.setPositiveButton(R.string.sedentary_permission_dialog_retry) { _, _ ->
                        onRetry()
                    }
                }
            }
            SedentaryPermissionOutcome.Granted -> Unit
        }

        builder.show()
    }
}
