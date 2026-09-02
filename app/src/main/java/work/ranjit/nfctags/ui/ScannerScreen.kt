package work.ranjit.nfctags.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import work.ranjit.nfctags.NdefPayloadType
import work.ranjit.nfctags.NfcManager
import work.ranjit.nfctags.NfcTagData

@Composable
fun ScannerScreen(
    nfcManager: NfcManager,
    tagData: NfcTagData,
    statusMessage: String
) {
    val context = LocalContext.current
    var isWriteExpanded by remember { mutableStateOf(false) }
    var textToWrite by remember { mutableStateOf("") }
    var isUri by remember { mutableStateOf(false) }
    
    // Pulsing animation for the NFC icon indicator
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "pulse_scale"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))
        
        // Pulsing Circle
        Box(
            modifier = Modifier
                .size(120.dp)
                .scale(if (tagData.tagId.isEmpty()) scale else 1f)
                .clip(CircleShape)
                .background(if (tagData.tagId.isEmpty()) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(if (tagData.tagId.isEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (tagData.tagId.isEmpty()) "NFC" else "✓",
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = statusMessage,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        if (tagData.tagId.isNotEmpty()) {
            // Smart Action Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Scanned Content",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = tagData.payload,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    
                    if (tagData.payloadType == NdefPayloadType.URI) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(tagData.payload))
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    // Handle invalid URI
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (tagData.payload.startsWith("tel:")) "Call Number" else "Open Link")
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Technical Details (Hidden by default or minimal)
            Text(
                text = "Tag ID: ${tagData.tagId}",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Write to Tag Expandable Section
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isWriteExpanded = !isWriteExpanded },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Write to Tag", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Icon(
                            imageVector = if (isWriteExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = "Expand"
                        )
                    }
                    
                    AnimatedVisibility(visible = isWriteExpanded) {
                        Column(modifier = Modifier.padding(top = 16.dp)) {
                            OutlinedTextField(
                                value = textToWrite,
                                onValueChange = { textToWrite = it },
                                label = { Text("Content to write") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(vertical = 8.dp)
                            ) {
                                Checkbox(
                                    checked = isUri,
                                    onCheckedChange = { isUri = it }
                                )
                                Text("Format as Web Link (URI)")
                            }
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { nfcManager.writeNdefMessage(textToWrite, isUri) },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Write")
                                }
                                OutlinedButton(
                                    onClick = { nfcManager.writeNdefMessage("", false) },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Erase")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
