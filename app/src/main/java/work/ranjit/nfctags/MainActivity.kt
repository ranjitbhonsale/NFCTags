package work.ranjit.nfctags

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanIntentResult
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.launch
import work.ranjit.nfctags.theme.NFCReaderWriterTheme

class MainActivity : ComponentActivity() {
    private lateinit var nfcManager: NfcManager
    private lateinit var tagEventManager: TagEventManager
    private lateinit var networkManager: NetworkManager

    private var qrScanResult by mutableStateOf("")

    private val barcodeLauncher = registerForActivityResult(ScanContract()) { result: ScanIntentResult ->
        if (result.contents != null) {
            qrScanResult = result.contents
        }
    }

    fun launchQrScanner() {
        val options = ScanOptions()
        options.setDesiredBarcodeFormats(ScanOptions.QR_CODE)
        options.setPrompt("Scan a QR Code")
        options.setBeepEnabled(false)
        barcodeLauncher.launch(options)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        nfcManager = NfcManager(this)
        tagEventManager = TagEventManager(this)
        networkManager = NetworkManager()

        enableEdgeToEdge()
        setContent {
            NFCReaderWriterTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.White
                ) {
                    NfcAppContent(this, nfcManager, tagEventManager, networkManager, qrScanResult)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        nfcManager.enableReaderMode()
    }

    override fun onPause() {
        super.onPause()
        nfcManager.disableReaderMode()
    }
}

@Composable
fun NfcAppContent(
    activity: MainActivity,
    nfcManager: NfcManager,
    tagEventManager: TagEventManager,
    networkManager: NetworkManager,
    scannedQrUrl: String
) {
    val statusMessage by nfcManager.statusMessage.collectAsState()
    val tagData by nfcManager.tagData.collectAsState()

    var textToWrite by remember { mutableStateOf("") }
    var passwordHex by remember { mutableStateOf("") }

    // Event assignment state
    var eventUrl by remember(scannedQrUrl) { mutableStateOf(scannedQrUrl) }
    var isPost by remember { mutableStateOf(false) }
    
    // Server response state
    var serverResponse by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    // Listen to tag changes and execute event if assigned
    LaunchedEffect(tagData.tagId) {
        if (tagData.tagId.isNotEmpty()) {
            val event = tagEventManager.getEvent(tagData.tagId)
            if (event != null) {
                serverResponse = "Sending request to ${event.url}..."
                val dataToSend = if (tagData.payload.isNotEmpty() && tagData.payload != "No NDEF content" && !tagData.payload.startsWith("Mifare Classic")) {
                    tagData.payload
                } else {
                    tagData.tagId
                }
                
                val result = networkManager.sendNfcData(event.url, dataToSend, event.isPost)
                serverResponse = result
            } else {
                serverResponse = "No event assigned to this tag."
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .padding(top = 32.dp, bottom = 32.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "NFC Reader & Writer",
            style = MaterialTheme.typography.headlineMedium,
            color = Color.Black
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(text = "Status: $statusMessage", color = Color.Gray)
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Tag Info Section
        Text(text = "Tag Information", style = MaterialTheme.typography.titleMedium, color = Color.Black)
        Divider(color = Color.LightGray, modifier = Modifier.padding(vertical = 8.dp))
        
        Text(text = "Tag ID: ${tagData.tagId.ifEmpty { "None" }}", color = Color.Black)
        Text(text = "Type: ${tagData.type}", color = Color.Black)
        Text(text = "Writable: ${if (tagData.isWritable) "Yes" else "No"}", color = Color.Black)
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(text = "Tag Content", style = MaterialTheme.typography.titleMedium, color = Color.Black)
        Divider(color = Color.LightGray, modifier = Modifier.padding(vertical = 8.dp))
        Text(text = tagData.payload, color = Color.Black)
        
        Spacer(modifier = Modifier.height(32.dp))

        // Webhook Assignment Section
        Text(text = "Assign Webhook Event", style = MaterialTheme.typography.titleMedium, color = Color.Black)
        Divider(color = Color.LightGray, modifier = Modifier.padding(vertical = 8.dp))
        
        Button(
            onClick = { activity.launchQrScanner() },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color.Blue)
        ) {
            Text("Scan QR Code for URL", color = Color.White)
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        OutlinedTextField(
            value = eventUrl,
            onValueChange = { eventUrl = it },
            label = { Text("Webhook URL") },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color.Black,
                unfocusedBorderColor = Color.Gray,
                focusedLabelColor = Color.Black,
            )
        )
        
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text("GET", color = Color.Black)
            Switch(
                checked = isPost,
                onCheckedChange = { isPost = it },
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            Text("POST", color = Color.Black)
        }
        
        Button(
            onClick = { 
                if (tagData.tagId.isNotEmpty() && eventUrl.isNotEmpty()) {
                    tagEventManager.saveEvent(tagData.tagId, eventUrl, isPost)
                    serverResponse = "Event saved for Tag ID: ${tagData.tagId}"
                } else {
                    serverResponse = "Please scan a tag and enter a URL first."
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color.Gray)
        ) {
            Text("Save Event for Tag", color = Color.White)
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(text = "Server Response:", style = MaterialTheme.typography.titleSmall, color = Color.Black)
        OutlinedTextField(
            value = serverResponse,
            onValueChange = {},
            readOnly = true,
            modifier = Modifier.fillMaxWidth().height(100.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = Color.LightGray,
                focusedBorderColor = Color.LightGray
            )
        )

        Spacer(modifier = Modifier.height(32.dp))
        
        // Write Section
        Text(text = "Write to Tag", style = MaterialTheme.typography.titleMedium, color = Color.Black)
        Divider(color = Color.LightGray, modifier = Modifier.padding(vertical = 8.dp))
        
        OutlinedTextField(
            value = textToWrite,
            onValueChange = { textToWrite = it },
            label = { Text("Text payload to write") },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color.Black,
                unfocusedBorderColor = Color.Gray,
                focusedLabelColor = Color.Black,
            )
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        OutlinedTextField(
            value = passwordHex,
            onValueChange = { passwordHex = it },
            label = { Text("Password / Key (Hex)") },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color.Black,
                unfocusedBorderColor = Color.Gray,
                focusedLabelColor = Color.Black,
            )
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Button(
            onClick = { nfcManager.writeNdefMessage(textToWrite, passwordHex) },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
        ) {
            Text("Write to Tag", color = Color.White)
        }
    }
}
