package com.example.notesida.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.notesida.data.NoteRepository

/** 闹钟触发时弹出提醒通知。 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ReminderScheduler.ACTION_REMIND) return
        val id = intent.getStringExtra(ReminderScheduler.EXTRA_ID) ?: return
        val note = NoteRepository.get(context).find(id)
        if (note != null && note.done) return
        val title = note?.title ?: intent.getStringExtra(ReminderScheduler.EXTRA_TITLE).orEmpty()
        val content = note?.content ?: intent.getStringExtra(ReminderScheduler.EXTRA_CONTENT).orEmpty()
        NotificationHelper.showReminder(context, id, title, content)
    }
}
