package com.twyn.app.ui.chat

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.twyn.app.domain.model.ChatMessage
import com.twyn.app.domain.model.ContentType
import com.twyn.app.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * Individual 1-on-1 chat screen.
 *
 * Features:
 * - End-to-end encrypted messaging (all content is ciphertext on server)
 * - Smooth message bubble slide-in animations
 * - Typing indicators
 * - Read receipts
 * - Voice message recording
 * - Photo/video/file sharing
 * - Location sharing
 * - Voice/video call buttons
 * - Media library access
 *
 * Each chat is completely independent — this is a private channel
 * between exactly two people, encrypted with unique keys.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    pairingId: String,
    onOpenMedia: () -> Unit,
    onOpenLocation: () -> Unit,
    onOpenCall: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: ChatViewModel = hiltViewModel()
) {
    val messages by viewModel.getMessages(pairingId).collectAsState()
    val isTyping by viewModel.isTyping.collectAsState()
    val partnerName by viewModel.partnerName.collectAsState()
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    // Mark messages as read when screen is visible
    LaunchedEffect(pairingId) {
        viewModel.markAsRead(pairingId)
        viewModel.loadPartnerName(pairingId)
    }

    Scaffold(
        topBar = {
            ChatTopBar(
                partnerName = partnerName,
                isTyping = isTyping,
                onOpenMedia = onOpenMedia,
                onOpenCall = onOpenCall,
                onBack = onBack
            )
        },
        bottomBar = {
            ChatInputBar(
                inputText = inputText,
                onInputChange = { inputText = it },
                onSendMessage = {
                    viewModel.sendMessage(pairingId, inputText, "local_user")
                    inputText = ""
                },
                onOpenLocation = onOpenLocation
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 8.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(
                items = messages,
                key = { it.messageId }
            ) { message ->
                // Each message bubble slides in from its side with a fade
                AnimatedMessageBubble(message = message)
            }
        }
    }
}

/**
 * Top bar with partner name, online status, and action buttons.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatTopBar(
    partnerName: String,
    isTyping: Boolean,
    onOpenMedia: () -> Unit,
    onOpenCall: (String) -> Unit,
    onBack: () -> Unit
) {
    TopAppBar(
        title = {
            Column {
                Text(
                    text = partnerName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                if (isTyping) {
                    val dots = remember { listOf(".", "..", "...") }
                    var dotIndex by remember { mutableIntStateOf(0) }
                    LaunchedEffect(Unit) {
                        while (true) {
                            dotIndex = (dotIndex + 1) % 3
                            kotlinx.coroutines.delay(500)
                        }
                    }
                    Text(
                        text = "typing${dots[dotIndex]}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Primary
                    )
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
        },
        actions = {
            // Voice call button
            IconButton(onClick = { onOpenCall("voice") }) {
                Icon(Icons.Default.Phone, contentDescription = "Voice Call")
            }
            // Video call button
            IconButton(onClick = { onOpenCall("video") }) {
                Icon(Icons.Default.Videocam, contentDescription = "Video Call")
            }
            // Media library button
            IconButton(onClick = onOpenMedia) {
                Icon(Icons.Default.Collections, contentDescription = "Media Library")
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background
        )
    )
}

/**
 * Message input bar with text field, send button, and attachment options.
 */
@Composable
private fun ChatInputBar(
    inputText: String,
    onInputChange: (String) -> Unit,
    onSendMessage: () -> Unit,
    onOpenLocation: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Attachment menu button
            IconButton(onClick = onOpenLocation) {
                Icon(
                    Icons.Default.LocationOn,
                    contentDescription = "Share Location",
                    tint = Primary
                )
            }

            // Text input
            OutlinedTextField(
                value = inputText,
                onValueChange = onInputChange,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp),
                placeholder = { Text("Message...") },
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = SurfaceLight,
                    focusedBorderColor = Primary
                ),
                maxLines = 4
            )

            Spacer(modifier = Modifier.width(8.dp))

            // Send button (animated)
            AnimatedVisibility(
                visible = inputText.isNotBlank(),
                enter = scaleIn(animationSpec = tween(200)) + fadeIn(),
                exit = scaleOut(animationSpec = tween(200)) + fadeOut()
            ) {
                FilledIconButton(
                    onClick = onSendMessage,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = Primary
                    )
                ) {
                    Icon(
                        Icons.Default.Send,
                        contentDescription = "Send",
                        tint = OnPrimary
                    )
                }
            }

            // Voice message button (when no text)
            AnimatedVisibility(
                visible = inputText.isBlank(),
                enter = scaleIn(animationSpec = tween(200)) + fadeIn(),
                exit = scaleOut(animationSpec = tween(200)) + fadeOut()
            ) {
                FilledIconButton(
                    onClick = { /* Start voice recording */ },
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = Secondary
                    )
                ) {
                    Icon(
                        Icons.Default.Mic,
                        contentDescription = "Record Voice Message",
                        tint = OnPrimary
                    )
                }
            }
        }
    }
}

/**
 * Individual message bubble with slide-in animation.
 *
 * Sent messages (from me) slide in from the right.
 * Received messages slide in from the left.
 * Each bubble fades in and slides smoothly.
 */
@Composable
private fun AnimatedMessageBubble(message: ChatMessage) {
    // Entrance animation — each new message slides in from its side
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(message.messageId) { appeared = true }

    val offsetX by animateFloatAsState(
        targetValue = if (appeared) 0f else if (message.isFromMe) 200f else -200f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "slideIn"
    )
    val alpha by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = tween(300),
        label = "fadeIn"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                translationX = offsetX
                this.alpha = alpha
            }
            .padding(vertical = 2.dp),
        contentAlignment = if (message.isFromMe) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        MessageBubble(message = message)
    }
}

/**
 * Message bubble UI — the actual message content.
 * Different styles for sent vs received, different content types.
 */
@Composable
private fun MessageBubble(message: ChatMessage) {
    val bubbleColor = if (message.isFromMe) BubbleSent else BubbleReceived
    val textColor = if (message.isFromMe) BubbleSentText else BubbleReceivedText
    val shape = if (message.isFromMe) {
        RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp)  // Right bubble
    } else {
        RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp)  // Left bubble
    }

    Surface(
        color = bubbleColor,
        shape = shape,
        modifier = Modifier.widthIn(max = 300.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            // Content based on type
            when (message.contentType) {
                ContentType.TEXT -> {
                    Text(
                        text = message.decryptedText ?: message.ciphertext,
                        color = textColor,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                ContentType.VOICE -> {
                    // Voice message placeholder
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = textColor
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        // Waveform visualization placeholder
                        Text(
                            text = "Voice message",
                            color = textColor,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                ContentType.PHOTO -> {
                    // Photo message — shows thumbnail
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(SurfaceLight),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Image,
                            contentDescription = null,
                            tint = OnSurfaceVariant,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                }
                ContentType.VIDEO -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(SurfaceLight),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.PlayCircle,
                            contentDescription = null,
                            tint = OnSurfaceVariant,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                }
                ContentType.FILE -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.InsertDriveFile,
                            contentDescription = null,
                            tint = textColor
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = message.mediaUrl ?: "File",
                            color = textColor,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                ContentType.LOCATION_REQUEST -> {
                    Text(
                        text = "📍 Location requested",
                        color = textColor,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                ContentType.LOCATION_RESPONSE -> {
                    Text(
                        text = "📍 Location shared",
                        color = textColor,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Timestamp and delivery status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = if (message.isFromMe) Arrangement.End else Arrangement.Start
            ) {
                Text(
                    text = formatMessageTime(message.timestamp),
                    color = textColor.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.labelSmall
                )
                if (message.isFromMe) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        if (message.isRead) Icons.Default.DoneAll
                        else if (message.isDelivered) Icons.Default.DoneAll
                        else Icons.Default.Done,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = if (message.isRead) PrimaryLight else textColor.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}

private fun formatMessageTime(timestamp: Long): String {
    return SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(timestamp))
}
