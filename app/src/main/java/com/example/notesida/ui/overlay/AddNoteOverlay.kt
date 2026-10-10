package com.example.notesida.ui.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import java.util.Calendar
import java.util.TimeZone

private class ReminderPreset(val label: String, val millis: Long)

/**
 * 新增便签的悬浮表单。包含二字标题、项目内容、提醒时间（预设 + 精确选择）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddNoteOverlay(
    onCancel: () -> Unit,
    onSave: (title: String, content: String, reminderAt: Long) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var reminderAt by remember { mutableStateOf(0L) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var pendingDateMillis by remember { mutableStateOf<Long?>(null) }

    val now = remember { System.currentTimeMillis() }
    val presets = remember(now) {
        listOf(
            ReminderPreset("不提醒", 0L),
            ReminderPreset("10分钟后", now + 10 * 60_000L),
            ReminderPreset("30分钟后", now + 30 * 60_000L),
            ReminderPreset("1小时后", now + 60 * 60_000L),
            ReminderPreset("3小时后", now + 3 * 60 * 60_000L),
            ReminderPreset("明天9:00", tomorrowAtNine())
        )
    }
    // -1 表示未选择，presets.size 表示“精确选择”。
    var selectedIndex by remember { mutableStateOf(0) }

    Box(modifier = Modifier.fillMaxSize()) {
        // 半透明遮罩，点击空白处取消。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onCancel
                )
        )

        Surface(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .padding(24.dp)
                .pointerInput(Unit) { detectTapGestures { } },
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 12.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("新建悬浮便签", style = MaterialTheme.typography.titleLarge)

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(2) },
                    label = { Text("标题（最多2字）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("项目内容") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("提醒时间", style = MaterialTheme.typography.titleSmall)

                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    presets.forEachIndexed { index, preset ->
                        FilterChip(
                            selected = selectedIndex == index,
                            onClick = {
                                selectedIndex = index
                                reminderAt = preset.millis
                            },
                            label = { Text(preset.label) }
                        )
                    }
                    FilterChip(
                        selected = selectedIndex == presets.size,
                        onClick = { showDatePicker = true },
                        label = { Text("精确选择") }
                    )
                }

                Text(
                    text = if (reminderAt > 0L) "将于 " + formatReminder(reminderAt) + " 提醒"
                    else "不提醒",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onCancel) { Text("取消") }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        enabled = title.isNotBlank(),
                        onClick = { onSave(title.trim(), content.trim(), reminderAt) }
                    ) {
                        Text("保存")
                    }
                }
            }
        }

        if (showDatePicker) {
            val dateState = rememberDatePickerState()
            TimeDateSheet(onDismiss = { showDatePicker = false }) {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .heightIn(max = 520.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    DatePicker(state = dateState)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showDatePicker = false }) { Text("取消") }
                        Spacer(Modifier.width(8.dp))
                        TextButton(
                            enabled = dateState.selectedDateMillis != null,
                            onClick = {
                                pendingDateMillis = dateState.selectedDateMillis
                                showDatePicker = false
                                showTimePicker = true
                            }
                        ) {
                            Text("下一步")
                        }
                    }
                }
            }
        }

        if (showTimePicker) {
            val timeState = rememberTimePickerState(is24Hour = true)
            TimeDateSheet(onDismiss = { showTimePicker = false }) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("选择时间", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    TimePicker(state = timeState)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showTimePicker = false }) { Text("取消") }
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = {
                            val dateMillis = pendingDateMillis ?: System.currentTimeMillis()
                            reminderAt = combineDateAndTime(dateMillis, timeState.hour, timeState.minute)
                            selectedIndex = presets.size
                            showTimePicker = false
                        }) {
                            Text("确定")
                        }
                    }
                }
            }
        }
    }
}

/** 覆盖在表单之上的选择器弹层。 */
@Composable
private fun TimeDateSheet(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                )
        )
        Surface(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .padding(16.dp)
                .pointerInput(Unit) { detectTapGestures { } },
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 12.dp
        ) {
            Box(modifier = Modifier.padding(8.dp), contentAlignment = Alignment.Center) {
                content()
            }
        }
    }
}

private fun combineDateAndTime(dateUtcMillis: Long, hour: Int, minute: Int): Long {
    val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        timeInMillis = dateUtcMillis
    }
    val local = Calendar.getInstance().apply {
        clear()
        set(
            utc.get(Calendar.YEAR),
            utc.get(Calendar.MONTH),
            utc.get(Calendar.DAY_OF_MONTH),
            hour,
            minute,
            0
        )
    }
    return local.timeInMillis
}

private fun tomorrowAtNine(): Long {
    return Calendar.getInstance().apply {
        add(Calendar.DAY_OF_YEAR, 1)
        set(Calendar.HOUR_OF_DAY, 9)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}
