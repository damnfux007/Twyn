/**
 * Twyn WebSocket Server — Node.js / Express + ws
 *
 * A lightweight server designed to run on Glitch.com (free tier).
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
const express = require('express');
const http = require('http');
const { WebSocketServer } = require('ws');
const cors = require('cors');
const multer = require('multer');
const path = require('path');
const fs = require('fs');
const { v4: uuid } = require('uuid');

const store = require('./store');
const conn = require('./connections');
const MessageHandler = require('./handler');

// ── Setup ──────────────────────────────────────────────────────

const app = express();
const server = http.createServer(app);
const wss = new WebSocketServer({ server });

const PORT = process.env.PORT || 3000;
const MEDIA_DIR = path.join(__dirname, 'media');

// Ensure media directory exists
if (!fs.existsSync(MEDIA_DIR)) fs.mkdirSync(MEDIA_DIR, { recursive: true });

// ── Middleware ──────────────────────────────────────────────────

app.use(cors());
app.use(express.json());
app.use(express.urlencoded({ extended: true }));

// ── HTTP Routes ────────────────────────────────────────────────

/**
 * Health check — used by Glitch and CI/CD to verify server is alive.
 */
app.get('/health', (req, res) => {
  res.json({
    status: 'ok',
    connectedUsers: conn.getConnectedUsers().length,
    timestamp: Date.now(),
    version: '1.0.0'
  });
});

/**
 * User registration — stores profile info for routing.
 */
app.post('/api/users', (req, res) => {
  const user = req.body;
  user.lastSeen = Date.now();
  store.upsertUser(user);
  res.json(user);
});

app.get('/api/users/:id', (req, res) => {
  const user = store.getUser(req.params.id);
  if (user) {
    res.json(user);
  } else {
    res.status(404).json({ error: 'User not found' });
  }
});

/**
 * Media upload — stores encrypted files temporarily (24h).
 * Actual file encrypted client-side before upload.
 */
const upload = multer({ dest: MEDIA_DIR, limits: { fileSize: 50 * 1024 * 1024 } });

app.post('/api/media/upload', upload.single('file'), (req, res) => {
  if (!req.file) return res.status(400).json({ error: 'No file uploaded' });

  const assetId = `media_${uuid()}`;
  const newPath = path.join(MEDIA_DIR, assetId);
  fs.renameSync(req.file.path, newPath);

  const asset = {
    assetId,
    pairingId: req.body.pairingId || '',
    senderId: req.body.senderId || '',
    fileName: req.file.originalname,
    contentType: req.body.contentType || req.file.mimetype,
    fileSize: req.file.size,
    encryptedThumbnailBase64: req.body.encryptedThumbnail || null,
    serverDownloadUrl: `/api/media/${assetId}`,
    createdAt: Date.now(),
    expiresAt: Date.now() + 24 * 60 * 60 * 1000
  };

  store.storeMediaAsset(asset);
  res.json({ assetId, downloadUrl: `/api/media/${assetId}` });
});

/**
 * Media download — serves encrypted files until 24h expiry.
 */
app.get('/api/media/:assetId', (req, res) => {
  const asset = store.getMediaAsset(req.params.assetId);
  if (!asset || asset.expiresAt < Date.now()) {
    return res.status(404).json({ error: 'Media not found or expired' });
  }
  const filePath = path.join(MEDIA_DIR, asset.assetId);
  if (fs.existsSync(filePath)) {
    res.sendFile(filePath);
  } else {
    res.status(404).json({ error: 'Media file not found on disk' });
  }
});

// ── WebSocket Server ───────────────────────────────────────────

/**
 * Main WebSocket endpoint — all real-time communication flows through here.
 * Each connected client gets a MessageHandler instance.
 */
wss.on('connection', (ws, req) => {
  const handler = new MessageHandler(ws);

  ws.on('message', (data) => {
    handler.handle(data.toString());
  });

  ws.on('close', () => {
    handler.handleDisconnect();
  });

  ws.on('error', (err) => {
    console.error('WebSocket error:', err.message);
    handler.handleDisconnect();
  });
});

// ── Media Cleanup Job ──────────────────────────────────────────

/**
 * Periodic cleanup of expired media files (older than 24h).
 * Runs every hour.
 */
setInterval(() => {
  const cleaned = store.cleanupExpiredMedia();
  if (cleaned > 0) {
    console.log(`Cleaned up ${cleaned} expired media assets`);
  }

  // Delete physical files older than 24h
  const cutoff = Date.now() - 24 * 60 * 60 * 1000;
  if (fs.existsSync(MEDIA_DIR)) {
    fs.readdirSync(MEDIA_DIR).forEach((file) => {
      const filePath = path.join(MEDIA_DIR, file);
      const stat = fs.statSync(filePath);
      if (stat.mtimeMs < cutoff) {
        fs.unlinkSync(filePath);
      }
    });
  }
}, 60 * 60 * 1000);

// ── Start Server ───────────────────────────────────────────────

server.listen(PORT, () => {
  console.log(`Twyn server running on port ${PORT}`);
  console.log(`WebSocket: ws://localhost:${PORT}/ws`);
  console.log(`Health: http://localhost:${PORT}/health`);
});
