# ProGuard rules for Nexo

# Keep Kotlin metadata
-keep class kotlin.Metadata { *; }

# Keep Kotlinx serialization
-keep class kotlinx.serialization.** { *; }
-keepclassmembers class * {
    @kotlinx.serialization.KSerializer *;
}

# Keep OkHttp
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn okhttp3.**

# Keep WebView related
-keep class android.webkit.** { *; }

# Keep our app classes
-keep class dev.nexo.app.** { *; }

# Keep Proot native libs
-keep class * {
    native <methods>;
}

# Keep EncryptedSharedPreferences
-keep class androidx.security.crypto.** { *; }

# Keep Compose
-keep class androidx.compose.** { *; }