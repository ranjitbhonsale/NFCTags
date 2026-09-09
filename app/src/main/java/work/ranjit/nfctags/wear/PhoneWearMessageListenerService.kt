package work.ranjit.nfctags.wear

import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import work.ranjit.nfctags.NetworkManager
import work.ranjit.nfctags.data.ActionType
import work.ranjit.nfctags.data.AppDatabase
import work.ranjit.nfctags.data.ScanHistoryEntity

class PhoneWearMessageListenerService : WearableListenerService() {

    companion object {
        private const val TAG = "PhoneWearListener"
        const val PATH_TRIGGER_AUTOMATION = "/trigger_automation"
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO)

    override fun onMessageReceived(messageEvent: MessageEvent) {
        if (messageEvent.path == PATH_TRIGGER_AUTOMATION) {
            val tagId = String(messageEvent.data, Charsets.UTF_8)
            Log.d(TAG, "Received trigger automation request from watch for tagId: $tagId")
            triggerAutomationForTag(tagId)
        }
    }

    private fun triggerAutomationForTag(tagId: String) {
        serviceScope.launch {
            val database = AppDatabase.getDatabase(applicationContext)
            val networkManager = NetworkManager()
            val event = database.automationDao().getAutomationByTagId(tagId)
            val tagEntity = database.tagDao().getTagById(tagId)

            if (event != null && event.isEnabled) {
                val processedUrl = event.url
                    .replace("{{tag_id}}", tagId)
                    .replace("{{timestamp}}", System.currentTimeMillis().toString())

                var resultStr = ""
                when (event.actionType) {
                    ActionType.OPEN_LINK -> {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(processedUrl)).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            startActivity(intent)
                            resultStr = "Watch triggered Open Link: $processedUrl"
                        } catch (e: Exception) {
                            resultStr = "Failed: ${e.message}"
                        }
                    }
                    ActionType.OPEN_APP -> {
                        val pkg = event.appPackage ?: ""
                        val launchIntent = packageManager.getLaunchIntentForPackage(pkg)?.apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        if (launchIntent != null) {
                            startActivity(launchIntent)
                            resultStr = "Watch triggered app: $pkg"
                        } else {
                            resultStr = "Failed to launch $pkg"
                        }
                    }
                    ActionType.WEBHOOK -> {
                        val dataToSend = tagEntity?.name?.ifEmpty { tagId } ?: tagId
                        resultStr = networkManager.sendNfcData(processedUrl, dataToSend, event.isPost)
                    }
                }

                database.scanHistoryDao().insert(
                    ScanHistoryEntity(
                        timestamp = System.currentTimeMillis(),
                        tagId = tagId,
                        payload = tagEntity?.name?.let { "Tag: $it" } ?: "Triggered from Watch",
                        webhookResult = resultStr
                    )
                )
            }
        }
    }
}
