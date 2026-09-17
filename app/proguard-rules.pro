# SafeGuard ProGuard & R8 Optimization Rules

# Preserve Kotlin metadata and line numbers for stack traces
-keepattributes SourceFile,LineNumberTable,*Annotation*,Signature,InnerClasses,EnclosingMethod

# SafeGuard Entities & Data Models
-keep class com.safeguard.data.entity.** { *; }
-keep class com.safeguard.blocklist.BlockedCategory { *; }
-keep class com.safeguard.blocklist.FilterDecision { *; }
-keep class com.safeguard.blocklist.FilterResult { *; }
-keep class com.safeguard.blocklist.DomainFilter$FilterEntry { *; }
-keep class com.safeguard.vpn.VpnStatus { *; }
-keep class com.safeguard.vpn.VpnState { *; }
-keep class com.safeguard.vpn.VpnErrorCode { *; }

# SafeGuard Service & Broadcast Receivers
-keep class com.safeguard.vpn.SafeGuardVpnService { *; }
-keep class com.safeguard.receiver.BootReceiver { *; }
-keep class com.safeguard.SafeGuardApplication { *; }
-keep class com.safeguard.MainActivity { *; }

# AndroidX Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**
-keep class androidx.room.** { *; }
-keep @androidx.room.Dao interface * { *; }
-keep @androidx.room.Entity class * { *; }

# AndroidX DataStore & Preferences
-keep class androidx.datastore.** { *; }

# Moshi & JSON Parsing
-keepclasseswithmembers class * {
    @com.squareup.moshi.* <methods>;
}
-keepclasseswithmembers class * {
    @com.squareup.moshi.* <fields>;
}
-keep class com.squareup.moshi.** { *; }
-dontwarn com.squareup.moshi.**

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}

# OkHttp & Retrofit
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# WorkManager
-keep class * extends androidx.work.Worker { *; }
-keep class * extends androidx.work.ListenableWorker { *; }
-keep class * extends androidx.work.CoroutineWorker { *; }

