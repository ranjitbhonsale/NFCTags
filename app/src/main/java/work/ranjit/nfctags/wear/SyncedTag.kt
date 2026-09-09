package work.ranjit.nfctags.wear

import kotlinx.serialization.Serializable

@Serializable
data class SyncedTag(
    val tagId: String,
    val name: String,
    val payload: String = "",
    val automationUrl: String? = null,
    val actionType: String? = null
)
