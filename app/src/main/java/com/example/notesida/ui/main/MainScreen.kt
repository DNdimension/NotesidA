package com.example.notesida.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.notesida.data.TodoNote
import com.example.notesida.ui.overlay.formatReminder

/**
 * 应用主界面：申请权限、启停悬浮服务，并管理已有便签。
 */
@Composable
fun MainScreen(
    overlayGranted: Boolean,
    exactAlarmOk: Boolean,
    serviceRunning: Boolean,
    notes: List<TodoNote>,
    onRequestOverlay: () -> Unit,
    onRequestExactAlarm: () -> Unit,
    onToggleService: () -> Unit,
    onCompleteNote: (String) -> Unit,
    onDeleteNote: (String) -> Unit,
    onClearCompleted: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("悬浮便签", style = MaterialTheme.typography.headlineMedium)

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = if (overlayGranted) "✅ 已获得悬浮窗权限" else "⚠️ 尚未获得悬浮窗权限",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "便签会悬浮在屏幕左右两侧，左侧悬浮按钮用于新增便签。" +
                        "每个便签包含二字标题、项目内容和提醒时间。",
                    style = MaterialTheme.typography.bodyMedium
                )
                if (!overlayGranted) {
                    Button(onClick = onRequestOverlay) {
                        Text("授予悬浮窗权限")
                    }
                }
                if (!exactAlarmOk) {
                    Text(
                        text = "开启精确闹钟权限后，提醒会准时触发。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(onClick = onRequestExactAlarm) {
                        Text("允许精确闹钟")
                    }
                }
                Button(onClick = onToggleService) {
                    Text(if (serviceRunning) "停止悬浮便签" else "启动悬浮便签")
                }
            }
        }

        val active = notes.filter { !it.done }
        val completed = notes.filter { it.done }

        Text(
            text = "悬浮中（${active.size}）",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        if (active.isEmpty()) {
            Text(
                text = "还没有便签，点击左侧悬浮按钮 ＋ 添加。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            active.forEach { note ->
                NoteCard(
                    note = note,
                    onComplete = { onCompleteNote(note.id) },
                    onDelete = { onDeleteNote(note.id) }
                )
            }
        }

        if (completed.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "已完成（${completed.size}）",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                OutlinedButton(onClick = onClearCompleted) { Text("清空") }
            }
            completed.forEach { note ->
                NoteCard(
                    note = note,
                    onComplete = null,
                    onDelete = { onDeleteNote(note.id) }
                )
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun NoteCard(
    note: TodoNote,
    onComplete: (() -> Unit)?,
    onDelete: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = note.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                if (note.done) {
                    Text("已完成", color = MaterialTheme.colorScheme.primary)
                }
            }
            if (note.content.isNotBlank()) {
                Text(note.content, style = MaterialTheme.typography.bodyMedium)
            }
            if (note.reminderAt > 0L) {
                Text(
                    text = "⏰ " + formatReminder(note.reminderAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (onComplete != null) {
                    Button(onClick = onComplete) { Text("完成") }
                }
                OutlinedButton(onClick = onDelete) { Text("删除") }
            }
        }
    }
}
