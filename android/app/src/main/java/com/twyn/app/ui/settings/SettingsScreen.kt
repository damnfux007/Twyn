package com.twyn.app.ui.settings

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.twyn.app.ui.theme.*
import java.io.ByteArrayOutputStream
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onNavigateToPairing: () -> Unit = {},
    onCheckUpdate: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val profile by viewModel.profile.collectAsState()
    val isDarkTheme by viewModel.isDarkTheme.collectAsState()
    val chatThemeIndex by viewModel.chatThemeIndex.collectAsState()
    var showChatThemeDialog by remember { mutableStateOf(false) }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val inputStream = context.contentResolver.openInputStream(it)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()
                val scaled = Bitmap.createScaledBitmap(bitmap, 400, 400, true)
                val baos = ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.JPEG, 80, baos)
                val base64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
                val file = File(context.filesDir, "profile_photo.jpg")
                file.writeBytes(Base64.decode(base64, Base64.DEFAULT))
                viewModel.updateProfilePhoto(file.absolutePath)
            } catch (_: Exception) { }
        }
    }

    if (showChatThemeDialog) {
        AlertDialog(
            onDismissRequest = { showChatThemeDialog = false },
            title = { Text("Chat Bubble Color") },
            text = {
                Column {
                    ChatThemes.all.forEachIndexed { index, theme ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    viewModel.setChatTheme(index)
                                    showChatThemeDialog = false
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(theme.sentBg)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(theme.name, style = MaterialTheme.typography.bodyLarge)
                            Spacer(modifier = Modifier.weight(1f))
                            if (index == chatThemeIndex) {
                                Icon(Icons.Default.Check, null, tint = Primary)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showChatThemeDialog = false }) {
                    Text("Done")
                }
            }
        )
    }

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
            SectionHeader("Profile")

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
                    if (profile.profilePhotoUrl.isNotEmpty() && File(profile.profilePhotoUrl).exists()) {
                        AsyncImage(
                            model = File(profile.profilePhotoUrl),
                            contentDescription = "Profile Photo",
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Text(
                            text = profile.displayName.take(1).uppercase(),
                            color = OnPrimary,
                            style = MaterialTheme.typography.displayLarge
                        )
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = profile.displayName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Medium
                    )
                    TextButton(onClick = { galleryLauncher.launch("image/*") }) {
                        Text("Change Photo")
                    }
                }
            }

            var displayName by remember { mutableStateOf(profile.displayName) }
            SettingTextField(
                label = "Display Name",
                value = displayName,
                onValueChange = {
                    displayName = it
                    viewModel.updateDisplayName(it)
                }
            )

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

            Divider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))

            SectionHeader("Privacy")

            SettingToggle(
                title = "Show Online Status",
                subtitle = "Let paired contacts see when you're online",
                icon = Icons.Default.Visibility,
                checked = profile.showOnlineStatus,
                onCheckedChange = { viewModel.toggleOnlineStatus(it) }
            )

            Divider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))

            SectionHeader("Appearance")

            SettingToggle(
                title = "Dark Theme",
                subtitle = "Use dark color scheme",
                icon = Icons.Default.DarkMode,
                checked = isDarkTheme,
                onCheckedChange = { viewModel.toggleDarkTheme(it) }
            )

            SettingClickable(
                title = "Chat Theme",
                subtitle = "Bubble color: ${ChatThemes.all.getOrNull(chatThemeIndex)?.name ?: "Blue"}",
                icon = Icons.Default.Palette,
                onClick = { showChatThemeDialog = true }
            )

            Divider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))

            SectionHeader("Account")

            SettingClickable(
                title = "Your Pairing QR Code",
                subtitle = "Generate a new QR code to pair with someone",
                icon = Icons.Default.QrCode,
                onClick = onNavigateToPairing
            )

            SettingClickable(
                title = "Check for Updates",
                subtitle = "See if a new version is available",
                icon = Icons.Default.SystemUpdate,
                onClick = onCheckUpdate
            )

            Divider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))

            SectionHeader("About")

            SettingClickable(
                title = "Twyn v1.0.20",
                subtitle = "End-to-end encrypted 1-on-1 messaging",
                icon = Icons.Default.Info,
                onClick = onCheckUpdate
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
        placeholder = if (placeholder.isNotEmpty()) {{ Text(placeholder) }} else null,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
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
        Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
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
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = OnSurfaceVariant)
    }
}
