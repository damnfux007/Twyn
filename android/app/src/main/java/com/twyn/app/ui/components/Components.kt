package com.twyn.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import com.twyn.app.ui.theme.*

/**
 * Animated typing indicator — three bouncing dots.
 * Shown when the paired contact is typing.
 */
@Composable
fun TypingIndicator(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "typing")

    Row(
        modifier = modifier.padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(3) { index ->
            val scale by infiniteTransition.animateFloat(
                initialValue = 0.5f,
                targetValue = 1.2f,
                animationSpec = infiniteRepeatable(
                    animation = tween(400, delayMillis = index * 150),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "dot$index"
            )
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .scale(scale)
                    .clip(CircleShape)
                    .background(OnSurfaceVariant)
            )
        }
    }
}

/**
 * Animated unread badge with a subtle bounce effect.
 */
@Composable
fun UnreadBadge(
    count: Int,
    modifier: Modifier = Modifier
) {
    if (count <= 0) return

    val infiniteTransition = rememberInfiniteTransition(label = "badge")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "badgeScale"
    )

    Box(
        modifier = modifier
            .scale(scale)
            .size(22.dp)
            .clip(CircleShape)
            .background(UnreadBadge),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (count > 99) "99+" else if (count > 9) "9+" else "$count",
            color = OnPrimary,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

/**
 * Online status indicator dot.
 */
@Composable
fun OnlineIndicator(
    isOnline: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(12.dp)
            .clip(CircleShape)
            .background(if (isOnline) OnlineGreen else OnSurfaceVariant)
            .padding(2.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface)
            .padding(2.dp)
            .clip(CircleShape)
            .background(if (isOnline) OnlineGreen else OnSurfaceVariant)
    )
}

/**
 * Encrypted message lock icon — confirms E2E encryption.
 */
@Composable
fun EncryptionIndicator(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.padding(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "\uD83D\uDD12", // Lock emoji
            style = MaterialTheme.typography.labelSmall
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = "End-to-end encrypted",
            style = MaterialTheme.typography.labelSmall,
            color = OnSurfaceVariant
        )
    }
}
