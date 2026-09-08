package work.ranjit.nfctags.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import work.ranjit.nfctags.NfcTagData
import work.ranjit.nfctags.data.NfcTagEntity
import work.ranjit.nfctags.data.TagDao
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TagInventoryScreen(
    tagData: NfcTagData,
    tagDao: TagDao
) {
    val coroutineScope = rememberCoroutineScope()
    val tags by tagDao.getAllTags().collectAsState(initial = emptyList())
    
    var showAddDialog by remember { mutableStateOf(false) }
    var newTagName by remember { mutableStateOf("") }
    
    // Auto-prompt to add tag if a new tag is scanned that isn't in DB
    LaunchedEffect(tagData.tagId) {
        if (tagData.tagId.isNotEmpty()) {
            kotlinx.coroutines.withContext(Dispatchers.IO) {
                val existing = tagDao.getTagById(tagData.tagId)
                if (existing == null) {
                    showAddDialog = true
                    newTagName = "Tag ${tagData.tagId.take(4)}"
                }
            }
        }
    }

    if (showAddDialog && tagData.tagId.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Save New Tag") },
            text = {
                Column {
                    Text("Tag ID: ${tagData.tagId}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newTagName,
                        onValueChange = { newTagName = it },
                        label = { Text("Tag Name (e.g., Front Door)") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    coroutineScope.launch(Dispatchers.IO) {
                        tagDao.insertTag(
                            NfcTagEntity(
                                tagId = tagData.tagId,
                                name = newTagName,
                                dateAdded = System.currentTimeMillis()
                            )
                        )
                    }
                    showAddDialog = false
                }) {
                    Text("Save Tag")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Skip")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "My Tags",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Manage your physical NFC tags.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Gray
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        if (tags.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Nfc, contentDescription = null, modifier = Modifier.size(64.dp), tint = Color.LightGray)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("No tags saved.", color = Color.Gray)
                    Text("Scan a physical tag to add it to your inventory.", color = Color.Gray)
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(tags) { tag ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Nfc, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(tag.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                Text("ID: ${tag.tagId}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                                Text("Added: ${sdf.format(Date(tag.dateAdded))}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                            }
                            IconButton(onClick = {
                                coroutineScope.launch(Dispatchers.IO) {
                                    tagDao.deleteTag(tag)
                                }
                            }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }
}
