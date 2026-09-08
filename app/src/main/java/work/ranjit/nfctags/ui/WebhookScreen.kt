package work.ranjit.nfctags.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Send
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

@Composable
fun WebhookScreen(
    tagData: NfcTagData,
    automationDao: AutomationDao,
    scannedQrUrl: String,
    onLaunchQrScanner: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val automations by automationDao.getAllAutomations().collectAsState(initial = emptyList())

    var eventUrl by remember(scannedQrUrl) { mutableStateOf(scannedQrUrl) }
    var isPost by remember { mutableStateOf(false) }
    var actionType by remember { mutableStateOf(ActionType.WEBHOOK) }
    var message by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
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
                text = "Link a Webhook or URL to a specific NFC Tag. Use {{tag_id}} or {{timestamp}} as dynamic variables.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Currently Scanned Tag", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (tagData.tagId.isNotEmpty()) tagData.tagId else "No tag scanned yet. Tap a tag to begin.",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
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
            
            Button(
                onClick = { 
                    if (tagData.tagId.isNotEmpty() && eventUrl.isNotEmpty()) {
                        coroutineScope.launch(Dispatchers.IO) {
                            // Check if one already exists and delete it first (since we only support 1 per tag currently)
                            val existing = automationDao.getAutomationByTagId(tagData.tagId)
                            if (existing != null) {
                                automationDao.deleteAutomation(existing)
                            }
                            automationDao.insertAutomation(
                                AutomationEntity(
                                    tagId = tagData.tagId,
                                    actionType = actionType,
                                    url = eventUrl,
                                    isPost = isPost,
                                    isEnabled = true
                                )
                            )
                        }
                        message = "Successfully linked URL to Tag: ${tagData.tagId}"
                    } else {
                        message = "Please scan a tag and enter a URL first."
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save Automation")
            }
            
            if (message.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = message,
                    color = if (message.startsWith("Success")) Color(0xFF388E3C) else MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))
            Text("Active Automations", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            
            if (automations.isEmpty()) {
                Text("No automations created yet.", color = Color.Gray)
            }
        }
        
        items(automations) { automation ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
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
                        Text(text = "Tag: ${automation.tagId}", fontWeight = FontWeight.Bold)
                        Text(text = automation.url, style = MaterialTheme.typography.bodySmall, color = Color.Gray, maxLines = 2)
                        val typeStr = if (automation.actionType == ActionType.WEBHOOK) "Webhook (${if(automation.isPost) "POST" else "GET"})" else "Open Link"
                        Text(text = typeStr, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                    }
                    IconButton(onClick = {
                        coroutineScope.launch(Dispatchers.IO) {
                            automationDao.deleteAutomation(automation)
                        }
                    }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}
