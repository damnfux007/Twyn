package com.twyn.server

import com.twyn.server.config.ServerConfig
import com.twyn.server.models.*
import com.twyn.server.routing.MessageHandler
import com.twyn.server.services.ConnectionManager
import com.twyn.server.services.InMemoryStore
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

/**
 * Twyn WebSocket Server
 *
 * A lightweight server designed to run on Oracle Cloud's Always Free tier.
 * Responsibilities:
 *  - Route encrypted messages between paired users (server never sees plaintext)
 *  - Queue messages for offline delivery, delete after delivery
 *  - Manage temporary media storage (24h expiry)
 *  - Relay WebRTC signaling messages (offer/answer/ICE candidates)
 *  - Handle pairing QR code generation and completion
 *
 * The server is intentionally thin — all encryption, key management,
 * and media processing happens client-side on each user's device.
 */
fun main() {
    // Ensure media storage directory exists
    File(ServerConfig.MEDIA_STORAGE_PATH).mkdirs()

    embeddedServer(Netty, port = ServerConfig.PORT, host = ServerConfig.HOST) {
        configureSerialization()
        configureCors()
        configureWebSockets()
        configureStatusPages()
        configureRoutes()
        startMediaCleanupJob()
    }.start(wait = true)
}

private fun Application.configureSerialization() {
    install(ContentNegotiation) {
        json(Json {
            ignoreUnknownKeys = true
            isLenient = true
            prettyPrint = false
        })
    }
}

private fun Application.configureCors() {
    install(CORS) {
        anyHost()
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
    }
}

private fun Application.configureWebSockets() {
    install(WebSockets) {
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }
}

private fun Application.configureStatusPages() {
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            call.respondText(
                "Internal error: ${cause.message}",
                status = HttpStatusCode.InternalServerError
            )
        }
    }
}

/**
 * HTTP and WebSocket routes.
 *
 * HTTP endpoints:
 *   GET  /health           — Health check for uptime monitoring
 *   POST /api/users        — Register/update a user profile
 *   GET  /api/users/{id}   — Get a user's public profile
 *   POST /api/media/upload — Upload encrypted media file
 *   GET  /api/media/{id}   — Download encrypted media file
 *
 * WebSocket:
 *   WS /ws                — Main real-time communication channel
 */
private fun Application.configureRoutes() {
    routing {
        // ── Health check ────────────────────────────────────────
        get("/health") {
            call.respond(mapOf(
                "status" to "ok",
                "connectedUsers" to ConnectionManager.getConnectedUsers().size,
                "timestamp" to System.currentTimeMillis()
            ))
        }

        // ── User registration ───────────────────────────────────
        post("/api/users") {
            val user = call.receive<User>()
            InMemoryStore.upsertUser(user.copy(
                lastSeen = System.currentTimeMillis()
            ))
            call.respond(user)
        }

        get("/api/users/{id}") {
            val userId = call.parameters["id"] ?: return@get call.respond(
                HttpStatusCode.BadRequest, "Missing user ID"
            )
            val user = InMemoryStore.getUser(userId)
            if (user != null) {
                call.respond(user)
            } else {
                call.respond(HttpStatusCode.NotFound, "User not found")
            }
        }

        // ── Media upload (encrypted file storage) ───────────────
        post("/api/media/upload") {
            val multipart = call.receiveMultipart()
            var assetId = ""
            var fileName = ""
            var contentType = ""
            var pairingId = ""
            var senderId = ""
            var encryptedThumbnail = ""

            multipart.forEachPart { part ->
                when (part) {
                    is PartData.FormItem -> {
                        when (part.name) {
                            "pairingId" -> pairingId = part.value
                            "senderId" -> senderId = part.value
                            "contentType" -> contentType = part.value
                            "encryptedThumbnail" -> encryptedThumbnail = part.value
                        }
                    }
                    is PartData.FileItem -> {
                        assetId = "media_${UUID.randomUUID()}"
                        fileName = part.originalFileName ?: "unknown"
                        // Save encrypted file to temp storage
                        val file = File(ServerConfig.MEDIA_STORAGE_PATH, assetId)
                        val bytes = part.streamProvider().readBytes()
                        file.writeBytes(bytes)

                        // Store metadata
                        val asset = MediaAsset(
                            assetId = assetId,
                            pairingId = pairingId,
                            senderId = senderId,
                            fileName = fileName,
                            contentType = contentType,
                            fileSize = bytes.size.toLong(),
                            encryptedThumbnailBase64 = encryptedThumbnail.ifEmpty { null },
                            serverDownloadUrl = "/api/media/$assetId"
                        )
                        InMemoryStore.storeMediaAsset(asset)
                    }
                    else -> {}
                }
                part.dispose()
            }

            call.respond(mapOf(
                "assetId" to assetId,
                "downloadUrl" to "/api/media/$assetId"
            ))
        }

        // ── Media download ──────────────────────────────────────
        get("/api/media/{assetId}") {
            val assetId = call.parameters["assetId"] ?: return@get call.respond(
                HttpStatusCode.BadRequest, "Missing asset ID"
            )
            val asset = InMemoryStore.getMediaAsset(assetId)
            if (asset == null || asset.expiresAt < System.currentTimeMillis()) {
                call.respond(HttpStatusCode.NotFound, "Media not found or expired")
                return@get
            }
            val file = File(ServerConfig.MEDIA_STORAGE_PATH, assetId)
            if (file.exists()) {
                call.respondFile(file)
            } else {
                call.respond(HttpStatusCode.NotFound, "Media file not found on disk")
            }
        }

        // ── WebSocket endpoint ──────────────────────────────────
        webSocket("/ws") {
            val handler = MessageHandler(this)
            try {
                for (frame in incoming) {
                    when (frame) {
                        is Frame.Text -> handler.handleFrame(frame)
                        is Frame.Close -> break
                        else -> {}
                    }
                }
            } finally {
                handler.handleDisconnect()
            }
        }
    }
}

/**
 * Periodic job to clean up expired media files (older than 24h).
 * Runs every hour on the server.
 */
private fun Application.startMediaCleanupJob() {
    launch {
        while (isActive) {
            delay(60 * 60 * 1000) // Every hour
            val cleaned = InMemoryStore.cleanupExpiredMedia()
            if (cleaned > 0) {
                println("Cleaned up $cleaned expired media assets")
            }
            // Also delete physical files
            val dir = File(ServerConfig.MEDIA_STORAGE_PATH)
            dir.listFiles()?.filter { it.lastModified() < System.currentTimeMillis() - 24 * 60 * 60 * 1000 }
                ?.forEach { it.delete() }
        }
    }
}
