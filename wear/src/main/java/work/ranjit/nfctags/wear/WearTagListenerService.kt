package work.ranjit.nfctags.wear

import android.util.Log
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.serialization.json.Json

class WearTagListenerService : WearableListenerService() {

    companion object {
        private const val TAG = "WearTagListenerService"
        const val PATH_SYNC_TAGS = "/nfc_tags"
        const val KEY_TAGS_DATA = "tags_json"
    }

    private val json = Json { ignoreUnknownKeys = true }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        WearTagRepository.init(this)
        for (event in dataEvents) {
            if (event.type == DataEvent.TYPE_CHANGED && event.dataItem.uri.path == PATH_SYNC_TAGS) {
                val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap
                val tagsJson = dataMap.getString(KEY_TAGS_DATA)
                if (!tagsJson.isNullOrEmpty()) {
                    try {
                        val tagsList = json.decodeFromString<List<SyncedTag>>(tagsJson)
                        WearTagRepository.saveTags(this, tagsList)
                        Log.d(TAG, "Successfully synced ${tagsList.size} tags to watch")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to decode synced tags", e)
                    }
                }
            }
        }
    }
}
