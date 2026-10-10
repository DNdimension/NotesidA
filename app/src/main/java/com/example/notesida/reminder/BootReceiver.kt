package com.example.notesida.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.notesida.data.NoteRepository

/** 开机后重新安排尚未到期的提醒。 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_LOCKED_BOOT_COMPLETED) {
            return
        }
        val now = System.currentTimeMillis()
        NoteRepository.get(context).notes.value
            .filter { !it.done && it.reminderAt > now }
            .forEach { ReminderScheduler.schedule(context, it) }
    }
}
