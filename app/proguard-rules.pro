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

# Tink 引用了但未使用的可选依赖（Google HTTP Client、Joda、等）
-dontwarn com.google.api.client.**
-dontwarn com.google.api.services.**
-dontwarn javax.lang.model.**
-dontwarn org.joda.time.**
-dontwarn com.google.gson.**

# 让 R8 继续编译即使有缺失引用（仅限这些类实际未被调用的情况）
-ignorewarnings
