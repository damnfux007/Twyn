package com.twyn.app.ui.pairing

import android.Manifest
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer
import com.twyn.app.ui.theme.*
import java.io.File

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun QrScanScreen(
    onPairingComplete: () -> Unit,
    onBack: () -> Unit,
    viewModel: PairingViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    var isProcessing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val cameraPermission = rememberPermissionState(Manifest.permission.CAMERA)

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            isProcessing = true
            errorMessage = null
            scanQrFromUri(context, it) { result ->
                isProcessing = false
                when (result) {
                    is QrScanResult.Success -> {
                        viewModel.processScannedQr(result.content) {
                            onPairingComplete()
                        }
                    }
                    is QrScanResult.Error -> {
                        errorMessage = result.message
                    }
                }
            }
        }
    }

    val photoFile = remember {
        File(context.cacheDir, "twyn_qr_photo.jpg").also { it.deleteOnExit() }
    }
    val photoUri = remember {
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", photoFile)
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && photoFile.exists()) {
            isProcessing = true
            errorMessage = null
            scanQrFromFile(context, photoFile) { result ->
                isProcessing = false
                photoFile.delete()
                when (result) {
                    is QrScanResult.Success -> {
                        viewModel.processScannedQr(result.content) {
                            onPairingComplete()
                        }
                    }
                    is QrScanResult.Error -> {
                        errorMessage = result.message
                    }
                }
            }
        } else {
            errorMessage = "Photo capture failed. Try again."
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scan QR Code") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
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
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Point your camera at the Twyn QR code\nor pick one from your gallery",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = {
                    if (cameraPermission.status.isGranted) {
                        cameraLauncher.launch(photoUri)
                    } else {
                        cameraPermission.launchPermissionRequest()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Primary),
                enabled = !isProcessing
            ) {
                Icon(Icons.Default.CameraAlt, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Scan with Camera")
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedButton(
                onClick = { galleryLauncher.launch("image/*") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = !isProcessing
            ) {
                Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Pick from Gallery")
            }

            if (isProcessing) {
                Spacer(modifier = Modifier.height(24.dp))
                CircularProgressIndicator(color = Primary)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Processing QR code...", color = OnSurfaceVariant)
            }

            errorMessage?.let { error ->
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = UnreadBadge.copy(alpha = 0.15f)
                    )
                ) {
                    Text(
                        text = error,
                        modifier = Modifier.padding(12.dp),
                        color = UnreadBadge
                    )
                }
            }
        }
    }
}

private sealed class QrScanResult {
    data class Success(val content: String) : QrScanResult()
    data class Error(val message: String) : QrScanResult()
}

private fun decodeQrFromBitmap(bitmap: android.graphics.Bitmap): QrScanResult {
    val width = bitmap.width
    val height = bitmap.height
    val pixels = IntArray(width * height)
    bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

    val source = RGBLuminanceSource(width, height, pixels)
    val binaryBitmap = BinaryBitmap(HybridBinarizer(source))

    val reader = MultiFormatReader()
    return try {
        val result = reader.decode(binaryBitmap)
        val text = result.text
        if (text.contains("twyn", ignoreCase = true)) {
            QrScanResult.Success(text)
        } else {
            QrScanResult.Error("Scanned code is not a Twyn QR code.")
        }
    } catch (e: NotFoundException) {
        val rotated = rotateBitmap(bitmap, 90)
        if (rotated != null) {
            decodeQrFromBitmap(rotated)
        } else {
            QrScanResult.Error("No QR code detected. Make sure the code is clear, centered, and well-lit.")
        }
    } catch (e: Exception) {
        QrScanResult.Error("Failed to read QR code: ${e.message}")
    }
}

private fun rotateBitmap(source: android.graphics.Bitmap, degrees: Int): android.graphics.Bitmap? {
    return try {
        val matrix = android.graphics.Matrix()
        matrix.postRotate(degrees.toFloat())
        android.graphics.Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    } catch (e: Exception) {
        null
    }
}

private fun scanQrFromFile(
    context: Context,
    file: File,
    onResult: (QrScanResult) -> Unit
) {
    try {
        val bitmap = BitmapFactory.decodeFile(file.absolutePath)
        if (bitmap == null) {
            onResult(QrScanResult.Error("Cannot read the photo."))
            return
        }
        onResult(decodeQrFromBitmap(bitmap))
    } catch (e: Exception) {
        onResult(QrScanResult.Error("Cannot read image: ${e.message}"))
    }
}

private fun scanQrFromUri(
    context: Context,
    uri: Uri,
    onResult: (QrScanResult) -> Unit
) {
    try {
        val inputStream = context.contentResolver.openInputStream(uri)
        val bitmap = BitmapFactory.decodeStream(inputStream)
        inputStream?.close()

        if (bitmap == null) {
            onResult(QrScanResult.Error("Cannot read this image."))
            return
        }
        onResult(decodeQrFromBitmap(bitmap))
    } catch (e: Exception) {
        onResult(QrScanResult.Error("Cannot read image: ${e.message}"))
    }
}
