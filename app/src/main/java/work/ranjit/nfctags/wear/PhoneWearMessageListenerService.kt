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
import work.ranjit.nfctags.LocationHelper
import work.ranjit.nfctags.SmsSender
import work.ranjit.nfctags.data.ScanHistoryEntity

class PhoneWearMessageListenerService : WearableListenerService() {

    companion object {
        private const val TAG = "PhoneWearListener"
        const val PATH_TRIGGER_AUTOMATION = "/trigger_automation"
        const val PATH_REQUEST_SYNC = "/request_sync"
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO)

    override fun onMessageReceived(messageEvent: MessageEvent) {
        if (messageEvent.path == PATH_TRIGGER_AUTOMATION) {
            val tagId = String(messageEvent.data, Charsets.UTF_8)
            Log.d(TAG, "Received trigger automation request from watch for tagId: $tagId")
            triggerAutomationForTag(tagId)
        } else if (messageEvent.path == PATH_REQUEST_SYNC) {
            Log.d(TAG, "Received /request_sync from watch. Pushing tags...")
            serviceScope.launch {
                val database = AppDatabase.getDatabase(applicationContext)
                WearDataSyncManager.syncTagsToWatch(applicationContext, database)
            }
        }
    }

    private fun triggerAutomationForTag(tagId: String) {
        serviceScope.launch {
            val database = AppDatabase.getDatabase(applicationContext)
            val networkManager = NetworkManager()
            val event = database.automationDao().getAutomationByTagId(tagId)
            val tagEntity = database.tagDao().getTagById(tagId)

            if (event != null && event.isEnabled) {
                var processedUrl = event.url
                    .replace("{{tag_id}}", tagId)
                    .replace("{{timestamp}}", System.currentTimeMillis().toString())

                if (event.attachLocation || processedUrl.contains("{{lat}}") || processedUrl.contains("{{location}}") || processedUrl.contains("{{maps_url}}")) {
                    val loc = LocationHelper.getCurrentLocation(applicationContext)
                    processedUrl = LocationHelper.processLocationVariables(processedUrl, loc)
                    if (event.attachLocation && loc != null && !processedUrl.contains("lat=") && !processedUrl.contains("maps.google.com")) {
                        val sep = if (processedUrl.contains("?")) "&" else "?"
                        processedUrl += "${sep}lat=${loc.latitude}&lon=${loc.longitude}"
                    }
                }

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
                    ActionType.SEND_SMS -> {
                        var processedMessage = (event.smsMessage ?: "")
                            .replace("{{tag_id}}", tagId)
                            .replace("{{timestamp}}", System.currentTimeMillis().toString())

                        if (event.attachLocation || processedMessage.contains("{{lat}}") || processedMessage.contains("{{location}}") || processedMessage.contains("{{maps_url}}")) {
                            val loc = LocationHelper.getCurrentLocation(applicationContext)
                            processedMessage = LocationHelper.processLocationVariables(processedMessage, loc)
                            if (event.attachLocation && loc != null && !processedMessage.contains("maps.google.com")) {
                                processedMessage += "\nLocation: ${LocationHelper.formatMapsUrl(loc.latitude, loc.longitude)}"
                            }
                        }

                        resultStr = SmsSender.sendSms(
                            applicationContext,
                            event.smsPhoneNumbers,
                            processedMessage
                        )
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
