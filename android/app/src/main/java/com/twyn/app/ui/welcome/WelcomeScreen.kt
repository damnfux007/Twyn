package com.twyn.app.ui.welcome

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.twyn.app.ui.theme.*

/**
 * Welcome / Sign-In screen shown on first launch.
 *
 * Features:
 * - Display name input (shown to all paired contacts)
 * - Bio (optional, shown in profile)
 * - Server URL input (pre-filled with Render URL)
 * - One-tap sign-in, no password needed
 * - Clean animated entrance
 */
@Composable
fun WelcomeScreen(
    onSignInComplete: () -> Unit,
    viewModel: WelcomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // Navigate away when sign-in completes
    LaunchedEffect(uiState.isComplete) {
        if (uiState.isComplete) onSignInComplete()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Background, Surface)
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(60.dp))

            // App logo / icon
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(Primary),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "T",
                    color = OnPrimary,
                    fontSize = 42.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // App name
            Text(
                text = "Twyn",
                style = MaterialTheme.typography.displayLarge,
                color = OnSurface,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Tagline
            Text(
                text = "Private, encrypted 1-on-1 messaging",
                style = MaterialTheme.typography.bodyLarge,
                color = OnSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Encryption badge
            Surface(
                color = Primary.copy(alpha = 0.15f),
                shape = RoundedCornerShape(20.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = Primary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "End-to-end encrypted",
                        style = MaterialTheme.typography.labelSmall,
                        color = Primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(48.dp))

            // ── Sign-In Form ────────────────────────────────────

            // Display name
            OutlinedTextField(
                value = uiState.displayName,
                onValueChange = { viewModel.updateName(it) },
                label = { Text("Your Name") },
                placeholder = { Text("What should your contacts call you?") },
                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Primary,
                    unfocusedBorderColor = SurfaceLight,
                    focusedContainerColor = Surface.copy(alpha = 0.5f),
                    unfocusedContainerColor = Surface.copy(alpha = 0.5f)
                ),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Bio (optional)
            OutlinedTextField(
                value = uiState.bio,
                onValueChange = { viewModel.updateBio(it) },
                label = { Text("Bio (optional)") },
                placeholder = { Text("Tell people about yourself...") },
                leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Primary,
                    unfocusedBorderColor = SurfaceLight,
                    focusedContainerColor = Surface.copy(alpha = 0.5f),
                    unfocusedContainerColor = Surface.copy(alpha = 0.5f)
                ),
                maxLines = 3
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Server URL
            OutlinedTextField(
                value = uiState.serverUrl,
                onValueChange = { viewModel.updateServerUrl(it) },
                label = { Text("Server URL") },
                placeholder = { Text("wss://twyn-server.onrender.com/ws") },
                leadingIcon = { Icon(Icons.Default.Storage, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Primary,
                    unfocusedBorderColor = SurfaceLight,
                    focusedContainerColor = Surface.copy(alpha = 0.5f),
                    unfocusedContainerColor = Surface.copy(alpha = 0.5f)
                ),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Server URL helper text
            Text(
                text = "Don't have a server? Your friend can share one, or host free on Render.com",
                style = MaterialTheme.typography.bodySmall,
                color = OnSurfaceVariant.copy(alpha = 0.7f)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Error message
            AnimatedVisibility(visible = uiState.error != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = UnreadBadge.copy(alpha = 0.15f)
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = uiState.error ?: "",
                        modifier = Modifier.padding(12.dp),
                        color = UnreadBadge,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            if (uiState.error != null) Spacer(modifier = Modifier.height(16.dp))

            // Sign In button
            Button(
                onClick = { viewModel.signIn() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Primary),
                shape = RoundedCornerShape(12.dp),
                enabled = uiState.displayName.isNotBlank()
            ) {
                Icon(Icons.Default.ArrowForward, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Get Started",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Footer
            Text(
                text = "Your messages are always encrypted.\nThe server never sees what you type.",
                style = MaterialTheme.typography.bodySmall,
                color = OnSurfaceVariant.copy(alpha = 0.5f),
                textAlign = TextAlign.Center
            )
        }
    }
}
