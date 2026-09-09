package work.ranjit.nfctags.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TagDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertTag(tag: NfcTagEntity)

    @Query("SELECT * FROM nfc_tags ORDER BY dateAdded DESC")
    fun getAllTags(): Flow<List<NfcTagEntity>>

    @Query("SELECT * FROM nfc_tags ORDER BY dateAdded DESC")
    fun getAllTagsSync(): List<NfcTagEntity>

    @Query("SELECT * FROM nfc_tags WHERE tagId = :tagId LIMIT 1")
    fun getTagById(tagId: String): NfcTagEntity?

    @Delete
    fun deleteTag(tag: NfcTagEntity)
}
