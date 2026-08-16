/**
 * Twyn WebSocket Connection Manager
 *
 * Manages all active WebSocket sessions.
 * Routes messages to the correct recipient based on pairing membership.
 * Each connected user has exactly one WebSocket session.
 */
const store = require('./store');

class ConnectionManager {
  constructor() {
    this.sessions = new Map(); // userId -> ws
  }

  register(userId, ws) {
    this.sessions.set(userId, ws);
  }

  unregister(userId) {
    this.sessions.delete(userId);
  }

  isConnected(userId) {
    return this.sessions.has(userId);
  }

  getSession(userId) {
    return this.sessions.get(userId) || null;
  }

  getConnectedUsers() {
    return [...this.sessions.keys()];
  }

  /**
   * Send a typed message to a specific user.
   * Returns false if the user is not connected.
   */
  sendTo(userId, message) {
    const ws = this.sessions.get(userId);
    if (!ws || ws.readyState !== 1) {
      // Connection is dead, clean it up
      this.sessions.delete(userId);
      return false;
    }
    try {
      ws.send(JSON.stringify(message));
      return true;
    } catch (e) {
      this.sessions.delete(userId);
      return false;
    }
  }

  /**
   * Send a message to the other person in a pairing.
   * Primary routing method for all 1-on-1 communication.
   */
  sendToPairedUser(pairingId, senderId, message) {
    const pairedUserId = store.getPairedUser(pairingId, senderId);
    if (!pairedUserId) return false;
    return this.sendTo(pairedUserId, message);
  }

  /**
   * Broadcast to all connected users (for presence updates).
   */
  broadcast(message, excludeUserId = null) {
    const data = JSON.stringify(message);
    for (const [userId, ws] of this.sessions) {
      if (userId !== excludeUserId && ws.readyState === 1) {
        try { ws.send(data); } catch (e) { /* ignore */ }
      }
    }
  }
}

module.exports = new ConnectionManager();
