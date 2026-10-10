package com.example.notesida.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.KeyEvent
import android.view.WindowManager
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.notesida.data.NoteRepository
import com.example.notesida.data.NoteSide
import com.example.notesida.data.TodoNote
import com.example.notesida.reminder.NotificationHelper
import com.example.notesida.reminder.ReminderScheduler
import com.example.notesida.ui.overlay.AddButtonContent
import com.example.notesida.ui.overlay.AddNoteOverlay
import com.example.notesida.ui.overlay.FloatingNoteContent
import com.example.notesida.ui.theme.NotesidATheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.roundToInt

/**
 * 负责在系统窗口上绘制所有悬浮便签、左侧新增按钮以及新增表单的前台服务。
 */
class FloatingService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var repo: NoteRepository
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val noteWindows = LinkedHashMap<String, NoteWindow>()
    private var addButton: AddButtonWindow? = null
    private var addOverlay: AddOverlay? = null

    private var screenWidth = 0
    private var screenHeight = 0

    private var foregroundStarted = false
    private var collecting = false

    private val overlayType: Int
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        NotificationHelper.createChannels(this)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        repo = NoteRepository.get(this)
        updateMetrics()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundInternal()
        if (!foregroundStarted) {
            foregroundStarted = true
            updateMetrics()
            ensureAddButton()
            syncNotes(repo.notes.value)
            if (!collecting) {
                collecting = true
                scope.launch {
                    repo.notes.collect { syncNotes(it) }
                }
            }
        }
        return START_STICKY
    }

    private fun startForegroundInternal() {
        val notification = NotificationHelper.buildServiceNotification(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NotificationHelper.SERVICE_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NotificationHelper.SERVICE_NOTIFICATION_ID, notification)
        }
    }

    override fun onDestroy() {
        isRunning = false
        scope.cancel()
        addOverlay?.dismiss()
        addOverlay = null
        addButton?.remove()
        addButton = null
        noteWindows.values.toList().forEach { it.remove() }
        noteWindows.clear()
        super.onDestroy()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateMetrics()
        noteWindows.values.forEach { it.resnap() }
        addButton?.resnap()
    }

    // ---------------------------------------------------------------------
    // 便签窗口同步
    // ---------------------------------------------------------------------

    private fun syncNotes(notes: List<TodoNote>) {
        if (!Settings.canDrawOverlays(this)) return
        updateMetrics()

        val active = notes.filter { !it.done }
        val activeIds = active.map { it.id }.toSet()

        noteWindows.keys.filterNot { it in activeIds }.forEach { removeNoteWindow(it) }

        active.forEach { note ->
            val existing = noteWindows[note.id]
            if (existing == null) addNoteWindow(note) else existing.applyStoredPlacement(note)
        }
    }

    private fun addNoteWindow(note: TodoNote) {
        val window = NoteWindow(note.id)
        noteWindows[note.id] = window
        window.show(note)
    }

    private fun removeNoteWindow(id: String) {
        noteWindows.remove(id)?.remove()
    }

    private fun ensureAddButton() {
        if (addButton != null || !Settings.canDrawOverlays(this)) return
        val button = AddButtonWindow()
        addButton = button
        button.show()
    }

    private fun showAddOverlay() {
        if (addOverlay != null || !Settings.canDrawOverlays(this)) return
        val overlay = AddOverlay()
        addOverlay = overlay
        overlay.show()
    }

    private fun hideAddOverlay() {
        addOverlay?.dismiss()
        addOverlay = null
    }

    private fun saveNewNote(title: String, content: String, reminderAt: Long) {
        updateMetrics()
        val active = repo.notes.value.filter { !it.done }
        val side = if (active.size % 2 == 0) NoteSide.LEFT else NoteSide.RIGHT
        val y = (screenHeight / 4 + active.size * dp(72))
            .coerceIn(0, (screenHeight - dp(80)).coerceAtLeast(0))
        val note = TodoNote(
            id = UUID.randomUUID().toString(),
            title = title.take(2),
            content = content,
            reminderAt = reminderAt,
            side = side,
            y = y
        )
        repo.add(note)
        if (reminderAt > 0L) ReminderScheduler.schedule(this, note)
    }

    private fun completeNote(id: String) {
        val note = repo.find(id) ?: return
        ReminderScheduler.cancel(this, note)
        repo.markDone(id)
    }

    private fun deleteNote(id: String) {
        val note = repo.find(id) ?: return
        ReminderScheduler.cancel(this, note)
        repo.remove(id)
    }

    private fun updateMetrics() {
        val metrics = resources.displayMetrics
        screenWidth = metrics.widthPixels
        screenHeight = metrics.heightPixels
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

    // ---------------------------------------------------------------------
    // 单个悬浮便签窗口
    // ---------------------------------------------------------------------

    private inner class NoteWindow(private val noteId: String) {
        val view = ComposeView(this@FloatingService)
        private val owner = OverlayLifecycleOwner()
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START }

        private var dragging = false

        fun show(note: TodoNote) {
            attachOwner(view, owner)

            params.x = if (note.side == NoteSide.LEFT) 0 else (screenWidth - dp(80)).coerceAtLeast(0)
            params.y = note.y.coerceIn(0, (screenHeight - dp(60)).coerceAtLeast(0))

            view.setContent {
                NotesidATheme(dynamicColor = false) {
                    val notes by repo.notes.collectAsState()
                    val current = notes.firstOrNull { it.id == noteId }
                    if (current != null) {
                        FloatingNoteContent(
                            note = current,
                            onComplete = { completeNote(noteId) },
                            onDelete = { deleteNote(noteId) },
                            onDrag = { dx, dy -> onDrag(dx, dy) },
                            onDragEnd = { onDragEnd() },
                            onSizeChanged = { _, _ -> clampAndSnap() }
                        )
                    }
                }
            }

            windowManager.addView(view, params)
            view.post {
                snapToSide(if (note.side == NoteSide.LEFT) NoteSide.LEFT else NoteSide.RIGHT)
            }
        }

        fun applyStoredPlacement(note: TodoNote) {
            if (dragging || view.width == 0) return
            params.x = if (note.side == NoteSide.LEFT) {
                0
            } else {
                (screenWidth - view.width).coerceAtLeast(0)
            }
            params.y = note.y.coerceIn(0, (screenHeight - view.height).coerceAtLeast(0))
            runCatching { windowManager.updateViewLayout(view, params) }
        }

        fun remove() {
            runCatching { windowManager.removeView(view) }
            owner.destroy()
        }

        private fun onDrag(dx: Float, dy: Float) {
            dragging = true
            val maxX = (screenWidth - view.width).coerceAtLeast(0)
            val maxY = (screenHeight - view.height).coerceAtLeast(0)
            params.x = (params.x + dx.roundToInt()).coerceIn(0, maxX)
            params.y = (params.y + dy.roundToInt()).coerceIn(0, maxY)
            runCatching { windowManager.updateViewLayout(view, params) }
        }

        private fun onDragEnd() {
            dragging = false
            val centerX = params.x + view.width / 2f
            val side = if (centerX < screenWidth / 2f) NoteSide.LEFT else NoteSide.RIGHT
            snapToSide(side)
            repo.updatePlacement(noteId, side, params.y)
        }

        private fun clampAndSnap() {
            if (dragging || view.width == 0) return
            view.post {
                val side = if (params.x + view.width / 2f < screenWidth / 2f) {
                    NoteSide.LEFT
                } else {
                    NoteSide.RIGHT
                }
                snapToSide(side)
            }
        }

        private fun snapToSide(side: NoteSide) {
            if (view.width == 0) return
            params.x = if (side == NoteSide.LEFT) {
                0
            } else {
                (screenWidth - view.width).coerceAtLeast(0)
            }
            params.y = params.y.coerceIn(0, (screenHeight - view.height).coerceAtLeast(0))
            runCatching { windowManager.updateViewLayout(view, params) }
        }

        fun resnap() {
            view.post {
                if (view.width == 0) return@post
                val side = if (params.x + view.width / 2f < screenWidth / 2f) {
                    NoteSide.LEFT
                } else {
                    NoteSide.RIGHT
                }
                snapToSide(side)
            }
        }
    }

    // ---------------------------------------------------------------------
    // 左下角新增按钮窗口
    // ---------------------------------------------------------------------

    private inner class AddButtonWindow {
        val view = ComposeView(this@FloatingService)
        private val owner = OverlayLifecycleOwner()
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(4)
            y = screenHeight / 3
        }

        fun show() {
            attachOwner(view, owner)
            view.setContent {
                NotesidATheme(dynamicColor = false) {
                    AddButtonContent(
                        onAdd = { showAddOverlay() },
                        onDrag = { dx, dy -> onDrag(dx, dy) },
                        onDragEnd = { onDragEnd() }
                    )
                }
            }
            windowManager.addView(view, params)
        }

        fun remove() {
            runCatching { windowManager.removeView(view) }
            owner.destroy()
        }

        private fun onDrag(dx: Float, dy: Float) {
            val maxX = (screenWidth - view.width).coerceAtLeast(0)
            val maxY = (screenHeight - view.height).coerceAtLeast(0)
            params.x = (params.x + dx.roundToInt()).coerceIn(0, maxX)
            params.y = (params.y + dy.roundToInt()).coerceIn(0, maxY)
            runCatching { windowManager.updateViewLayout(view, params) }
        }

        private fun onDragEnd() {
            if (view.width == 0) return
            val centerX = params.x + view.width / 2f
            params.x = if (centerX < screenWidth / 2f) 0 else (screenWidth - view.width).coerceAtLeast(0)
            params.y = params.y.coerceIn(0, (screenHeight - view.height).coerceAtLeast(0))
            runCatching { windowManager.updateViewLayout(view, params) }
        }

        fun resnap() {
            view.post {
                if (view.width == 0) return@post
                val centerX = params.x + view.width / 2f
                params.x = if (centerX < screenWidth / 2f) 0 else (screenWidth - view.width).coerceAtLeast(0)
                params.y = params.y.coerceIn(0, (screenHeight - view.height).coerceAtLeast(0))
                runCatching { windowManager.updateViewLayout(view, params) }
            }
        }
    }

    // ---------------------------------------------------------------------
    // 新增表单窗口（可获得焦点，便于输入文字）
    // ---------------------------------------------------------------------

    private inner class AddOverlay {
        val view = ComposeView(this@FloatingService)
        private val owner = OverlayLifecycleOwner()
        @Suppress("DEPRECATION")
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
        }

        fun show() {
            attachOwner(view, owner)
            view.setContent {
                NotesidATheme(dynamicColor = false) {
                    AddNoteOverlay(
                        onCancel = { hideAddOverlay() },
                        onSave = { title, content, reminderAt ->
                            saveNewNote(title, content, reminderAt)
                            hideAddOverlay()
                        }
                    )
                }
            }
            windowManager.addView(view, params)
            view.requestFocus()
            view.setOnKeyListener { _, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                    hideAddOverlay()
                    true
                } else {
                    false
                }
            }
        }

        fun dismiss() {
            runCatching { windowManager.removeView(view) }
            owner.destroy()
        }
    }

    private fun attachOwner(view: ComposeView, owner: OverlayLifecycleOwner) {
        view.setViewTreeLifecycleOwner(owner)
        view.setViewTreeViewModelStoreOwner(owner)
        view.setViewTreeSavedStateRegistryOwner(owner)
        owner.attach()
    }

    companion object {
        /** 供界面观察服务是否在运行。 */
        var isRunning by mutableStateOf(false)
            private set

        fun start(context: Context) {
            val intent = Intent(context, FloatingService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, FloatingService::class.java))
        }
    }
}
