#!/bin/bash
# ─────────────────────────────────────────────────────────────
# Twyn Server — Manual Deploy Script
# For deploying the server JAR to Oracle Cloud manually.
#
# Usage:
#   ./deploy-server.sh <oracle-cloud-ip> <ssh-key-path>
#
# Example:
#   ./deploy-server.sh 129.153.xx.xx ~/.ssh/oracle_cloud_key
# ─────────────────────────────────────────────────────────────

set -euo pipefail

ORACLE_HOST="${1:?Usage: $0 <oracle-cloud-ip> <ssh-key-path>}"
SSH_KEY="${2:?Usage: $0 <oracle-cloud-ip> <ssh-key-path>}"
SSH_USER="ubuntu"

echo "🚀 Deploying Twyn server to ${ORACLE_HOST}..."

# ── Build the server ─────────────────────────────────────────
echo ""
echo "📦 Building server JAR..."
cd "$(dirname "$0")/../server"
gradle shadowJar --no-daemon
JAR_FILE=$(ls build/libs/*.jar | head -1)
echo "Built: $JAR_FILE"

# ── Upload to Oracle Cloud ───────────────────────────────────
echo ""
echo "📤 Uploading to Oracle Cloud..."
scp -i "$SSH_KEY" "$JAR_FILE" "${SSH_USER}@${ORACLE_HOST}:/tmp/twyn-server.jar"

# ── Deploy on remote ─────────────────────────────────────────
echo ""
echo "🔧 Deploying on server..."
ssh -i "$SSH_KEY" "${SSH_USER}@${ORACLE_HOST}" << 'REMOTE_SCRIPT'
  # Stop existing server
  sudo systemctl stop twyn-server 2>/dev/null || true

  # Backup old JAR
  if [ -f /opt/twyn-server/twyn-server.jar ]; then
    mv /opt/twyn-server/twyn-server.jar /opt/twyn-server/twyn-server.jar.bak
  fi

  # Move new JAR
  mv /tmp/twyn-server.jar /opt/twyn-server/twyn-server.jar
  chmod +x /opt/twyn-server/twyn-server.jar

  # Clean media cache
  rm -rf /tmp/twyn-media/*
  mkdir -p /tmp/twyn-media

  # Start server
  sudo systemctl start twyn-server
  sleep 3

  # Health check
  if curl -sf http://localhost:8080/health > /dev/null; then
    echo "✅ Server is healthy!"
    curl -s http://localhost:8080/health
  else
    echo "❌ Health check failed. Logs:"
    sudo journalctl -u twyn-server --no-pager -n 20
    exit 1
  fi
REMOTE_SCRIPT

echo ""
echo "======================================"
echo "✅ Deployment complete!"
echo "Server: http://${ORACLE_HOST}:8080"
echo "WebSocket: ws://${ORACLE_HOST}:8080/ws"
echo "======================================"
