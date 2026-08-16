# Twyn Server (Node.js for Glitch.com)

A lightweight WebSocket server that routes encrypted messages between paired users. Runs on Glitch.com's free tier — no credit card needed.

## What the server does
- Routes encrypted messages between paired users (never sees plaintext)
- Queues messages for offline delivery, deletes after delivery
- Manages temporary media storage (24h expiry)
- Relays WebRTC signaling (offer/answer/ICE candidates)
- Handles pairing completion

## Run locally
```bash
cd server
npm install
npm start
# Server runs at http://localhost:3000
```

## Deploy to Glitch
1. Go to https://glitch.com
2. Click "New Project" → "Import from GitHub"
3. Select `damnfux007/Twyn`
4. Glitch runs `npm start` automatically
5. Your server URL: `https://your-project-name.glitch.me`
6. WebSocket URL: `wss://your-project-name.glitch.me/ws`

## Endpoints
- `GET /health` — Health check
- `WS /ws` — WebSocket (all real-time communication)
- `POST /api/media/upload` — Upload encrypted media
- `GET /api/media/:id` — Download encrypted media
