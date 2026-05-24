# Keep generic type info so Retrofit/Gson can deserialize.
-keepattributes Signature
-keepattributes *Annotation*

# ─── Kotlin metadata ────────────────────────────────────────────────
-keep class kotlin.Metadata { *; }
-keep class kotlin.reflect.** { *; }

# ─── Compose ────────────────────────────────────────────────────────
# Compose runtime tooling references reflect into Composable singletons.
-keep class androidx.compose.runtime.** { *; }
-dontwarn androidx.compose.**

# ─── AndroidX Media3 ────────────────────────────────────────────────
# Media3 already ships consumer rules in newer versions, but pin a
# few critical entries we depend on regardless.
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# ─── Retrofit / Gson / OkHttp ───────────────────────────────────────
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response

# Gson uses reflection on serialized fields.
-keep class com.google.gson.** { *; }
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
# Domain models that Gson serializes via TypeToken.
-keep class com.podcastplayer.app.domain.model.** { *; }
-keep class com.podcastplayer.app.data.local.QueueStorage$QueuePayload { *; }
-keep class com.podcastplayer.app.data.remote.** { *; }

# ─── Room ───────────────────────────────────────────────────────────
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# ─── youtubedl-android / Python runtime ─────────────────────────────
# The bundled Python runtime resolves classes by reflection at unpack time.
-keep class com.yausername.** { *; }
-dontwarn com.yausername.**

# ─── WorkManager workers (reflective instantiation) ─────────────────
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.CoroutineWorker
-keepclassmembers class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# ─── Application class / Services (manifest-referenced) ─────────────
-keep class com.podcastplayer.app.PodcastApplication { *; }
-keep class com.podcastplayer.app.MainActivity { *; }
-keep class com.podcastplayer.app.service.** { *; }

# Allow stripping noisy classes safely.
-dontwarn javax.annotation.**
