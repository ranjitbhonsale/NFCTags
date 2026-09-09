package work.ranjit.nfctags.wear

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object WearTagRepository {
    private const val PREFS_NAME = "wear_tags_prefs"
    private const val KEY_TAGS = "key_synced_tags"
    private const val KEY_ACTIVE_TAG_ID = "key_active_tag_id"

    private val json = Json { ignoreUnknownKeys = true }

    private val _tags = MutableStateFlow<List<SyncedTag>>(emptyList())
    val tags: StateFlow<List<SyncedTag>> = _tags.asStateFlow()

    private val _activeTag = MutableStateFlow<SyncedTag?>(null)
    val activeTag: StateFlow<SyncedTag?> = _activeTag.asStateFlow()

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedJson = prefs.getString(KEY_TAGS, null)
        val loadedTags = if (!savedJson.isNullOrEmpty()) {
            try {
                json.decodeFromString<List<SyncedTag>>(savedJson)
            } catch (e: Exception) {
                emptyList()
            }
        } else {
            emptyList()
        }
        _tags.value = loadedTags

        val activeId = prefs.getString(KEY_ACTIVE_TAG_ID, null)
        _activeTag.value = loadedTags.find { it.tagId == activeId } ?: loadedTags.firstOrNull()
    }

    fun saveTags(context: Context, newTags: List<SyncedTag>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_TAGS, json.encodeToString(newTags)).apply()
        _tags.value = newTags

        val currentActive = _activeTag.value
        if (currentActive == null || !newTags.any { it.tagId == currentActive.tagId }) {
            setActiveTag(context, newTags.firstOrNull())
        }
    }

    fun setActiveTag(context: Context, tag: SyncedTag?) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_ACTIVE_TAG_ID, tag?.tagId).apply()
        _activeTag.value = tag
    }
}
