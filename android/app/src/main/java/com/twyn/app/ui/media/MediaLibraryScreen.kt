package com.twyn.app.ui.media

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.twyn.app.domain.model.MediaFile
import com.twyn.app.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * Media Library screen for a specific paired chat.
 *
 * Shows all shared media (photos, videos, voice messages, files)
 * in a grid layout, organized by date. Pulls from each user's own
 * Google Drive for permanent storage.
 *
 * Features:
 * - Grid view of photos/videos with thumbnails
 * - File list view for documents
 * - Upload new media via camera or gallery
 * - On-demand loading from Google Drive
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaLibraryScreen(
    pairingId: String,
    onBack: () -> Unit,
    viewModel: MediaViewModel = hiltViewModel()
) {
    val mediaFiles by viewModel.mediaFiles.collectAsState()
    val isUploading by viewModel.isUploading.collectAsState()
    val context = LocalContext.current

    // Load media when screen opens
    LaunchedEffect(pairingId) {
        viewModel.loadMedia(pairingId)
    }

    // Gallery picker
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.uploadMedia(pairingId, it, "image/*")
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Media Library", fontWeight = FontWeight.Medium)
                },
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
        floatingActionButton = {
            FloatingActionButton(
                onClick = { galleryLauncher.launch("image/*") },
                containerColor = Primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Upload Media")
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        if (isUploading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Primary)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Encrypting and uploading...", color = OnSurfaceVariant)
                }
            }
        } else if (mediaFiles.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Collections,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = OnSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("No media shared yet", color = OnSurfaceVariant)
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(4.dp)
            ) {
                items(mediaFiles) { media ->
                    MediaGridItem(media = media)
                }
            }
        }
    }
}

/**
 * Individual media item in the grid.
 * Shows thumbnail for images, icon for other file types.
 */
@Composable
private fun MediaGridItem(media: MediaFile) {
    Card(
        modifier = Modifier
            .padding(2.dp)
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .clickable { /* Open media viewer */ },
        colors = CardDefaults.cardColors(
            containerColor = SurfaceLight
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                media.contentType.startsWith("image/") -> {
                    // Show image thumbnail
                    media.localPath?.let { path ->
                        AsyncImage(
                            model = java.io.File(path),
                            contentDescription = media.fileName,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } ?: media.thumbnailBase64?.let { base64 ->
                        val bitmap = remember(base64) {
                            android.util.Base64.decode(base64, android.util.Base64.DEFAULT).let { bytes ->
                                android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                            }
                        }
                        bitmap?.let {
                            AsyncImage(
                                model = it,
                                contentDescription = media.fileName,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                    } ?: Icon(
                        Icons.Default.Image,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        tint = OnSurfaceVariant
                    )
                }
                media.contentType.startsWith("video/") -> {
                    Icon(
                        Icons.Default.PlayCircle,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        tint = OnSurfaceVariant
                    )
                }
                media.contentType.startsWith("audio/") -> {
                    Icon(
                        Icons.Default.AudioFile,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        tint = OnSurfaceVariant
                    )
                }
                else -> {
                    Icon(
                        Icons.Default.InsertDriveFile,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        tint = OnSurfaceVariant
                    )
                }
            }

            // File name overlay at bottom
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter),
                color = Surface.copy(alpha = 0.8f)
            ) {
                Text(
                    text = media.fileName,
                    style = MaterialTheme.typography.labelSmall,
                    color = OnSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.padding(4.dp)
                )
            }
        }
    }
}
