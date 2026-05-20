# 关闭 R8 混淆（obfuscation），保留代码压缩和资源删除
-dontobfuscate

# ============================================================
# Project-specific ProGuard / R8 rules
# ============================================================

# ============================================================
# Jetpack Compose
# ============================================================
-keep class androidx.compose.** { *; }
-keepclassmembers class * implements androidx.compose.runtime.Composable { *; }
-dontwarn androidx.compose.**

# ============================================================
# Kotlin Coroutines
# ============================================================
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
-dontwarn kotlinx.coroutines.**

# ============================================================
# OkHttp
# ============================================================
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# ============================================================
# Gson
# ============================================================
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Keep ALL Gson data model classes (prevent R8 from renaming class names)
# Gson uses reflection to instantiate classes by name - renaming breaks it
-keep class edu.cqwu.electricity.data.model.** { *; }
-keep class edu.cqwu.electricity.data.network.** { *; }

# ============================================================
# Coil
# ============================================================
-keep class coil.** { *; }
-dontwarn coil.**

# ============================================================
# Zxing (QR Code scanning)
# ============================================================
-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**

# ============================================================
# MaterialKolor (HCT color science)
# ============================================================
-keep class com.materialkolor.** { *; }
-dontwarn com.materialkolor.**

# ============================================================
# Navigation Compose
# ============================================================
-keep class androidx.navigation.** { *; }
-dontwarn androidx.navigation.**

# ============================================================
# Standard Android rules
# ============================================================
# Keep the line number information for debugging stack traces
-keepattributes SourceFile,LineNumberTable
# Hide the original source file name (optional)
#-renamesourcefileattribute SourceFile