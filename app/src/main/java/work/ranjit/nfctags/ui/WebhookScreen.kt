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
import androidx.compose.material.icons.filled.Phone
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
import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.ResolveInfo
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import work.ranjit.nfctags.NfcTagData
import work.ranjit.nfctags.SmsSender
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
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val automations by automationDao.getAllAutomations().collectAsState(initial = emptyList())
    val tags by tagDao.getAllTags().collectAsState(initial = emptyList())

    var hasSmsPermission by remember { mutableStateOf(SmsSender.hasSmsPermission(context)) }
    val smsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasSmsPermission = isGranted
    }

    var showEditor by remember { mutableStateOf(false) }
    var editingAutomation by remember { mutableStateOf<AutomationEntity?>(null) }
    
    // Editor State
    var selectedTagId by remember { mutableStateOf("") }
    var selectedAppPackage by remember { mutableStateOf("") }
    var eventUrl by remember(scannedQrUrl) { mutableStateOf(scannedQrUrl) }
    var isPost by remember { mutableStateOf(false) }
    var actionType by remember { mutableStateOf(ActionType.WEBHOOK) }
    var smsPhoneNumbers by remember { mutableStateOf("") }
    var smsMessage by remember { mutableStateOf("") }
    
    var tagDropdownExpanded by remember { mutableStateOf(false) }

    fun openEditor(automation: AutomationEntity? = null) {
        editingAutomation = automation
        hasSmsPermission = SmsSender.hasSmsPermission(context)
        if (automation != null) {
            selectedTagId = automation.tagId
            eventUrl = automation.url
            isPost = automation.isPost
            actionType = automation.actionType
            selectedAppPackage = automation.appPackage ?: ""
            smsPhoneNumbers = automation.smsPhoneNumbers ?: ""
            smsMessage = automation.smsMessage ?: ""
        } else {
            // Auto-select currently scanned tag if it exists in DB
            selectedTagId = if (tagData.tagId.isNotEmpty() && tags.any { it.tagId == tagData.tagId }) tagData.tagId else ""
            eventUrl = scannedQrUrl
            isPost = false
            actionType = ActionType.WEBHOOK
            selectedAppPackage = ""
            smsPhoneNumbers = ""
            smsMessage = ""
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
                        val itemIcon = when (automation.actionType) {
                            ActionType.WEBHOOK -> Icons.Default.Send
                            ActionType.OPEN_LINK -> Icons.Default.Link
                            ActionType.OPEN_APP -> Icons.Default.Send
                            ActionType.SEND_SMS -> Icons.Default.Phone
                        }
                        Icon(
                            imageVector = itemIcon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            val tagName = tags.find { it.tagId == automation.tagId }?.name ?: "Unknown Tag"
                            Text(text = "$tagName (${automation.tagId})", fontWeight = FontWeight.Bold)
                            val subtitle = when (automation.actionType) {
                                ActionType.SEND_SMS -> "To: ${automation.smsPhoneNumbers ?: "No number"} | \"${automation.smsMessage ?: ""}\""
                                ActionType.OPEN_APP -> "Launch: ${automation.appPackage ?: "No app"}"
                                else -> automation.url
                            }
                            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = Color.Gray, maxLines = 2)
                            val typeStr = when (automation.actionType) {
                                ActionType.WEBHOOK -> "Webhook (${if(automation.isPost) "POST" else "GET"})"
                                ActionType.OPEN_LINK -> "Open Link"
                                ActionType.OPEN_APP -> "Open App"
                                ActionType.SEND_SMS -> "Send Direct SMS"
                            }
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
                
                val contactPickerLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartActivityForResult()
                ) { result ->
                    if (result.resultCode == Activity.RESULT_OK) {
                        val contactUri = result.data?.data
                        if (contactUri != null) {
                            try {
                                val cursor = context.contentResolver.query(
                                    contactUri,
                                    arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                                    null, null, null
                                )
                                cursor?.use { c ->
                                    if (c.moveToFirst()) {
                                        val numberIndex = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                                        if (numberIndex != -1) {
                                            val rawNum = c.getString(numberIndex) ?: ""
                                            val cleanNum = rawNum.replace("[^0-9+]".toRegex(), "")
                                            if (cleanNum.isNotEmpty()) {
                                                smsPhoneNumbers = if (smsPhoneNumbers.isBlank()) {
                                                    cleanNum
                                                } else {
                                                    "$smsPhoneNumbers, $cleanNum"
                                                }
                                            }
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = actionType == ActionType.OPEN_APP,
                        onClick = { actionType = ActionType.OPEN_APP }
                    )
                    Text("Open App (Launch)")
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = actionType == ActionType.SEND_SMS,
                        onClick = { actionType = ActionType.SEND_SMS }
                    )
                    Text("Send Direct SMS (Background)")
                }

                if (actionType == ActionType.WEBHOOK || actionType == ActionType.OPEN_LINK) {
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

                if (actionType == ActionType.OPEN_APP) {
                    Spacer(modifier = Modifier.height(16.dp))
                    val appsContext = LocalContext.current
                    var apps by remember { mutableStateOf(listOf<ResolveInfo>()) }
                    LaunchedEffect(Unit) {
                        val intent = Intent(Intent.ACTION_MAIN)
                        intent.addCategory(Intent.CATEGORY_LAUNCHER)
                        apps = appsContext.packageManager.queryIntentActivities(intent, 0)
                    }
                    var appDropdownExpanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = appDropdownExpanded,
                        onExpandedChange = { appDropdownExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = selectedAppPackage.ifEmpty { "Select app" },
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("App to launch") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = appDropdownExpanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = appDropdownExpanded,
                            onDismissRequest = { appDropdownExpanded = false }
                        ) {
                            apps.forEach { info ->
                                val pkg = info.activityInfo.packageName
                                val label = info.loadLabel(appsContext.packageManager).toString()
                                DropdownMenuItem(
                                    text = { Text("$label ($pkg)") },
                                    onClick = {
                                        selectedAppPackage = pkg
                                        appDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                if (actionType == ActionType.SEND_SMS) {
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = smsPhoneNumbers,
                        onValueChange = { smsPhoneNumbers = it },
                        label = { Text("Preset Phone Number(s)") },
                        placeholder = { Text("e.g. +1234567890, +1987654321") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        OutlinedButton(
                            onClick = {
                                val pickIntent = Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
                                contactPickerLauncher.launch(pickIntent)
                            }
                        ) {
                            Text("👤 Pick from Contacts")
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = smsMessage,
                        onValueChange = { smsMessage = it },
                        label = { Text("Preset SMS Message Body") },
                        placeholder = { Text("e.g. Scanned tag {{tag_id}} at {{timestamp}}") },
                        minLines = 3,
                        maxLines = 5,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "Dynamic tags available: {{tag_id}}, {{timestamp}}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray,
                        modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                    )

                    if (!hasSmsPermission) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "⚠️ Send SMS permission required to send in the background without opening an external app.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = { smsPermissionLauncher.launch(Manifest.permission.SEND_SMS) },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                                ) {
                                    Text("Grant SMS Permission", color = Color.White)
                                }
                            }
                        }
                    }
                }

                val saveEnabled = when (actionType) {
                    ActionType.OPEN_APP -> selectedTagId.isNotEmpty() && selectedAppPackage.isNotEmpty()
                    ActionType.SEND_SMS -> selectedTagId.isNotEmpty() && smsPhoneNumbers.isNotBlank() && smsMessage.isNotBlank()
                    else -> selectedTagId.isNotEmpty() && eventUrl.isNotEmpty()
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
                            if (selectedTagId.isNotEmpty()) {
                                coroutineScope.launch(Dispatchers.IO) {
                                    val newAutomation = AutomationEntity(
                                        id = editingAutomation?.id ?: 0,
                                        tagId = selectedTagId,
                                        actionType = actionType,
                                        appPackage = if (actionType == ActionType.OPEN_APP) selectedAppPackage else null,
                                        url = if (actionType == ActionType.WEBHOOK || actionType == ActionType.OPEN_LINK) eventUrl else "",
                                        isPost = isPost,
                                        isEnabled = true,
                                        smsPhoneNumbers = if (actionType == ActionType.SEND_SMS) smsPhoneNumbers.trim() else null,
                                        smsMessage = if (actionType == ActionType.SEND_SMS) smsMessage.trim() else null
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
                        enabled = saveEnabled
                    ) {
                        Text("Save")
                    }
                }
            }
        }
    }
}
