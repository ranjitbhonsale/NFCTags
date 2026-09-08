package work.ranjit.nfctags.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import work.ranjit.nfctags.NfcTagData
import work.ranjit.nfctags.TagEventManager

@Composable
fun WebhookScreen(
    tagData: NfcTagData,
    tagEventManager: TagEventManager,
    scannedQrUrl: String,
    onLaunchQrScanner: () -> Unit
) {
    var eventUrl by remember(scannedQrUrl) { mutableStateOf(scannedQrUrl) }
    var isPost by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "Automations",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Link a Webhook URL to a specific NFC Tag. When you scan that tag, the app will automatically send its data to your URL.",
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
            label = { Text("Webhook URL") },
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
        
        var actionType by remember { mutableStateOf(work.ranjit.nfctags.ActionType.WEBHOOK) }

        Text("Action Type", fontWeight = FontWeight.Bold)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            RadioButton(
                selected = actionType == work.ranjit.nfctags.ActionType.WEBHOOK,
                onClick = { actionType = work.ranjit.nfctags.ActionType.WEBHOOK }
            )
            Text("Send Webhook (Background)")
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            RadioButton(
                selected = actionType == work.ranjit.nfctags.ActionType.OPEN_LINK,
                onClick = { actionType = work.ranjit.nfctags.ActionType.OPEN_LINK }
            )
            Text("Open Link / Deep Link (Browser)")
        }

        if (actionType == work.ranjit.nfctags.ActionType.WEBHOOK) {
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
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
                    tagEventManager.saveEvent(tagData.tagId, eventUrl, isPost, actionType)
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
    }
}
