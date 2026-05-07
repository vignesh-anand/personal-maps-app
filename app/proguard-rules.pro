# Keep GTFS-RT protobuf classes
-keep class com.google.transit.realtime.** { *; }
-keep class com.google.protobuf.** { *; }

# Hilt / Dagger
-keep class dagger.hilt.** { *; }
-keep class * extends androidx.hilt.work.HiltWorker { *; }

# Retrofit
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response

# Kotlinx serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keep,includedescriptorclasses class com.scoot.transit.**$$serializer { *; }
-keepclassmembers class com.scoot.transit.** {
    *** Companion;
}
-keepclasseswithmembers class com.scoot.transit.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**
