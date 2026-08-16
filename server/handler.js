/**
 * Twyn WebSocket Message Handler
 *
 * Processes all incoming WebSocket messages and routes them by type.
 *
 * Protocol flow:
 * 1. Client connects and sends AUTH with their userId
 * 2. Server registers the connection and confirms with AUTH_OK
 * 3. Client can now send/receive messages for their pairings
 * 4. All message content is ciphertext — server never decrypts
 */
const store = require('./store');
const conn = require('./connections');
const { v4: uuid } = require('uuid');

class MessageHandler {
  constructor(ws) {
    this.ws = ws;
    this.authenticatedUserId = null;
  }

  /**
   * Handle an incoming text message from the client.
   */
  handle(raw) {
    let msg;
    try {
      msg = JSON.parse(raw);
    } catch (e) {
      return this.sendError('Invalid JSON');
    }

    switch (msg.type) {
      case 'AUTH':                  return this.handleAuth(msg);
      case 'SEND_MESSAGE':         return this.handleSendMessage(msg);
      case 'COMPLETE_PAIRING':     return this.handleCompletePairing(msg);
      case 'UPLOAD_MEDIA':         return this.handleUploadMedia(msg);
      case 'DOWNLOAD_MEDIA':       return this.handleDownloadMedia(msg);
      case 'UPDATE_PROFILE':       return this.handleUpdateProfile(msg);
      case 'LOCATION_REQUEST':     return this.handleLocationRequest(msg);
      case 'LOCATION_RESPONSE':    return this.handleLocationResponse(msg);
      case 'TYPING_START':
      case 'TYPING_STOP':          return this.handleTyping(msg);
      case 'MESSAGE_READ':         return this.handleMessageRead(msg);
      case 'CALL_OFFER':
      case 'CALL_ANSWER':
      case 'CALL_CANDIDATE':
      case 'CALL_HANGUP':
      case 'CALL_REJECTED':        return this.handleCallSignaling(msg);
      case 'PING':                 return this.send({ type: 'PONG', payload: '', timestamp: Date.now() });
      default:                     return this.sendError(`Unhandled type: ${msg.type}`);
    }
  }

  handleAuth(msg) {
    const userId = msg.payload;
    this.authenticatedUserId = userId;
    conn.register(userId, this.ws);

    const user = store.getUser(userId);
    if (user) {
      store.upsertUser({ ...user, isOnline: true });
    }

    // Deliver pending offline messages
    const pairings = store.getPairingsForUser(userId);
    for (const pairing of pairings) {
      const pending = store.drainPendingMessages(pairing.pairingId);
      for (const m of pending) {
        if (m.senderId !== userId) {
          conn.sendTo(userId, { type: 'INCOMING_MESSAGE', payload: JSON.stringify(m), timestamp: Date.now() });
        }
      }
    }

    this.send({ type: 'AUTH_OK', payload: 'ok', timestamp: Date.now() });
  }

  handleSendMessage(msg) {
    if (!this.authenticatedUserId) return this.sendError('Not authenticated');

    let encryptedMsg;
    try {
      encryptedMsg = JSON.parse(msg.payload);
    } catch (e) {
      return this.sendError('Invalid message format');
    }

    const pairing = store.getPairing(encryptedMsg.pairingId);
    if (!pairing || (pairing.userAId !== this.authenticatedUserId && pairing.userBId !== this.authenticatedUserId)) {
      return this.sendError('Not authorized for this pairing');
    }

    const delivered = conn.sendToPairedUser(
      encryptedMsg.pairingId,
      this.authenticatedUserId,
      { type: 'INCOMING_MESSAGE', payload: JSON.stringify(encryptedMsg), timestamp: Date.now() }
    );

    if (delivered) {
      this.send({ type: 'MESSAGE_DELIVERED', payload: encryptedMsg.messageId, timestamp: Date.now() });
    } else {
      store.queueMessage(encryptedMsg);
      this.send({ type: 'MESSAGE_DELIVERED', payload: `${encryptedMsg.messageId}:queued`, timestamp: Date.now() });
    }
  }

  handleCompletePairing(msg) {
    if (!this.authenticatedUserId) return this.sendError('Not authenticated');

    let data;
    try {
      data = JSON.parse(msg.payload);
    } catch (e) {
      return this.sendError('Invalid pairing data');
    }

    const creatorId = data.partnerId;
    if (creatorId === this.authenticatedUserId) {
      return this.sendError('Cannot pair with yourself');
    }

    const existing = store.findPairingBetween(this.authenticatedUserId, creatorId);
    if (existing) {
      this.send({ type: 'PAIRING_EXISTS', payload: existing.pairingId, timestamp: Date.now() });
      return;
    }

    const pairing = store.createPairing(creatorId, this.authenticatedUserId);
    const pairingJson = JSON.stringify(pairing);

    conn.sendTo(creatorId, { type: 'PAIRING_COMPLETE', payload: pairingJson, timestamp: Date.now() });
    conn.sendTo(this.authenticatedUserId, { type: 'PAIRING_COMPLETE', payload: pairingJson, timestamp: Date.now() });
  }

  handleUploadMedia(msg) {
    if (!this.authenticatedUserId) return this.sendError('Not authenticated');

    let asset;
    try {
      asset = JSON.parse(msg.payload);
    } catch (e) {
      return this.sendError('Invalid media asset');
    }

    store.storeMediaAsset(asset);

    conn.sendToPairedUser(
      asset.pairingId,
      this.authenticatedUserId,
      { type: 'MEDIA_UPLOADED', payload: JSON.stringify(asset), timestamp: Date.now() }
    );
  }

  handleDownloadMedia(msg) {
    const assetId = msg.payload;
    const asset = store.getMediaAsset(assetId);
    if (asset) {
      this.send({ type: 'MEDIA_URL', payload: JSON.stringify(asset), timestamp: Date.now() });
    } else {
      this.sendError('Media asset not found or expired');
    }
  }

  handleUpdateProfile(msg) {
    if (!this.authenticatedUserId) return this.sendError('Not authenticated');

    let update;
    try {
      update = JSON.parse(msg.payload);
    } catch (e) {
      return this.sendError('Invalid profile update');
    }

    const existing = store.getUser(this.authenticatedUserId);
    if (!existing) return this.sendError('User not found');

    const updated = store.upsertUser({
      ...existing,
      displayName: update.displayName || existing.displayName,
      bio: update.bio || existing.bio,
      profilePhotoUrl: update.profilePhotoUrl || existing.profilePhotoUrl,
      showOnlineStatus: update.showOnlineStatus !== undefined ? update.showOnlineStatus : existing.showOnlineStatus
    });

    this.send({ type: 'PROFILE_UPDATED', payload: JSON.stringify(updated), timestamp: Date.now() });
  }

  handleLocationRequest(msg) {
    if (!this.authenticatedUserId) return this.sendError('Not authenticated');

    let data;
    try {
      data = JSON.parse(msg.payload);
    } catch (e) {
      return this.sendError('Invalid location request');
    }

    conn.sendToPairedUser(
      data.pairingId,
      this.authenticatedUserId,
      { type: 'LOCATION_REQUEST', payload: JSON.stringify({ requesterId: this.authenticatedUserId }), timestamp: Date.now() }
    );
  }

  handleLocationResponse(msg) {
    if (!this.authenticatedUserId) return this.sendError('Not authenticated');

    const pairings = store.getPairingsForUser(this.authenticatedUserId);
    for (const pairing of pairings) {
      conn.sendToPairedUser(
        pairing.pairingId,
        this.authenticatedUserId,
        { type: 'LOCATION_RESPONSE', payload: msg.payload, timestamp: Date.now() }
      );
    }
  }

  handleTyping(msg) {
    if (!this.authenticatedUserId) return this.sendError('Not authenticated');
    const pairingId = msg.payload;
    conn.sendToPairedUser(pairingId, this.authenticatedUserId, { type: msg.type, payload: this.authenticatedUserId, timestamp: Date.now() });
  }

  handleMessageRead(msg) {
    if (!this.authenticatedUserId) return this.sendError('Not authenticated');
    const pairingId = msg.payload;
    conn.sendToPairedUser(pairingId, this.authenticatedUserId, { type: 'MESSAGE_READ', payload: this.authenticatedUserId, timestamp: Date.now() });
  }

  handleCallSignaling(msg) {
    if (!this.authenticatedUserId) return this.sendError('Not authenticated');

    let data;
    try {
      data = JSON.parse(msg.payload);
    } catch (e) {
      return this.sendError('Invalid call signaling data');
    }

    conn.sendToPairedUser(data.pairingId, this.authenticatedUserId, { type: msg.type, payload: msg.payload, timestamp: Date.now() });
  }

  handleDisconnect() {
    if (!this.authenticatedUserId) return;
    conn.unregister(this.authenticatedUserId);
    const user = store.getUser(this.authenticatedUserId);
    if (user) {
      store.upsertUser({ ...user, isOnline: false, lastSeen: Date.now() });
    }
  }

  send(message) {
    try {
      this.ws.send(JSON.stringify(message));
    } catch (e) { /* ignore */ }
  }

  sendError(error) {
    this.send({ type: 'AUTH_FAIL', payload: error, timestamp: Date.now() });
  }
}

module.exports = MessageHandler;
