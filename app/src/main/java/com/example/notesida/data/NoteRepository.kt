package com.example.notesida.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray

/**
 * 便签数据仓库：把便签保存到 SharedPreferences，并通过 [StateFlow] 对外广播。
 * 单例，方便 Service、Activity、广播接收器共享同一份数据。
 */
class NoteRepository private constructor(context: Context) {

    private val prefs =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _notes = MutableStateFlow(load())
    val notes: StateFlow<List<TodoNote>> = _notes.asStateFlow()

    private fun load(): List<TodoNote> {
        val raw = prefs.getString(KEY_NOTES, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).map { TodoNote.fromJson(array.getJSONObject(it)) }
        }.getOrDefault(emptyList())
    }

    private fun persist(list: List<TodoNote>) {
        val array = JSONArray()
        list.forEach { array.put(it.toJson()) }
        prefs.edit().putString(KEY_NOTES, array.toString()).apply()
    }

    private fun mutate(block: (List<TodoNote>) -> List<TodoNote>) {
        val next = block(_notes.value)
        _notes.value = next
        persist(next)
    }

    fun add(note: TodoNote) = mutate { it + note }

    fun remove(id: String) = mutate { list -> list.filterNot { it.id == id } }

    fun markDone(id: String) = mutate { list ->
        list.map { if (it.id == id) it.copy(done = true) else it }
    }

    fun updatePlacement(id: String, side: NoteSide, y: Int) = mutate { list ->
        list.map { if (it.id == id) it.copy(side = side, y = y) else it }
    }

    fun clearCompleted() = mutate { list -> list.filterNot { it.done } }

    fun find(id: String): TodoNote? = _notes.value.firstOrNull { it.id == id }

    companion object {
        private const val PREFS_NAME = "notesid_store"
        private const val KEY_NOTES = "notes"

        @Volatile
        private var instance: NoteRepository? = null

        fun get(context: Context): NoteRepository =
            instance ?: synchronized(this) {
                instance ?: NoteRepository(context).also { instance = it }
            }
    }
}
