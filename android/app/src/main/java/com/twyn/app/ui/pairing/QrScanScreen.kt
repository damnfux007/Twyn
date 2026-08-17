package com.twyn.app.ui.pairing

import android.Manifest
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.twyn.app.ui.theme.*
import java.io.File
import java.util.concurrent.Executors
import kotlinx.coroutines.delay

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun QrScanScreen(
    onPairingComplete: () -> Unit,
    onBack: () -> Unit,
    viewModel: PairingViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val cameraPermission = rememberPermissionState(Manifest.permission.CAMERA)
    var isScanning by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showSuccess by remember { mutableStateOf(false) }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            isScanning = false
            scanQrFromUri(context, it) { result ->
                when (result) {
                    is QrScanResult.Success -> {
                        viewModel.processScannedQr(result.content) {
                            onPairingComplete()
                        }
                    }
                    is QrScanResult.Error -> {
                        errorMessage = result.message
                        isScanning = true
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
                    containerColor = Color.Black.copy(alpha = 0.7f),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        containerColor = Color.Black
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (cameraPermission.status.isGranted) {
                LiveCameraScanner(
                    isScanning = isScanning,
                    onQrDetected = { content ->
                        isScanning = false
                        viewModel.processScannedQr(content) {
                            onPairingComplete()
                        }
                    }
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .padding(16.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(12.dp)
                ) {
                    Text(
                        text = "Point camera at Twyn QR code",
                        color = Color.White,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Box(
                    modifier = Modifier
                        .size(260.dp)
                        .align(Alignment.Center)
                        .border(2.dp, Primary, RoundedCornerShape(12.dp))
                )

                errorMessage?.let { error ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .padding(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = UnreadBadge.copy(alpha = 0.9f)
                        )
                    ) {
                        Text(
                            text = error,
                            modifier = Modifier.padding(12.dp),
                            color = Color.White
                        )
                    }
                }

                if (showSuccess) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.Center)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Primary.copy(alpha = 0.9f))
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "Pairing successful!",
                            color = Color.White,
                            fontSize = 16.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    OutlinedButton(
                        onClick = { galleryLauncher.launch("image/*") },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color.White
                        )
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Pick from Gallery")
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Camera permission is needed\nto scan QR codes",
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = { cameraPermission.launchPermissionRequest() },
                        colors = ButtonDefaults.buttonColors(containerColor = Primary)
                    ) {
                        Text("Grant Camera Permission")
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedButton(
                        onClick = { galleryLauncher.launch("image/*") },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                    ) {
                        Text("Pick from Gallery Instead")
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveCameraScanner(
    isScanning: Boolean,
    onQrDetected: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var lastScanTime by remember { mutableStateOf(0L) }

    val scanner = remember {
        BarcodeScanning.getClient(
            com.google.mlkit.vision.barcode.BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build()
        )
    }

    DisposableEffect(Unit) {
        onDispose { scanner.close() }
    }

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder().build()
                preview.setSurfaceProvider(previewView.surfaceProvider)

                val imageAnalysis = ImageAnalysis.Builder()
                    .setTargetResolution(Size(1280, 720))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                imageAnalysis.setAnalyzer(Executors.newSingleThreadExecutor()) { imageProxy ->
                    if (!isScanning) {
                        imageProxy.close()
                        return@setAnalyzer
                    }

                    val now = System.currentTimeMillis()
                    if (now - lastScanTime < 1000) {
                        imageProxy.close()
                        return@setAnalyzer
                    }

                    try {
                        val mediaImage = imageProxy.image
                        if (mediaImage != null) {
                            val image = InputImage.fromMediaImage(
                                mediaImage,
                                imageProxy.imageInfo.rotationDegrees
                            )
                            scanner.process(image)
                                .addOnSuccessListener { barcodes ->
                                    val twynBarcode = barcodes.firstOrNull { barcode ->
                                        barcode.rawValue?.contains("twyn", ignoreCase = true) == true
                                    }
                                    if (twynBarcode != null && isScanning) {
                                        lastScanTime = System.currentTimeMillis()
                                        onQrDetected(twynBarcode.rawValue!!)
                                    }
                                }
                                .addOnFailureListener { e ->
                                    Log.e("QrScan", "Barcode scan failed", e)
                                }
                                .addOnCompleteListener {
                                    imageProxy.close()
                                }
                        } else {
                            imageProxy.close()
                        }
                    } catch (e: Exception) {
                        Log.e("QrScan", "Image analysis error", e)
                        imageProxy.close()
                    }
                }

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageAnalysis
                    )
                } catch (e: Exception) {
                    Log.e("QrScan", "Camera bind failed", e)
                }
            }, ContextCompat.getMainExecutor(ctx))

            previewView
        },
        modifier = Modifier.fillMaxSize()
    )
}

private sealed class QrScanResult {
    data class Success(val content: String) : QrScanResult()
    data class Error(val message: String) : QrScanResult()
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

        val scanner = BarcodeScanning.getClient(
            com.google.mlkit.vision.barcode.BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build()
        )

        val image = InputImage.fromBitmap(bitmap, 0)
        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                val twynBarcode = barcodes.firstOrNull { barcode ->
                    barcode.rawValue?.contains("twyn", ignoreCase = true) == true
                }
                if (twynBarcode != null) {
                    onResult(QrScanResult.Success(twynBarcode.rawValue!!))
                } else {
                    onResult(QrScanResult.Error("No Twyn QR code found in this image."))
                }
            }
            .addOnFailureListener { e ->
                onResult(QrScanResult.Error("Failed to scan image: ${e.message}"))
            }
            .addOnCompleteListener {
                scanner.close()
            }
    } catch (e: Exception) {
        onResult(QrScanResult.Error("Cannot read image: ${e.message}"))
    }
}
