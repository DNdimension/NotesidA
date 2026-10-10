package com.example.notesida.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.notesida.data.TodoNote

/** 使用 AlarmManager 在指定时间触发提醒。 */
object ReminderScheduler {

    const val ACTION_REMIND = "com.example.notesida.action.REMIND"
    const val EXTRA_ID = "extra_id"
    const val EXTRA_TITLE = "extra_title"
    const val EXTRA_CONTENT = "extra_content"

    fun schedule(context: Context, note: TodoNote) {
        if (note.reminderAt <= 0L || note.done) {
            cancel(context, note)
            return
        }
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        val pendingIntent = buildPendingIntent(context, note)
        val triggerAt = note.reminderAt
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !manager.canScheduleExactAlarms()) {
                // 没有精确闹钟权限时退化为非精确提醒，保证仍然能提醒。
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            } else {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            }
        } catch (_: SecurityException) {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        }
    }

    fun cancel(context: Context, note: TodoNote) {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        manager.cancel(buildPendingIntent(context, note))
    }

    private fun buildPendingIntent(context: Context, note: TodoNote): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_REMIND
            putExtra(EXTRA_ID, note.id)
            putExtra(EXTRA_TITLE, note.title)
            putExtra(EXTRA_CONTENT, note.content)
        }
        return PendingIntent.getBroadcast(
            context,
            note.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
