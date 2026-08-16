package com.twyn.app.ui.pairing

import android.Manifest
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
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
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.twyn.app.ui.theme.*

/**
 * QR Code scanning screen.
 * Supports two modes:
 * 1. Live camera scanning using ML Kit barcode detection
 * 2. Pick an existing QR code image from the phone's photo gallery
 *
 * Both methods decode the same QR format: "twyn:<userId>:<preKeyBundle>:<token>"
 */
@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun QrScanScreen(
    onPairingComplete: () -> Unit,
    onBack: () -> Unit,
    viewModel: PairingViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    var scanMode by remember { mutableStateOf<ScanMode?>(null) }
    var isProcessing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val cameraPermission = rememberPermissionState(Manifest.permission.CAMERA)

    // Gallery image picker
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            isProcessing = true
            scanQrFromImage(context, it) { result ->
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

    // Camera capture (simplified — in production use CameraX preview)
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        bitmap?.let {
            isProcessing = true
            scanQrFromBitmap(context, it) { result ->
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
                text = "Choose how to scan the QR code",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Camera scan button
            Button(
                onClick = {
                    if (cameraPermission.status.isGranted) {
                        scanMode = ScanMode.CAMERA
                        cameraLauncher.launch(null)
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

            // Gallery pick button
            OutlinedButton(
                onClick = {
                    scanMode = ScanMode.GALLERY
                    galleryLauncher.launch("image/*")
                },
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

private enum class ScanMode { CAMERA, GALLERY }

private sealed class QrScanResult {
    data class Success(val content: String) : QrScanResult()
    data class Error(val message: String) : QrScanResult()
}

/**
 * Scan a QR code from a Bitmap (camera capture).
 * Uses ML Kit barcode detection.
 */
private fun scanQrFromBitmap(
    context: Context,
    bitmap: android.graphics.Bitmap,
    onResult: (QrScanResult) -> Unit
) {
    val image = InputImage.fromBitmap(bitmap, 0)
    val scanner = BarcodeScanning.getClient()

    scanner.process(image)
        .addOnSuccessListener { barcodes ->
            val qr = barcodes.firstOrNull { barcode ->
                barcode.valueType == Barcode.TYPE_TEXT &&
                barcode.rawValue?.startsWith("twyn:") == true
            }
            if (qr != null) {
                onResult(QrScanResult.Success(qr.rawValue!!))
            } else {
                onResult(QrScanResult.Error("No valid Twyn QR code found in image"))
            }
        }
        .addOnFailureListener { e ->
            onResult(QrScanResult.Error("Failed to scan QR code: ${e.message}"))
        }
}

/**
 * Scan a QR code from a gallery image URI.
 */
private fun scanQrFromImage(
    context: Context,
    uri: Uri,
    onResult: (QrScanResult) -> Unit
) {
    try {
        val image = InputImage.fromFilePath(context, uri)
        val scanner = BarcodeScanning.getClient()

        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                val qr = barcodes.firstOrNull { barcode ->
                    barcode.valueType == Barcode.TYPE_TEXT &&
                    barcode.rawValue?.startsWith("twyn:") == true
                }
                if (qr != null) {
                    onResult(QrScanResult.Success(qr.rawValue!!))
                } else {
                    onResult(QrScanResult.Error("No valid Twyn QR code found in image"))
                }
            }
            .addOnFailureListener { e ->
                onResult(QrScanResult.Error("Failed to scan: ${e.message}"))
            }
    } catch (e: Exception) {
        onResult(QrScanResult.Error("Cannot read image: ${e.message}"))
    }
}
