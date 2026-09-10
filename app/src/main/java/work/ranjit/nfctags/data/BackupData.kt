package work.ranjit.nfctags.data

import kotlinx.serialization.Serializable
import work.ranjit.nfctags.data.ActionType

@Serializable
data class BackupData(
    val tags: List<SerializableNfcTag>,
    val automations: List<SerializableAutomation>
)

@Serializable
data class SerializableNfcTag(
    val tagId: String,
    val name: String,
    val dateAdded: Long
)

@Serializable
data class SerializableAutomation(
    val tagId: String,
    val actionType: String,
    val url: String = "",
    val isPost: Boolean = false,
    val isEnabled: Boolean = true,
    val appPackage: String? = null,
    val smsPhoneNumbers: String? = null,
    val smsMessage: String? = null
)
