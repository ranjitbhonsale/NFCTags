package work.ranjit.nfctags

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import work.ranjit.nfctags.data.*
import java.io.BufferedReader
import java.io.InputStreamReader

class BackupManager(private val context: Context, private val database: AppDatabase) {

    suspend fun exportBackup(uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val tags = database.tagDao().getAllTags().first()
            val automations = database.automationDao().getAllAutomations().first()

            val backupData = BackupData(
                tags = tags.map { SerializableNfcTag(it.tagId, it.name, it.dateAdded) },
                automations = automations.map { 
                    SerializableAutomation(
                        it.tagId, 
                        it.actionType.name, 
                        it.url, 
                        it.isPost, 
                        it.isEnabled
                    ) 
                }
            )

            val jsonString = Json { prettyPrint = true }.encodeToString(backupData)

            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(jsonString.toByteArray())
            } ?: return@withContext Result.failure(Exception("Could not open output stream"))

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun importBackup(uri: Uri): Result<Pair<Int, Int>> = withContext(Dispatchers.IO) {
        try {
            val jsonString = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).readText()
            } ?: return@withContext Result.failure(Exception("Could not open input stream"))

            val backupData = Json { ignoreUnknownKeys = true }.decodeFromString<BackupData>(jsonString)

            var importedTags = 0
            var importedAutomations = 0

            // Import tags
            for (tag in backupData.tags) {
                database.tagDao().insertTag(
                    NfcTagEntity(
                        tagId = tag.tagId,
                        name = tag.name,
                        dateAdded = tag.dateAdded
                    )
                )
                importedTags++
            }

            // Import automations
            for (auto in backupData.automations) {
                val actionType = try {
                    ActionType.valueOf(auto.actionType)
                } catch (e: Exception) {
                    ActionType.WEBHOOK
                }
                
                val existing = database.automationDao().getAutomationByTagId(auto.tagId)
                if (existing != null) {
                    database.automationDao().updateAutomation(
                        existing.copy(
                            actionType = actionType,
                            url = auto.url,
                            isPost = auto.isPost,
                            isEnabled = auto.isEnabled
                        )
                    )
                } else {
                    database.automationDao().insertAutomation(
                        AutomationEntity(
                            tagId = auto.tagId,
                            actionType = actionType,
                            url = auto.url,
                            isPost = auto.isPost,
                            isEnabled = auto.isEnabled
                        )
                    )
                }
                importedAutomations++
            }

            Result.success(Pair(importedTags, importedAutomations))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
