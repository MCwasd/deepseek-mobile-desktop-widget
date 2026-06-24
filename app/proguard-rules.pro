# ProGuard / R8 rules for DeepSeek Widget
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keepattributes InnerClasses

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# Gson
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# App Widget (keep all classes used by the widget)
-keep class com.tiramisu.deepseekwidget.** { *; }
-keepclassmembers class com.tiramisu.deepseekwidget.** { *; }

# Security / Tink (EncryptedSharedPreferences)
-keep class com.google.crypto.tink.** { *; }
-keep class com.google.errorprone.** { *; }
