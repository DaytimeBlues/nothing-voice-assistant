# ProGuard rules for Nothing Voice Assistant

# Keep VoiceInteractionService
-keep class com.nothing.voiceassistant.NothingAssistantService { *; }
-keep class com.nothing.voiceassistant.NothingAssistantSessionService { *; }
-keep class com.nothing.voiceassistant.NothingAssistantSession { *; }

# Keep Room entities
-keep class com.nothing.voiceassistant.data.Recording { *; }

# Google API Client
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.api.** { *; }
-keep class com.google.auth.** { *; }
-dontwarn com.google.api.client.extensions.android.**
-dontwarn com.google.api.client.googleapis.extensions.android.**

# gRPC for Speech-to-Text
-keep class io.grpc.** { *; }
-dontwarn io.grpc.**

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
