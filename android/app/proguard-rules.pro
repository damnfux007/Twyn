# ── Signal Protocol (libsignal) ──────────────────────────────────
-keep class org.signal.libsignal.** { *; }
-keep class org.signal.storageservice.** { *; }
-dontwarn org.signal.libsignal.**

# ── WebRTC ───────────────────────────────────────────────────────
-keep class org.webrtc.** { *; }
-dontwarn org.webrtc.**

# ── Google Drive API ─────────────────────────────────────────────
-keep class com.google.api.services.drive.** { *; }
-keep class com.google.api.client.** { *; }

# ── Kotlinx Serialization ────────────────────────────────────────
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
