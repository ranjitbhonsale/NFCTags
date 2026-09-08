package work.ranjit.nfctags.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "nfc_tags")
data class NfcTagEntity(
    @PrimaryKey val tagId: String,
    val name: String,
    val dateAdded: Long
)
