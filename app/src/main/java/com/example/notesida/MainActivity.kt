package com.example.notesida

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import com.example.notesida.data.NoteRepository
import com.example.notesida.reminder.NotificationHelper
import com.example.notesida.reminder.ReminderScheduler
import com.example.notesida.service.FloatingService
import com.example.notesida.ui.main.MainScreen
import com.example.notesida.ui.theme.NotesidATheme

class MainActivity : ComponentActivity() {

    private val overlayGranted = mutableStateOf(false)
    private val exactAlarmOk = mutableStateOf(true)

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        NotificationHelper.createChannels(this)
        overlayGranted.value = Settings.canDrawOverlays(this)
        exactAlarmOk.value = canScheduleExactAlarms()
        requestNotificationPermissionIfNeeded()

        val repo = NoteRepository.get(this)
        setContent {
            NotesidATheme {
                val notes by repo.notes.collectAsState()
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainScreen(
                        overlayGranted = overlayGranted.value,
                        exactAlarmOk = exactAlarmOk.value,
                        serviceRunning = FloatingService.isRunning,
                        notes = notes,
                        onRequestOverlay = ::openOverlaySettings,
                        onRequestExactAlarm = ::openExactAlarmSettings,
                        onToggleService = ::toggleService,
                        onCompleteNote = { id -> completeNote(repo, id) },
                        onDeleteNote = { id -> deleteNote(repo, id) },
                        onClearCompleted = { repo.clearCompleted() },
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        overlayGranted.value = Settings.canDrawOverlays(this)
        exactAlarmOk.value = canScheduleExactAlarms()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun openOverlaySettings() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        startActivity(intent)
    }

    private fun canScheduleExactAlarms(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() ?: true
        } else {
            true
        }

    private fun openExactAlarmSettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val intent = Intent(
            Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
            Uri.parse("package:$packageName")
        )
        startActivity(intent)
    }

    private fun toggleService() {
        if (FloatingService.isRunning) {
            FloatingService.stop(this)
        } else if (Settings.canDrawOverlays(this)) {
            FloatingService.start(this)
        } else {
            openOverlaySettings()
        }
    }

    private fun completeNote(repo: NoteRepository, id: String) {
        val note = repo.find(id) ?: return
        ReminderScheduler.cancel(this, note)
        repo.markDone(id)
    }

    private fun deleteNote(repo: NoteRepository, id: String) {
        val note = repo.find(id) ?: return
        ReminderScheduler.cancel(this, note)
        repo.remove(id)
    }
}
