package work.ranjit.nfctags.wear

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import work.ranjit.nfctags.data.AppDatabase

object WearDataSyncManager {
    private const val TAG = "WearDataSyncManager"
    private const val PATH_SYNC_TAGS = "/nfc_tags"
    private const val KEY_TAGS_DATA = "tags_json"

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun syncTagsToWatch(context: Context, database: AppDatabase): Boolean = withContext(Dispatchers.IO) {
        try {
            val tags = database.tagDao().getAllTagsSync()
            val automations = database.automationDao().getAllAutomationsSync()

            val syncedList = tags.map { tag ->
                val auto = automations.find { it.tagId == tag.tagId }
                SyncedTag(
                    tagId = tag.tagId,
                    name = tag.name,
                    payload = "TAG_ID:${tag.tagId}",
                    automationUrl = auto?.url,
                    actionType = auto?.actionType?.name
                )
            }

            val tagsJson = json.encodeToString(syncedList)

            val putDataMapReq = PutDataMapRequest.create(PATH_SYNC_TAGS).apply {
                dataMap.putString(KEY_TAGS_DATA, tagsJson)
                dataMap.putLong("timestamp", System.currentTimeMillis())
            }

            val putDataReq = putDataMapReq.asPutDataRequest().setUrgent()
            Wearable.getDataClient(context).putDataItem(putDataReq).await()
            Log.d(TAG, "Successfully pushed ${syncedList.size} tags to watch data layer")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync tags to watch", e)
            false
        }
    }
}
