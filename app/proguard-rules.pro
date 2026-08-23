# Proguard rules for JapaneseASMR Release Build

# Jaudiotagger
-dontwarn net.jthink.jaudiotagger.**
-keep class net.jthink.jaudiotagger.** { *; }

# Models & Entities
-keep class com.saymaven.downloader.japaneseasmr.data.model.** { *; }
-keep class com.saymaven.downloader.japaneseasmr.data.local.entity.** { *; }

# Media3 ExoPlayer
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# OkHttp & Jsoup
-dontwarn okhttp3.**
-dontwarn org.jsoup.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**
