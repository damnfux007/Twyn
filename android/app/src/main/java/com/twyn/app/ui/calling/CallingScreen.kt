package com.twyn.app.ui.calling

import androidx.compose.animation.core.*
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
    val partnerName by viewModel.partnerName.collectAsState()

    LaunchedEffect(pairingId) {
        viewModel.loadPartnerName(pairingId)
        viewModel.startCall(pairingId, callType)
    }

    // Pulsing animation for calling state
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(modifier = Modifier.height(80.dp))

            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .graphicsLayer {
                            if (callState == CallState.CALLING) {
                                scaleX = pulseScale
                                scaleY = pulseScale
                            }
                        }
                        .clip(CircleShape)
                        .background(Primary),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = partnerName.take(1).uppercase(),
                        color = OnPrimary,
                        style = MaterialTheme.typography.displayLarge
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = partnerName,
                    style = MaterialTheme.typography.headlineMedium,
                    color = OnSurface,
                    fontWeight = FontWeight.SemiBold
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
                IconButton(
                    onClick = { isMuted = !isMuted },
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(if (isMuted) UnreadBadge.copy(alpha = 0.2f) else SurfaceLight)
                ) {
                    Icon(
                        if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                        contentDescription = "Mute",
                        tint = if (isMuted) UnreadBadge else OnSurface
                    )
                }

                IconButton(
                    onClick = {
                        viewModel.endCall(pairingId)
                        onCallEnd()
                    },
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(UnreadBadge)
                ) {
                    Icon(
                        Icons.Default.CallEnd,
                        contentDescription = "End Call",
                        tint = OnPrimary,
                        modifier = Modifier.size(32.dp)
                    )
                }

                var isSpeaker by remember { mutableStateOf(false) }
                IconButton(
                    onClick = { isSpeaker = !isSpeaker },
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(if (isSpeaker) Primary.copy(alpha = 0.2f) else SurfaceLight)
                ) {
                    Icon(
                        if (isSpeaker) Icons.Default.VolumeUp else Icons.Default.VolumeDown,
                        contentDescription = "Speaker",
                        tint = if (isSpeaker) Primary else OnSurface
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
