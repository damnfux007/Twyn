package com.twyn.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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

/**
 * Settings screen — profile and app configuration.
 *
 * Features:
 * - Edit display name and bio (shown to all paired contacts)
 * - Profile photo (uploaded to server, shown in chat list)
 * - Online/last-seen status toggle
 * - Chat theme customization
 * - App theme (dark/light)
 * - About/version info
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val profile by viewModel.profile.collectAsState()
    var showOnlineStatus by remember { mutableStateOf(profile.showOnlineStatus) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            // ── Profile Section ────────────────────────────────────
            SectionHeader("Profile")

            // Profile photo
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(Primary),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = profile.displayName.take(1).uppercase(),
                        color = OnPrimary,
                        style = MaterialTheme.typography.displayLarge
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = profile.displayName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Medium
                    )
                    TextButton(onClick = { /* Open photo picker */ }) {
                        Text("Change Photo")
                    }
                }
            }

            // Display name
            var displayName by remember { mutableStateOf(profile.displayName) }
            SettingTextField(
                label = "Display Name",
                value = displayName,
                onValueChange = {
                    displayName = it
                    viewModel.updateDisplayName(it)
                }
            )

            // Bio
            var bio by remember { mutableStateOf(profile.bio) }
            SettingTextField(
                label = "Bio",
                value = bio,
                onValueChange = {
                    bio = it
                    viewModel.updateBio(it)
                },
                placeholder = "Tell your contacts about yourself..."
            )

            Divider(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                color = SurfaceLight
            )

            // ── Privacy Section ────────────────────────────────────
            SectionHeader("Privacy")

            // Online status toggle
            SettingToggle(
                title = "Show Online Status",
                subtitle = "Let paired contacts see when you're online",
                icon = Icons.Default.Visibility,
                checked = showOnlineStatus,
                onCheckedChange = {
                    showOnlineStatus = it
                    viewModel.toggleOnlineStatus(it)
                }
            )

            Divider(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                color = SurfaceLight
            )

            // ── Appearance Section ──────────────────────────────────
            SectionHeader("Appearance")

            // Theme toggle
            var isDarkTheme by remember { mutableStateOf(true) }
            SettingToggle(
                title = "Dark Theme",
                subtitle = "Use dark color scheme",
                icon = Icons.Default.DarkMode,
                checked = isDarkTheme,
                onCheckedChange = { isDarkTheme = it }
            )

            // Chat wallpaper / theme
            SettingClickable(
                title = "Chat Theme",
                subtitle = "Customize chat bubble colors",
                icon = Icons.Default.Palette,
                onClick = { /* Open theme picker */ }
            )

            Divider(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                color = SurfaceLight
            )

            // ── Account Section ─────────────────────────────────────
            SectionHeader("Account")

            SettingClickable(
                title = "Your Pairing QR Code",
                subtitle = "Generate a new QR code to pair with someone",
                icon = Icons.Default.QrCode,
                onClick = { /* Navigate to pairing screen */ }
            )

            SettingClickable(
                title = "Google Drive",
                subtitle = "Manage permanent media storage",
                icon = Icons.Default.Cloud,
                onClick = { /* Open Google Sign-In */ }
            )

            Divider(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                color = SurfaceLight
            )

            // ── About Section ───────────────────────────────────────
            SectionHeader("About")

            SettingClickable(
                title = "Twyn v1.0.0",
                subtitle = "End-to-end encrypted 1-on-1 messaging",
                icon = Icons.Default.Info,
                onClick = { }
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = Primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
    )
}

@Composable
private fun SettingTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = ""
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { Text(placeholder, color = OnSurfaceVariant) },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Primary,
            unfocusedBorderColor = SurfaceLight
        ),
        singleLine = label == "Display Name"
    )
}

@Composable
private fun SettingToggle(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = OnSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = Primary
            )
        )
    }
}

@Composable
private fun SettingClickable(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = OnSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
        }
        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            tint = OnSurfaceVariant
        )
    }
}
