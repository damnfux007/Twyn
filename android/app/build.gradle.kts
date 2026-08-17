plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.twyn.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.twyn.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.5"
    }

    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }
}

dependencies {
    // ── Core Android ───────────────────────────────────────────────
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.6.2")
    implementation("androidx.activity:activity-compose:1.8.2")

    // ── Jetpack Compose BOM ────────────────────────────────────────
    val composeBom = platform("androidx.compose:compose-bom:2023.10.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.foundation:foundation")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // ── Navigation Compose ─────────────────────────────────────────
    implementation("androidx.navigation:navigation-compose:2.7.6")

    // ── Hilt (Dependency Injection) ────────────────────────────────
    implementation("com.google.dagger:hilt-android:2.48.1")
    ksp("com.google.dagger:hilt-compiler:2.48.1")
    implementation("androidx.hilt:hilt-navigation-compose:1.1.0")

    // ── Room (Local Database) ──────────────────────────────────────
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    // ── DataStore (Preferences) ────────────────────────────────────
    implementation("androidx.datastore:datastore-preferences:1.0.0")

    // ── Kotlin Serialization ───────────────────────────────────────
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")

    // ── Networking ─────────────────────────────────────────────────
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // ── Signal Protocol (E2E Encryption) ───────────────────────────
    // TODO: Re-integrate libsignal when API is stable; using AES placeholder for now
    // implementation("org.signal:libsignal-client:0.36.0")

    // ── CameraX (QR code scanning) ─────────────────────────────────
    val cameraxVersion = "1.3.1"
    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")

    // ── ML Kit (QR code detection) ─────────────────────────────────
    implementation("com.google.mlkit:barcode-scanning:17.2.0")

    // ── ZXing (QR code generation) ─────────────────────────────────
    implementation("com.github.alexzhirkevich:custom-qr-generator:1.6.2")
    implementation("com.google.zxing:core:3.5.3")

    // ── Accompanist (Permissions) ──────────────────────────────────
    implementation("com.google.accompanist:accompanist-permissions:0.32.0")

    // ── WebRTC (Voice/Video Calling) ───────────────────────────────
    // TODO: Add WebRTC AAR manually or from correct Maven coordinates
    // WebRTC signaling uses OkHttp WebSocket (already included above)

    // ── Location Services ──────────────────────────────────────────
    implementation("com.google.android.gms:play-services-location:21.0.1")

    // ── Maps (Location Display) ────────────────────────────────────
    // Using OpenStreetMap via osmdroid (free, no API key needed)
    implementation("org.osmdroid:osmdroid-android:6.1.18")

    // ── Google Drive API (for permanent media storage) ─────────────
    // TODO: Add back when we confirm correct artifact coordinates
    // implementation("com.google.api-client:google-api-client-android:2.2.0")
    // implementation("com.google.apis:google-api-services-drive:v3-rev20231127-2.0.0")
    // implementation("com.google.auth:google-auth-library-oauth2-http:1.20.0")

    // ── Google Sign-In (for Drive auth) ────────────────────────────
    implementation("com.google.android.gms:play-services-auth:20.7.0")

    // ── Coil (Image Loading in Compose) ────────────────────────────
    implementation("io.coil-kt:coil-compose:2.5.0")

    // ── MediaCodec helpers ─────────────────────────────────────────
    implementation("androidx.media3:media3-exoplayer:1.2.0")

    // ── Coroutines ─────────────────────────────────────────────────
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // ── Splash Screen ──────────────────────────────────────────────
    implementation("androidx.core:core-splashscreen:1.0.1")
}
