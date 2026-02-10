# ──────────────────────────────────────────────────────────────
# Nku Sentinel — ProGuard / R8 Rules
# Audit Fix: F-3 (HIGH) — Enable code shrinking for release
# ──────────────────────────────────────────────────────────────

# ── SmolLM / llama.cpp JNI ──
# Keep JNI native methods and the SmolLM public API
-keep class io.shubham0204.smollm.** { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}

# ── Jetpack Compose ──
# Compose compiler generates synthetic classes; keep stability metadata
-dontwarn androidx.compose.**
-keep class androidx.compose.** { *; }
-keepclassmembers class * {
    @androidx.compose.runtime.Composable <methods>;
}

# ── MediaPipe ──
-keep class com.google.mediapipe.** { *; }
-dontwarn com.google.mediapipe.**

# ── ONNX Runtime (kept for future use) ──
-keep class ai.onnxruntime.** { *; }
-dontwarn ai.onnxruntime.**

# ── OkHttp (debug-only, but keep rules in case of debug builds) ──
-dontwarn okhttp3.**
-dontwarn okio.**

# ── Nku App ──
# Keep data classes used for serialization/reflection
-keep class com.nku.app.NkuResult { *; }
-keep class com.nku.app.ClinicalAssessment { *; }
-keep class com.nku.app.VitalSigns { *; }

# Keep enums
-keepclassmembers enum com.nku.app.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ── General ──
# Keep annotation metadata
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses

# Don't warn about missing optional dependencies
-dontwarn javax.annotation.**
-dontwarn kotlin.reflect.jvm.**

# ── AutoValue / MediaPipe annotation processor (not needed at runtime) ──
-dontwarn javax.lang.model.**
-dontwarn autovalue.shaded.**
-dontwarn com.google.auto.value.**

