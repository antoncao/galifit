package com.tfm.galifit.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.tfm.galifit.MainActivity
import com.tfm.galifit.R

object SedentaryNotificationHelper {

    const val CHANNEL_ID = "sedentary_alerts"

    private const val CHANNEL_NAME = "Sedentarismo"
    private const val CHANNEL_DESC = "Avisos cuando llevas mucho tiempo sin moverte"

    private const val NOTIFICATION_ID = 5301

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = CHANNEL_DESC
        }
        manager.createNotificationChannel(channel)
    }

    fun showSedentaryAlert(
        context: Context,
        minutesIdle: Long,
        recentSteps: Long,
        isTest: Boolean = false,
        stepsUnreadable: Boolean = false
    ) {
        ensureChannel(context)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val contentIntent = PendingIntent.getActivity(context, 0, intent, pendingFlags)

        val title = if (isTest) "Prueba de aviso de sedentarismo" else "¡Hora de moverte!"
        val body = when {
            isTest && stepsUnreadable ->
                "No se pudieron leer los pasos. Revisa permisos y la fuente configurada."
            isTest ->
                "Prueba correcta. $recentSteps pasos en los últimos $minutesIdle min."
            recentSteps <= 0L ->
                "Llevas unos $minutesIdle min sin pasos detectados. Da una vuelta corta."
            else ->
                "Solo $recentSteps pasos en los últimos $minutesIdle min. Estírate o camina un poco."
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        }
    }
}
