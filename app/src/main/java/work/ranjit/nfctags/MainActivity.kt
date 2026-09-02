package work.ranjit.nfctags

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanIntentResult
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.launch
import work.ranjit.nfctags.data.AppDatabase
import work.ranjit.nfctags.data.ScanHistoryEntity
import work.ranjit.nfctags.theme.NFCReaderWriterTheme
import work.ranjit.nfctags.ui.HistoryScreen
import work.ranjit.nfctags.ui.ScannerScreen
import work.ranjit.nfctags.ui.WebhookScreen

class MainActivity : ComponentActivity() {
    private lateinit var nfcManager: NfcManager
    private lateinit var tagEventManager: TagEventManager
    private lateinit var networkManager: NetworkManager
    private lateinit var database: AppDatabase

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
        database = AppDatabase.getDatabase(this)

        enableEdgeToEdge()
        setContent {
            NFCReaderWriterTheme {
                val navController = rememberNavController()
                val tagData by nfcManager.tagData.collectAsState()
                val statusMessage by nfcManager.statusMessage.collectAsState()
                
                // Webhook trigger logic
                LaunchedEffect(tagData.tagId) {
                    if (tagData.tagId.isNotEmpty()) {
                        val event = tagEventManager.getEvent(tagData.tagId)
                        var webhookRes = ""
                        
                        if (event != null) {
                            val dataToSend = if (tagData.payload.isNotEmpty() && tagData.payload != "Empty tag" && !tagData.payload.startsWith("Mifare Classic")) {
                                tagData.payload
                            } else {
                                tagData.tagId
                            }
                            
                            webhookRes = networkManager.sendNfcData(event.url, dataToSend, event.isPost)
                        }
                        
                        // Save to history
                        lifecycleScope.launch {
                            database.scanHistoryDao().insert(
                                ScanHistoryEntity(
                                    timestamp = System.currentTimeMillis(),
                                    tagId = tagData.tagId,
                                    payload = tagData.payload,
                                    webhookResult = webhookRes.ifEmpty { null }
                                )
                            )
                        }
                    }
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        NavigationBar {
                            val navBackStackEntry by navController.currentBackStackEntryAsState()
                            val currentRoute = navBackStackEntry?.destination?.route

                            NavigationBarItem(
                                icon = { Icon(Icons.Filled.Nfc, contentDescription = "Scanner") },
                                label = { Text("Scanner") },
                                selected = currentRoute == "scanner",
                                onClick = {
                                    navController.navigate("scanner") {
                                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            )
                            NavigationBarItem(
                                icon = { Icon(Icons.Filled.Build, contentDescription = "Automations") },
                                label = { Text("Automations") },
                                selected = currentRoute == "automations",
                                onClick = {
                                    navController.navigate("automations") {
                                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            )
                            NavigationBarItem(
                                icon = { Icon(Icons.Filled.List, contentDescription = "History") },
                                label = { Text("History") },
                                selected = currentRoute == "history",
                                onClick = {
                                    navController.navigate("history") {
                                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            )
                        }
                    }
                ) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = "scanner",
                        modifier = Modifier.padding(innerPadding)
                    ) {
                        composable("scanner") {
                            ScannerScreen(nfcManager, tagData, statusMessage)
                        }
                        composable("automations") {
                            WebhookScreen(tagData, tagEventManager, qrScanResult) {
                                launchQrScanner()
                            }
                        }
                        composable("history") {
                            HistoryScreen(database.scanHistoryDao())
                        }
                    }
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
