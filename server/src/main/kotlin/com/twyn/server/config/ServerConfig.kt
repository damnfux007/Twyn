package com.twyn.server.config

/**
 * Server configuration. For Oracle Cloud Always Free tier deployment.
 * Adjust these values for your environment.
 */
object ServerConfig {
    const val HOST = "0.0.0.0"
    const val PORT = 8080

    // Media storage
    const val MEDIA_STORAGE_PATH = "/tmp/twyn-media"
    const val MEDIA_EXPIRY_HOURS = 24L
    const val MAX_FILE_SIZE_MB = 50L

    // Connection limits (generous for 5-10 users)
    const val MAX_WEBSOCKET_CONNECTIONS = 50
    const val HEARTBEAT_INTERVAL_MS = 30_000L
    const val CONNECTION_TIMEOUT_MS = 60_000L
}
