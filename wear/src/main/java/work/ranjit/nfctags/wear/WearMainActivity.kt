package work.ranjit.nfctags.wear

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.*
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class WearMainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            WearTagRepository.init(this)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        setContent {
            MaterialTheme(
                colors = Colors(
                    primary = Color(0xFF00B4D8),
                    primaryVariant = Color(0xFF0077B6),
                    secondary = Color(0xFF48CAE4),
                    background = Color.Black,
                    surface = Color(0xFF1E1E1E),
                    onPrimary = Color.White,
                    onSecondary = Color.Black,
                    onBackground = Color.White,
                    onSurface = Color.White
                )
            ) {
                WearNfcApp()
            }
        }
    }
}

@Composable
fun WearNfcApp() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val tags by WearTagRepository.tags.collectAsState()
    val activeTag by WearTagRepository.activeTag.collectAsState()
    val listState = rememberScalingLazyListState()

    fun triggerAutomationOnPhone(tag: SyncedTag) {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val nodeClient = Wearable.getNodeClient(context)
                val nodesList = nodeClient.connectedNodes.await()
                if (nodesList.isEmpty()) {
                    coroutineScope.launch(Dispatchers.Main) {
                        Toast.makeText(context, "Phone not connected", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }
                val messageClient = Wearable.getMessageClient(context)
                for (i in 0 until nodesList.size) {
                    val node = nodesList[i]
                    messageClient.sendMessage(node.id, "/trigger_automation", tag.tagId.toByteArray()).await()
                }
                coroutineScope.launch(Dispatchers.Main) {
                    Toast.makeText(context, "Triggered: ${tag.name}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                coroutineScope.launch(Dispatchers.Main) {
                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Scaffold(
        timeText = { TimeText() },
        vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) }
    ) {
        ScalingLazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            state = listState,
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = PaddingValues(top = 28.dp, bottom = 28.dp, start = 8.dp, end = 8.dp)
        ) {
            item {
                Text(
                    text = "NFC Emulation",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colors.primary,
                    textAlign = TextAlign.Center
                )
            }

            item {
                Card(
                    onClick = {},
                    enabled = false,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "🟢 Active for Tap",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF4CAF50)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = activeTag?.name ?: "No tag selected",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            if (tags.isEmpty()) {
                item {
                    Text(
                        text = "Open 'NFC Reader' on your phone and tap 'Sync to Watch' in My Tags.",
                        fontSize = 11.sp,
                        color = Color.Gray,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            } else {
                item {
                    Text(
                        text = "Select Tag to Emulate:",
                        fontSize = 12.sp,
                        color = Color.LightGray,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )
                }

                items(tags) { tag ->
                    val isSelected = tag.tagId == activeTag?.tagId
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Chip(
                            onClick = {
                                WearTagRepository.setActiveTag(context, tag)
                                Toast.makeText(context, "Emulating: ${tag.name}", Toast.LENGTH_SHORT).show()
                            },
                            label = {
                                Text(
                                    text = if (isSelected) "🟢  ${tag.name}" else tag.name,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 13.sp
                                )
                            },
                            colors = if (isSelected) {
                                ChipDefaults.primaryChipColors(
                                    backgroundColor = Color(0xFF1B3B4B),
                                    contentColor = Color.White
                                )
                            } else {
                                ChipDefaults.secondaryChipColors()
                            },
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (tag.automationUrl != null || tag.actionType != null) {
                            Spacer(modifier = Modifier.height(2.dp))
                            val actionTitle = when (tag.actionType) {
                                "SEND_SMS" -> "⚡ Send SMS"
                                "OPEN_APP" -> "⚡ Launch App"
                                "OPEN_LINK" -> "⚡ Open Link"
                                "WEBHOOK" -> "⚡ Send Webhook"
                                else -> "⚡ Run Action"
                            }
                            CompactChip(
                                onClick = { triggerAutomationOnPhone(tag) },
                                label = { Text(actionTitle, fontSize = 11.sp) },
                                colors = ChipDefaults.secondaryChipColors(),
                                modifier = Modifier.padding(bottom = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
