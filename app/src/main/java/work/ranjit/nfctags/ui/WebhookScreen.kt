package work.ranjit.nfctags.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import work.ranjit.nfctags.data.ActionType
import work.ranjit.nfctags.NfcTagData
import work.ranjit.nfctags.data.AutomationDao
import work.ranjit.nfctags.data.AutomationEntity
import work.ranjit.nfctags.data.TagDao

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebhookScreen(
    tagData: NfcTagData,
    automationDao: AutomationDao,
    tagDao: TagDao,
    scannedQrUrl: String,
    onLaunchQrScanner: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val automations by automationDao.getAllAutomations().collectAsState(initial = emptyList())
    val tags by tagDao.getAllTags().collectAsState(initial = emptyList())

    var showEditor by remember { mutableStateOf(false) }
    var editingAutomation by remember { mutableStateOf<AutomationEntity?>(null) }
    
    // Editor State
    var selectedTagId by remember { mutableStateOf("") }
    var eventUrl by remember(scannedQrUrl) { mutableStateOf(scannedQrUrl) }
    var isPost by remember { mutableStateOf(false) }
    var actionType by remember { mutableStateOf(ActionType.WEBHOOK) }
    
    var tagDropdownExpanded by remember { mutableStateOf(false) }

    fun openEditor(automation: AutomationEntity? = null) {
        editingAutomation = automation
        if (automation != null) {
            selectedTagId = automation.tagId
            eventUrl = automation.url
            isPost = automation.isPost
            actionType = automation.actionType
        } else {
            // Auto-select currently scanned tag if it exists in DB
            selectedTagId = if (tagData.tagId.isNotEmpty() && tags.any { it.tagId == tagData.tagId }) tagData.tagId else ""
            eventUrl = scannedQrUrl
            isPost = false
            actionType = ActionType.WEBHOOK
        }
        showEditor = true
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { openEditor(null) }) {
                Icon(Icons.Default.Add, contentDescription = "Add Automation")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            item {
                Text(
                    text = "Automations",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Configure actions to run when you scan your physical NFC tags.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                if (automations.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("No automations created yet. Tap the + button to create one.", color = Color.Gray)
                    }
                }
            }
            
            items(automations) { automation ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable { openEditor(automation) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (automation.actionType == ActionType.WEBHOOK) Icons.Default.Send else Icons.Default.Link,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            val tagName = tags.find { it.tagId == automation.tagId }?.name ?: "Unknown Tag"
                            Text(text = "$tagName (${automation.tagId})", fontWeight = FontWeight.Bold)
                            Text(text = automation.url, style = MaterialTheme.typography.bodySmall, color = Color.Gray, maxLines = 2)
                            val typeStr = if (automation.actionType == ActionType.WEBHOOK) "Webhook (${if(automation.isPost) "POST" else "GET"})" else "Open Link"
                            Text(text = typeStr, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                        }
                        IconButton(onClick = { openEditor(automation) }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit")
                        }
                    }
                }
            }
        }
    }

    if (showEditor) {
        ModalBottomSheet(onDismissRequest = { showEditor = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .padding(bottom = 32.dp)
            ) {
                Text(
                    text = if (editingAutomation == null) "New Automation" else "Edit Automation",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Tag Selector
                ExposedDropdownMenuBox(
                    expanded = tagDropdownExpanded,
                    onExpandedChange = { tagDropdownExpanded = it }
                ) {
                    OutlinedTextField(
                        value = if (selectedTagId.isEmpty()) "Select a tag" else (tags.find { it.tagId == selectedTagId }?.name ?: selectedTagId),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Select Tag") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = tagDropdownExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = tagDropdownExpanded,
                        onDismissRequest = { tagDropdownExpanded = false }
                    ) {
                        // Filter tags that don't already have automations (unless we are editing)
                        val availableTags = tags.filter { tag -> 
                            editingAutomation?.tagId == tag.tagId || !automations.any { it.tagId == tag.tagId }
                        }
                        
                        if (availableTags.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text("No available tags in inventory. Add one in 'My Tags' first.") },
                                onClick = { tagDropdownExpanded = false }
                            )
                        } else {
                            availableTags.forEach { tag ->
                                DropdownMenuItem(
                                    text = { Text("${tag.name} (${tag.tagId})") },
                                    onClick = {
                                        selectedTagId = tag.tagId
                                        tagDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                OutlinedTextField(
                    value = eventUrl,
                    onValueChange = { eventUrl = it },
                    label = { Text("Webhook URL or Link") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                OutlinedButton(
                    onClick = onLaunchQrScanner,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Scan QR Code for URL")
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text("Action Type", fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = actionType == ActionType.WEBHOOK,
                        onClick = { actionType = ActionType.WEBHOOK }
                    )
                    Text("Send Webhook (Background)")
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = actionType == ActionType.OPEN_LINK,
                        onClick = { actionType = ActionType.OPEN_LINK }
                    )
                    Text("Open Link / Deep Link (Browser)")
                }

                if (actionType == ActionType.WEBHOOK) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Webhook Method:", modifier = Modifier.weight(1f))
                        Text("GET", fontWeight = if (!isPost) FontWeight.Bold else FontWeight.Normal)
                        Switch(
                            checked = isPost,
                            onCheckedChange = { isPost = it },
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                        Text("POST", fontWeight = if (isPost) FontWeight.Bold else FontWeight.Normal)
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    if (editingAutomation != null) {
                        TextButton(
                            onClick = {
                                coroutineScope.launch(Dispatchers.IO) {
                                    automationDao.deleteAutomation(editingAutomation!!)
                                }
                                showEditor = false
                            },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Delete")
                        }
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                    
                    Button(
                        onClick = { 
                            if (selectedTagId.isNotEmpty() && eventUrl.isNotEmpty()) {
                                coroutineScope.launch(Dispatchers.IO) {
                                    val newAutomation = AutomationEntity(
                                        id = editingAutomation?.id ?: 0,
                                        tagId = selectedTagId,
                                        actionType = actionType,
                                        url = eventUrl,
                                        isPost = isPost,
                                        isEnabled = true
                                    )
                                    if (editingAutomation != null) {
                                        automationDao.updateAutomation(newAutomation)
                                    } else {
                                        automationDao.insertAutomation(newAutomation)
                                    }
                                }
                                showEditor = false
                            }
                        },
                        enabled = selectedTagId.isNotEmpty() && eventUrl.isNotEmpty()
                    ) {
                        Text("Save")
                    }
                }
            }
        }
    }
}
