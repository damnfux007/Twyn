#!/bin/bash
# ─────────────────────────────────────────────────────────────
# Twyn Oracle Cloud Setup Script
# Run this once on a fresh Oracle Cloud Always Free VM
# to prepare it for hosting the Twyn WebSocket server.
#
# Prerequisites:
# - Oracle Cloud Always Free VM (Ubuntu 22.04 or similar)
# - SSH key-based access
# - Ports 8080 (HTTP) and 22 (SSH) open in security list
# ─────────────────────────────────────────────────────────────

set -euo pipefail

echo "🔧 Twyn Server — Oracle Cloud Setup"
echo "======================================"

# ── System Updates ────────────────────────────────────────────
echo ""
echo "📦 Updating system packages..."
sudo apt-get update -y
sudo apt-get upgrade -y

# ── Install Java 17 ──────────────────────────────────────────
echo ""
echo "☕ Installing OpenJDK 17..."
sudo apt-get install -y openjdk-17-jdk
java -version

# ── Create Twyn directories ──────────────────────────────────
echo ""
echo "📁 Creating directories..."
sudo mkdir -p /opt/twyn-server
sudo mkdir -p /tmp/twyn-media
sudo mkdir -p /var/log/twyn

# ── Set up firewall ──────────────────────────────────────────
echo ""
echo "🔥 Configuring firewall..."
sudo ufw allow 22/tcp    # SSH
sudo ufw allow 8080/tcp  # Twyn server
sudo ufw --force enable

# ── Create systemd service ───────────────────────────────────
echo ""
echo "⚙️  Creating systemd service..."

sudo tee /etc/systemd/system/twyn-server.service > /dev/null <<EOF
[Unit]
Description=Twyn WebSocket Server
After=network.target

[Service]
Type=simple
User=$USER
WorkingDirectory=/opt/twyn-server
ExecStart=/usr/bin/java -Xms64m -Xmx256m -jar /opt/twyn-server/twyn-server.jar
Restart=always
RestartSec=10
StandardOutput=journal
StandardError=journal

# Security hardening
NoNewPrivileges=true
ProtectSystem=strict
ReadWritePaths=/tmp/twyn-media /opt/twyn-server

[Install]
WantedBy=multi-user.target
EOF

sudo systemctl daemon-reload
sudo systemctl enable twyn-server

# ── Set up log rotation ──────────────────────────────────────
echo ""
echo "📝 Setting up log rotation..."
sudo tee /etc/logrotate.d/twyn-server > /dev/null <<EOF
/var/log/twyn-server.log {
    daily
    rotate 7
    compress
    delaycompress
    missingok
    notifempty
}
EOF

# ── Get server IP ────────────────────────────────────────────
echo ""
SERVER_IP=$(curl -s ifconfig.me || echo "UNKNOWN")
echo "======================================"
echo "✅ Twyn server setup complete!"
echo ""
echo "Server IP: $SERVER_IP"
echo "Server port: 8080"
echo "WebSocket URL: ws://${SERVER_IP}:8080/ws"
echo ""
echo "Next steps:"
echo "  1. Upload the server JAR to /opt/twyn-server/"
echo "  2. Run: sudo systemctl start twyn-server"
echo "  3. Verify: curl http://localhost:8080/health"
echo ""
echo "Or use the GitHub Actions workflow — it handles all of this automatically."
echo "======================================"
