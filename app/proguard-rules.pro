# Proguard / R8 rules for JapaneseASMR Release Build

# Native JNI methods (Oboe, UsbAudioEngine, NativeAudioPlayer)
-keepclasseswithmembernames class * {
    native <methods>;
}
-keep class com.saymaven.downloader.japaneseasmr.service.NativeAudioPlayer { *; }
-keep class com.saymaven.downloader.japaneseasmr.service.usb.** { *; }
-keep class com.google.oboe.** { *; }

# Jaudiotagger (package is org.jaudiotagger.*, references desktop java.awt/javax.imageio)
-dontwarn net.jthink.jaudiotagger.**
-keep class net.jthink.jaudiotagger.** { *; }
-dontwarn org.jaudiotagger.**
-keep class org.jaudiotagger.** { *; }
-dontwarn java.awt.**
-dontwarn javax.imageio.**

# Models, Remote DTOs & Room Entities
-keep class com.saymaven.downloader.japaneseasmr.data.model.** { *; }
-keep class com.saymaven.downloader.japaneseasmr.data.local.entity.** { *; }
-keep class com.saymaven.downloader.japaneseasmr.data.remote.** { *; }

# Media3 ExoPlayer
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# OkHttp, Jsoup & Coil
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.jsoup.**
-dontwarn coil.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Coroutines
-dontwarn kotlinx.coroutines.**
