const { v4: uuid } = require('uuid');

class InMemoryStore {
  constructor() {
    this.users = new Map();
    this.pairings = new Map();
    this.userPairings = new Map();
    this.pendingMessages = new Map();
    this.mediaAssets = new Map();
  }

  upsertUser(user) {
    user.lastSeen = Date.now();
    this.users.set(user.userId, user);
    return user;
  }

  getUser(userId) {
    return this.users.get(userId) || null;
  }

  createPairing(userAId, userBId, clientPairingId) {
    const pairingId = clientPairingId || `pair_${uuid()}`;
    const pairing = { pairingId, userAId, userBId, createdAt: Date.now() };
    this.pairings.set(pairingId, pairing);

    if (!this.userPairings.has(userAId)) this.userPairings.set(userAId, new Set());
    if (!this.userPairings.has(userBId)) this.userPairings.set(userBId, new Set());
    this.userPairings.get(userAId).add(pairingId);
    this.userPairings.get(userBId).add(pairingId);

    console.log(`Pairing created: ${pairingId} between ${userAId} and ${userBId}`);
    return pairing;
  }

  findPairingBetween(userIdA, userIdB) {
    for (const p of this.pairings.values()) {
      if ((p.userAId === userIdA && p.userBId === userIdB) ||
          (p.userAId === userIdB && p.userBId === userIdA)) {
        return p;
      }
    }
    return null;
  }

  getPairingsForUser(userId) {
    const ids = this.userPairings.get(userId);
    if (!ids) return [];
    return [...ids].map(id => this.pairings.get(id)).filter(Boolean);
  }

  getPairing(pairingId) {
    return this.pairings.get(pairingId) || null;
  }

  getPairedUser(pairingId, myUserId) {
    const p = this.pairings.get(pairingId);
    if (!p) return null;
    return p.userAId === myUserId ? p.userBId : p.userAId;
  }

  queueMessage(message) {
    if (!this.pendingMessages.has(message.pairingId)) {
      this.pendingMessages.set(message.pairingId, []);
    }
    this.pendingMessages.get(message.pairingId).push(message);
  }

  drainPendingMessages(pairingId) {
    const messages = this.pendingMessages.get(pairingId) || [];
    this.pendingMessages.delete(pairingId);
    return messages;
  }

  storeMediaAsset(asset) {
    this.mediaAssets.set(asset.assetId, asset);
  }

  getMediaAsset(assetId) {
    return this.mediaAssets.get(assetId) || null;
  }

  cleanupExpiredMedia() {
    const now = Date.now();
    let cleaned = 0;
    for (const [id, asset] of this.mediaAssets) {
      if (asset.expiresAt < now) {
        this.mediaAssets.delete(id);
        cleaned++;
      }
    }
    return cleaned;
  }
}

module.exports = new InMemoryStore();
