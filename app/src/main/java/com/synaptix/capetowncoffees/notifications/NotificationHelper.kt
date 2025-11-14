package com.synaptix.capetowncoffees.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.synaptix.capetowncoffees.MainActivity
import com.synaptix.capetowncoffees.R
import android.app.PendingIntent

object NotificationHelper {
    const val CHANNEL_ID: String = "ctc_activity"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.app_name) + " updates",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Activity notifications for Cape Town Coffees"
                enableLights(true)
                lightColor = Color.MAGENTA
                enableVibration(true)
            }
            manager.createNotificationChannel(channel)
        }
    }

    fun showSimple(context: Context, title: String, message: String, notificationId: Int = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()) {
        ensureChannel(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE else PendingIntent.FLAG_UPDATE_CURRENT
        val pending = PendingIntent.getActivity(context, 0, intent, flags)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pending)

        NotificationManagerCompat.from(context).notify(notificationId, builder.build())
    }
}
