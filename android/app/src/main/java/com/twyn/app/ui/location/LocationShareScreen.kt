package com.twyn.app.ui.location

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.twyn.app.ui.theme.*

/**
 * Location sharing screen.
 *
 * On-demand only — no background tracking, minimal battery use.
 *
 * How it works:
 * 1. Tap "Request Location" → sends request to partner's phone
 * 2. Partner's phone wakes briefly → grabs fresh GPS reading
 * 3. Location data returned → displayed on OpenStreetMap
 *
 * Display uses OpenStreetMap (osmdroid) — free, no API key needed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationShareScreen(
    pairingId: String,
    onBack: () -> Unit,
    viewModel: LocationViewModel = hiltViewModel()
) {
    val partnerLocation by viewModel.partnerLocation.collectAsState()
    val myLocation by viewModel.myLocation.collectAsState()
    var hasRequested by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Location Sharing") },
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
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Info card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = Primary.copy(alpha = 0.1f)
                ),
                shape = MaterialTheme.shapes.medium
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = Primary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Location is shared on-demand only. " +
                               "No background tracking. Minimal battery use.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = OnSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Map placeholder — in production, use osmdroid MapView
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                colors = CardDefaults.cardColors(containerColor = Surface)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    // OpenStreetMap would be rendered here via osmdroid
                    // MapView(context) with markers for both users' locations
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Map,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = OnSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Map view\n(OpenStreetMap)",
                            color = OnSurfaceVariant,
                            textAlign = TextAlign.Center
                        )

                        partnerLocation?.let { loc ->
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Partner: ${loc.latitude}, ${loc.longitude}",
                                color = Primary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Request partner's location
                Button(
                    onClick = {
                        viewModel.requestPartnerLocation(pairingId, "local_user")
                        hasRequested = true
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Primary)
                ) {
                    Icon(Icons.Default.MyLocation, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Request Location")
                }

                // Share my location
                OutlinedButton(
                    onClick = { viewModel.shareMyLocation() },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.ShareLocation, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Share Mine")
                }
            }

            if (hasRequested && partnerLocation == null) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Waiting for partner to share location...",
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurfaceVariant
                )
            }
        }
    }
}
