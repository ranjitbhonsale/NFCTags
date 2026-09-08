package work.ranjit.nfctags.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import work.ranjit.nfctags.ActionType

@Entity(tableName = "automations")
data class AutomationEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val tagId: String,
    val actionType: ActionType,
    val url: String,
    val isPost: Boolean,
    val isEnabled: Boolean = true
)
