package com.example.notesida.data

import org.json.JSONObject

/** 便签显示在屏幕的哪一侧。 */
enum class NoteSide { LEFT, RIGHT }

/**
 * 一条悬浮便签。
 *
 * @param id          唯一标识
 * @param title       二字标题，悬浮时显示
 * @param content     项目内容，点击悬浮项后显示
 * @param reminderAt  提醒时间（ephemeral millis），0 表示不提醒
 * @param side        停靠在屏幕左侧还是右侧
 * @param y           竖直位置（像素），用于恢复拖动后的位置
 * @param done        是否已完成
 */
data class TodoNote(
    val id: String,
    val title: String,
    val content: String,
    val reminderAt: Long,
    val side: NoteSide,
    val y: Int,
    val done: Boolean = false
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("content", content)
        put("reminderAt", reminderAt)
        put("side", side.name)
        put("y", y)
        put("done", done)
    }

    companion object {
        fun fromJson(json: JSONObject): TodoNote = TodoNote(
            id = json.getString("id"),
            title = json.optString("title"),
            content = json.optString("content"),
            reminderAt = json.optLong("reminderAt", 0L),
            side = runCatching { NoteSide.valueOf(json.optString("side", "LEFT")) }
                .getOrDefault(NoteSide.LEFT),
            y = json.optInt("y", 0),
            done = json.optBoolean("done", false)
        )
    }
}
