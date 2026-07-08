# Notova release R8 rules.
# Minification + resource shrinking are enabled for release builds; these rules keep the
# reflection- and JNI-dependent surfaces of the on-device AI engines and the networking layer.

# ---- On-device AI engines (JNI / native, loaded reflectively) ----
-keep class com.google.ai.edge.litertlm.** { *; }
-keep class com.google.mediapipe.** { *; }
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.ai.edge.litertlm.**
-dontwarn com.google.mediapipe.**
-dontwarn com.google.mlkit.**
# Keep all native method holders (JNI binds by name).
-keepclasseswithmembernames class * {
    native <methods>;
}

# ---- kotlinx.serialization (used by the :integrations DTO layer) ----
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.notova.**$$serializer { *; }
-keepclassmembers class com.notova.** {
    *** Companion;
    *** serializer(...);
}

# ---- Retrofit / OkHttp ----
-keepattributes Signature, Exceptions
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**

# ---- Coroutines ----
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }
-dontwarn kotlinx.coroutines.**

# Hilt and Room ship their own consumer rules; nothing extra is required here.
