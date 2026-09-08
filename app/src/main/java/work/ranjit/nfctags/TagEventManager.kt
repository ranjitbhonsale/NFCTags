package work.ranjit.nfctags

import android.content.Context
import android.content.SharedPreferences

enum class ActionType {
    WEBHOOK, OPEN_LINK
}

data class NfcEvent(val url: String, val isPost: Boolean, val actionType: ActionType = ActionType.WEBHOOK)

class TagEventManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("NfcTagEvents", Context.MODE_PRIVATE)

    fun saveEvent(tagId: String, url: String, isPost: Boolean, actionType: ActionType) {
        prefs.edit().apply {
            putString("${tagId}_url", url)
            putBoolean("${tagId}_isPost", isPost)
            putString("${tagId}_actionType", actionType.name)
            apply()
        }
    }

    fun getEvent(tagId: String): NfcEvent? {
        val url = prefs.getString("${tagId}_url", null)
        if (url != null) {
            val isPost = prefs.getBoolean("${tagId}_isPost", false)
            val actionTypeStr = prefs.getString("${tagId}_actionType", ActionType.WEBHOOK.name)
            val actionType = try { ActionType.valueOf(actionTypeStr!!) } catch (e: Exception) { ActionType.WEBHOOK }
            return NfcEvent(url, isPost, actionType)
        }
        return null
    }

    fun clearEvent(tagId: String) {
        prefs.edit().apply {
            remove("${tagId}_url")
            remove("${tagId}_isPost")
            remove("${tagId}_actionType")
            apply()
        }
    }
}
