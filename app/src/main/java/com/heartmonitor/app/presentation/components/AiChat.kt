package com.heartmonitor.app.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.heartmonitor.app.domain.model.ChatMessage
import com.heartmonitor.app.presentation.theme.*

@Composable
fun AiChatBox(
    messages: List<ChatMessage>,
    isLoading: Boolean,
    onSendMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    
    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }
    
    Column(modifier = modifier.fillMaxWidth()) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = "AI",
                tint = YellowCaution,
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = "AI Analysis",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
        
        Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
        
        // Messages List
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            items(messages) { message ->
                ChatBubble(message = message)
            }
            
            if (isLoading) {
                item {
                    LoadingBubble()
                }
            }
        }
        
        Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
        
        // Input Field
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                modifier = Modifier.weight(1f),
                placeholder = { 
                    Text(
                        "Ask about your heart health...",
                        style = MaterialTheme.typography.bodyMedium
                    ) 
                },
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = OrangePrimary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                ),
                singleLine = true
            )
            
            FilledIconButton(
                onClick = {
                    if (inputText.isNotBlank()) {
                        onSendMessage(inputText)
                        inputText = ""
                    }
                },
                enabled = inputText.isNotBlank() && !isLoading,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = OrangePrimary,
                    contentColor = Color.White
                )
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send"
                )
            }
        }
    }
}

@Composable
private fun ChatBubble(
    message: ChatMessage,
    modifier: Modifier = Modifier
) {
    val alignment = if (message.isFromUser) Alignment.End else Alignment.Start
    val backgroundColor = if (message.isFromUser) {
        OrangePrimary
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val textColor = if (message.isFromUser) {
        Color.White
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val shape = if (message.isFromUser) {
        RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp)
    } else {
        RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp)
    }
    
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(shape)
                .background(backgroundColor)
                .padding(12.dp)
        ) {
            Text(
                text = message.content,
                style = MaterialTheme.typography.bodyMedium,
                color = textColor
            )
        }
    }
}

@Composable
private fun LoadingBubble(
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        repeat(3) { index ->
            val delay = index * 100
            LoadingDot(delay = delay)
        }
    }
}

@Composable
private fun LoadingDot(delay: Int) {
    var visible by remember { mutableStateOf(true) }
    
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(delay.toLong())
            visible = !visible
            kotlinx.coroutines.delay(300)
        }
    }
    
    Box(
        modifier = Modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(
                if (visible) TextSecondary else TextSecondary.copy(alpha = 0.3f)
            )
    )
}

@Composable
fun AiAnalysisCard(
    heartRateStatus: String,
    detectedConditions: List<Pair<String, Int>>,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = "AI",
                    tint = YellowCaution,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "AI Analysis",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            
            // Heart Rate Status
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(12.dp)
            ) {
                Text(
                    text = "Heart rate is ${heartRateStatus}! BPM exceeds 100!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (heartRateStatus == "too fast") RedWarning else TextPrimary
                )
            }
            
            // Detected Conditions
            detectedConditions.forEach { (condition, probability) ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(12.dp)
                ) {
                    Row {
                        Text(
                            text = "$probability% of ",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = condition,
                            style = MaterialTheme.typography.bodyMedium,
                            color = RedWarning,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = " detected!",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}
