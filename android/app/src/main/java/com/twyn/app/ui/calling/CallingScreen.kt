package com.twyn.app.ui.calling

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.twyn.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallingScreen(
    pairingId: String,
    callType: String,
    onCallEnd: () -> Unit,
    viewModel: CallingViewModel = hiltViewModel()
) {
    val callState by viewModel.callState.collectAsState()
    val callDuration by viewModel.callDuration.collectAsState()

    LaunchedEffect(pairingId) {
        viewModel.startCall(pairingId, "local_user", callType)
    }

    Scaffold(
        containerColor = Background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.padding(top = 64.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .clip(CircleShape)
                        .background(Primary),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "P",
                        color = OnPrimary,
                        style = MaterialTheme.typography.displayLarge
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Paired Contact",
                    style = MaterialTheme.typography.headlineMedium,
                    color = OnSurface,
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = when (callState) {
                        CallState.CALLING -> "Calling..."
                        CallState.RECEIVING -> "Incoming call..."
                        CallState.CONNECTED -> formatDuration(callDuration)
                        CallState.ENDED -> "Call ended"
                        CallState.FAILED -> "Call failed"
                        CallState.IDLE -> ""
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = when (callState) {
                        CallState.CONNECTED -> OnlineGreen
                        CallState.FAILED -> UnreadBadge
                        else -> OnSurfaceVariant
                    }
                )
            }

            if (callType == "video" && callState == CallState.CONNECTED) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(16.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .background(Surface)
                )
            }

            Row(
                modifier = Modifier.padding(32.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                var isMuted by remember { mutableStateOf(false) }
                FloatingActionButton(
                    onClick = { isMuted = !isMuted },
                    containerColor = if (isMuted) UnreadBadge else SurfaceLight,
                    shape = CircleShape,
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(
                        if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                        contentDescription = "Mute",
                        tint = OnSurface
                    )
                }

                FloatingActionButton(
                    onClick = {
                        viewModel.endCall(pairingId)
                        onCallEnd()
                    },
                    containerColor = UnreadBadge,
                    shape = CircleShape,
                    modifier = Modifier.size(72.dp)
                ) {
                    Icon(
                        Icons.Default.CallEnd,
                        contentDescription = "End Call",
                        tint = OnPrimary,
                        modifier = Modifier.size(32.dp)
                    )
                }

                var isSpeaker by remember { mutableStateOf(false) }
                FloatingActionButton(
                    onClick = { isSpeaker = !isSpeaker },
                    containerColor = if (isSpeaker) Primary else SurfaceLight,
                    shape = CircleShape,
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(
                        if (isSpeaker) Icons.Default.VolumeUp else Icons.Default.VolumeDown,
                        contentDescription = "Speaker",
                        tint = OnSurface
                    )
                }
            }
        }
    }
}

private fun formatDuration(seconds: Long): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return "%d:%02d".format(mins, secs)
}
