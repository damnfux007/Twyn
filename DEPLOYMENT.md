# Twyn — GitHub & Oracle Cloud Deployment Guide

## Quick Start

### 1. Fork / Clone the Repository
```bash
git clone https://github.com/YOUR_USERNAME/Twyn.git
cd Twyn
```

### 2. Oracle Cloud Always Free VM Setup
1. Create an Always Free VM instance on [cloud.oracle.com](https://cloud.oracle.com)
2. Choose **Ubuntu 22.04** as the image
3. Open port **8080** in your security list (Networking → Virtual Cloud Networks → Security Lists)
4. SSH into the VM and run:
```bash
curl -sL https://raw.githubusercontent.com/YOUR_USERNAME/Twyn/main/deploy/setup-oracle-cloud.sh | bash
```

### 3. Configure GitHub Secrets
Go to your GitHub repo → Settings → Secrets and variables → Actions, and add:

| Secret | Description |
|--------|-------------|
| `ORACLE_HOST` | Your Oracle Cloud VM public IP |
| `ORACLE_USER` | SSH username (usually `ubuntu`) |
| `ORACLE_SSH_KEY` | Private SSH key for the VM |
| `ANDROID_KEYSTORE_BASE64` | Base64-encoded release keystore (for APK signing) |
| `KEYSTORE_PASSWORD` | Keystore password |
| `KEY_ALIAS` | Key alias in the keystore |
| `KEY_PASSWORD` | Key password |

### 4. Deploy Server via GitHub
Push to `main` branch with changes in `server/`:
```bash
git add server/
git commit -m "Update server"
git push origin main
```
The `deploy-server.yml` workflow automatically:
- Builds the server JAR
- Uploads it to your Oracle Cloud VM
- Creates/restarts the systemd service
- Runs health check

### 5. Build Android APK via GitHub
Create a version tag to trigger the build:
```bash
git tag v1.0.0
git push origin v1.0.0
```
The `build-android.yml` workflow automatically:
- Builds a signed release APK
- Creates a GitHub Release with the APK attached
- You can download the APK from the Releases page

### 6. Install the App
1. Go to your repo's Releases page
2. Download the latest `twyn-v*.apk`
3. Transfer to your Android device
4. Enable "Install from unknown sources" if prompted
5. Open the APK to install
6. Enter your server URL: `ws://YOUR_ORACLE_IP:8080/ws`

---

## Architecture

```
┌─────────────┐     WebSocket     ┌──────────────────┐
│  Android     │ ◄──────────────► │  Oracle Cloud     │
│  App (APK)   │     (encrypted)  │  Always Free VM   │
│              │                   │  (Ktor Server)    │
│  - Signal    │                   │                   │
│    Protocol  │                   │  - Routes msgs    │
│  - WebRTC    │                   │  - Queues offline │
│  - Compose   │                   │  - Temp storage   │
└─────────────┘                   └──────────────────┘
       │                                  │
       ▼                                  ▼
  Google Drive                    Media cache
  (permanent                      (24h expiry)
   encrypted
   backup)
```

## CI/CD Pipeline

```
Push to main (server/)     Tag v*.*.*
        │                        │
        ▼                        ▼
┌─────────────────┐    ┌─────────────────┐
│ deploy-server    │    │ build-android    │
│ workflow         │    │ workflow         │
│                  │    │                  │
│ 1. Gradle build  │    │ 1. Gradle build  │
│ 2. SCP to Oracle │    │ 2. Sign APK      │
│ 3. Restart svc   │    │ 3. GitHub Release│
│ 4. Health check  │    │ 4. Upload APK    │
└─────────────────┘    └─────────────────┘
        │                        │
        ▼                        ▼
  Server running            APK in Releases
  on Oracle Cloud           ready to install
```

## Manual Deployment (without GitHub Actions)

```bash
# Deploy server
./deploy/deploy-server.sh 129.153.xx.xx ~/.ssh/oracle_key

# Build APK locally
cd android
./gradlew assembleRelease
# APK at: app/build/outputs/apk/release/app-release.apk
```

## Server Endpoints
- `GET /health` — Health check
- `WS /ws` — WebSocket (all real-time communication)
- `POST /api/media/upload` — Upload encrypted media
- `GET /api/media/{id}` — Download encrypted media

## Cost
- **Oracle Cloud Always Free**: 4 OCPU, 24 GB RAM, 200 GB storage — unlimited time
- **Google Drive API**: Free tier — 100 GB storage, 20k queries/day
- **GitHub Actions**: 2,000 minutes/month free
- **Total cost: $0**
