package com.example.notesida.reminder

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.notesida.MainActivity

/** 通知渠道与通知构建。 */
object NotificationHelper {

    const val CHANNEL_REMINDER = "note_reminders"
    const val CHANNEL_SERVICE = "floating_service"
    const val SERVICE_NOTIFICATION_ID = 1001
    private const val REMINDER_ID_BASE = 2000

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val reminderChannel = NotificationChannel(
            CHANNEL_REMINDER,
            "便签提醒",
            NotificationManager.IMPORTANCE_HIGH
        ).apply { description = "到达提醒时间时通知便签内容" }
        val serviceChannel = NotificationChannel(
            CHANNEL_SERVICE,
            "悬浮便签服务",
            NotificationManager.IMPORTANCE_LOW
        ).apply { description = "保持便签悬浮显示" }
        manager.createNotificationChannel(reminderChannel)
        manager.createNotificationChannel(serviceChannel)
    }

    fun buildServiceNotification(context: Context): Notification =
        NotificationCompat.Builder(context, CHANNEL_SERVICE)
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .setContentTitle("悬浮便签正在运行")
            .setContentText("点击返回应用管理便签")
            .setOngoing(true)
            .setContentIntent(launchAppIntent(context))
            .build()

    fun showReminder(context: Context, id: String, title: String, content: String) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val text = content.ifBlank { "该完成啦！" }
        val notification = NotificationCompat.Builder(context, CHANNEL_REMINDER)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title.ifBlank { "便签提醒" })
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(launchAppIntent(context))
            .build()
        manager.notify(REMINDER_ID_BASE + id.hashCode(), notification)
    }

    private fun launchAppIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
