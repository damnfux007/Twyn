package com.twyn.app.ui.chat

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.twyn.app.domain.model.ChatMessage
import com.twyn.app.domain.model.ContentType
import com.twyn.app.ui.theme.*
import com.twyn.app.util.PreferencesManager
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

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
    val context = LocalContext.current
    val chatThemeIndex by remember {
        val prefs = PreferencesManager(context)
        prefs.chatThemeIndexFlow
    }.collectAsState()

    val photoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val path = it.toString()
            viewModel.sendPhoto(pairingId, path)
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    LaunchedEffect(pairingId) {
        viewModel.markAsRead(pairingId)
        viewModel.loadPartnerName(pairingId)
    }

    Scaffold(
        topBar = {
            ChatTopBar(
                partnerName = partnerName,
                isTyping = isTyping,
                onOpenCall = onOpenCall,
                onBack = onBack
            )
        },
        bottomBar = {
            ChatInputBar(
                inputText = inputText,
                onInputChange = { inputText = it },
                onSendMessage = {
                    viewModel.sendMessage(pairingId, inputText)
                    inputText = ""
                },
                onOpenLocation = onOpenLocation,
                onOpenPhoto = { photoLauncher.launch("image/*") }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        if (messages.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.ChatBubbleOutline,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = OnSurfaceVariant.copy(alpha = 0.4f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "No messages yet",
                        style = MaterialTheme.typography.bodyLarge,
                        color = OnSurfaceVariant
                    )
                    Text(
                        "Send a message to start the conversation",
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }
        } else {
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
                    AnimatedMessageBubble(message = message, chatThemeIndex = chatThemeIndex)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatTopBar(
    partnerName: String,
    isTyping: Boolean,
    onOpenCall: (String) -> Unit,
    onBack: () -> Unit
) {
    TopAppBar(
        title = {
            Column {
                Text(
                    text = partnerName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                if (isTyping) {
                    Text(
                        text = "typing...",
                        style = MaterialTheme.typography.bodySmall,
                        color = Primary
                    )
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ChevronLeft, contentDescription = "Back", modifier = Modifier.size(28.dp))
            }
        },
        actions = {
            IconButton(onClick = { onOpenCall("voice") }) {
                Icon(Icons.Default.Phone, contentDescription = "Voice Call")
            }
            IconButton(onClick = { onOpenCall("video") }) {
                Icon(Icons.Default.Videocam, contentDescription = "Video Call")
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background
        )
    )
}

@Composable
private fun ChatInputBar(
    inputText: String,
    onInputChange: (String) -> Unit,
    onSendMessage: () -> Unit,
    onOpenLocation: () -> Unit,
    onOpenPhoto: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onOpenPhoto) {
                Icon(Icons.Default.Photo, contentDescription = "Photo", tint = Primary)
            }

            OutlinedTextField(
                value = inputText,
                onValueChange = onInputChange,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 40.dp),
                placeholder = { Text("Message") },
                shape = RoundedCornerShape(20.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = SurfaceLight,
                    focusedBorderColor = Primary,
                    unfocusedContainerColor = SurfaceLight.copy(alpha = 0.5f),
                    focusedContainerColor = SurfaceLight.copy(alpha = 0.5f)
                ),
                maxLines = 4
            )

            Spacer(modifier = Modifier.width(6.dp))

            AnimatedVisibility(
                visible = inputText.isNotBlank(),
                enter = scaleIn(animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessHigh)) + fadeIn(),
                exit = scaleOut() + fadeOut()
            ) {
                IconButton(
                    onClick = onSendMessage,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Primary)
                ) {
                    Icon(
                        Icons.Default.ArrowUpward,
                        contentDescription = "Send",
                        tint = OnPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            AnimatedVisibility(
                visible = inputText.isBlank(),
                enter = scaleIn() + fadeIn(),
                exit = scaleOut() + fadeOut()
            ) {
                IconButton(onClick = onOpenLocation) {
                    Icon(Icons.Default.LocationOn, contentDescription = "Location", tint = OnSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun AnimatedMessageBubble(message: ChatMessage, chatThemeIndex: Int = 0) {
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(message.messageId) { appeared = true }

    val offsetX by animateFloatAsState(
        targetValue = if (appeared) 0f else if (message.isFromMe) 80f else -80f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "slideIn"
    )
    val alpha by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = tween(200),
        label = "fadeIn"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                translationX = offsetX
                this.alpha = alpha
            }
            .padding(vertical = 1.dp, horizontal = 4.dp),
        contentAlignment = if (message.isFromMe) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        MessageBubble(message = message, chatThemeIndex = chatThemeIndex)
    }
}

@Composable
private fun MessageBubble(message: ChatMessage, chatThemeIndex: Int = 0) {
    val theme = ChatThemes.all.getOrNull(chatThemeIndex) ?: ChatThemes.blue

    val bubbleColor = if (message.isFromMe) theme.sentBg else theme.receivedBg
    val textColor = if (message.isFromMe) theme.sentText else theme.receivedText

    val shape = when {
        message.contentType == ContentType.PHOTO -> RoundedCornerShape(12.dp)
        message.isFromMe -> RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp)
        else -> RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp)
    }

    Surface(
        color = bubbleColor,
        shape = shape,
        modifier = Modifier.widthIn(max = 280.dp)
    ) {
        Column(modifier = Modifier.padding(if (message.contentType == ContentType.PHOTO) 2.dp else 10.dp)) {
            when (message.contentType) {
                ContentType.TEXT -> {
                    Text(
                        text = message.decryptedText ?: message.ciphertext,
                        color = textColor,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                ContentType.PHOTO -> {
                    message.mediaUrl?.let { url ->
                        if (url.startsWith("content://") || url.startsWith("/")) {
                            val model = if (url.startsWith("content://")) Uri.parse(url) else File(url)
                            AsyncImage(
                                model = model,
                                contentDescription = "Photo",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 240.dp)
                                    .clip(RoundedCornerShape(10.dp)),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(180.dp)
                                    .background(SurfaceLight)
                                    .clip(RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Image, null, tint = OnSurfaceVariant, modifier = Modifier.size(40.dp))
                            }
                        }
                    }
                }
                ContentType.VOICE -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.PlayArrow, null, tint = textColor)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Voice message", color = textColor, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                ContentType.VIDEO -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .background(SurfaceLight),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.PlayCircle, null, tint = OnSurfaceVariant, modifier = Modifier.size(48.dp))
                    }
                }
                ContentType.FILE -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.InsertDriveFile, null, tint = textColor)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(message.mediaUrl ?: "File", color = textColor, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                ContentType.LOCATION_REQUEST -> {
                    Text("📍 Location requested", color = textColor, style = MaterialTheme.typography.bodyMedium)
                }
                ContentType.LOCATION_RESPONSE -> {
                    Text("📍 Location shared", color = textColor, style = MaterialTheme.typography.bodyMedium)
                }
            }

            if (message.contentType != ContentType.PHOTO) {
                Spacer(modifier = Modifier.height(4.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = if (message.isFromMe) Arrangement.End else Arrangement.Start
            ) {
                Text(
                    text = formatMessageTime(message.timestamp),
                    color = textColor.copy(alpha = 0.5f),
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
                        tint = if (message.isRead) PrimaryLight else textColor.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}

private fun formatMessageTime(timestamp: Long): String {
    return SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(timestamp))
}
