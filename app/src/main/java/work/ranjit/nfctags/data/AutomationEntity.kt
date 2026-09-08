package work.ranjit.nfctags.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class ActionType {
    WEBHOOK, OPEN_LINK, OPEN_APP
}

@Entity(tableName = "automations")
data class AutomationEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val tagId: String,
    val actionType: ActionType,
    val appPackage: String? = null,
    val url: String,
    val isPost: Boolean,
    val isEnabled: Boolean = true
)
