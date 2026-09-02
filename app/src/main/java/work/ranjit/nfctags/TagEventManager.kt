package work.ranjit.nfctags

import android.content.Context
import android.content.SharedPreferences

data class NfcEvent(val url: String, val isPost: Boolean)

class TagEventManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("NfcTagEvents", Context.MODE_PRIVATE)

    fun saveEvent(tagId: String, url: String, isPost: Boolean) {
        prefs.edit().apply {
            putString("${tagId}_url", url)
            putBoolean("${tagId}_isPost", isPost)
            apply()
        }
    }

    fun getEvent(tagId: String): NfcEvent? {
        val url = prefs.getString("${tagId}_url", null)
        if (url != null) {
            val isPost = prefs.getBoolean("${tagId}_isPost", false)
            return NfcEvent(url, isPost)
        }
        return null
    }

    fun clearEvent(tagId: String) {
        prefs.edit().apply {
            remove("${tagId}_url")
            remove("${tagId}_isPost")
            apply()
        }
    }
}
