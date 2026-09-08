package work.ranjit.nfctags.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AutomationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAutomation(automation: AutomationEntity)

    @Update
    fun updateAutomation(automation: AutomationEntity)

    @Delete
    fun deleteAutomation(automation: AutomationEntity)

    @Query("SELECT * FROM automations ORDER BY id DESC")
    fun getAllAutomations(): Flow<List<AutomationEntity>>

    @Query("SELECT * FROM automations WHERE tagId = :tagId AND isEnabled = 1")
    fun getActiveAutomationsForTag(tagId: String): List<AutomationEntity>
    
    // For migration from SharedPreferences
    @Query("SELECT * FROM automations WHERE tagId = :tagId LIMIT 1")
    fun getAutomationByTagId(tagId: String): AutomationEntity?
}
