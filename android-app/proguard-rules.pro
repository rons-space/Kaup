# R8 rules for the release build.
#
# The release build type sets isMinifyEnabled = true and names this file, so
# without it `assembleRelease` fails outright. Keep the rules narrow: anything
# reached only by reflection has to be listed, everything else should shrink.

# Room generates implementations that reference the entity and DAO types by
# name, and the generated code is loaded reflectively from the database class.
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-dontwarn androidx.room.paging.**

# Type converters resolve enum constants by name (see RoleConverter and the
# SyncStatus converter), so the constants must survive obfuscation.
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
    **[] $VALUES;
    public *;
}

# kotlinx.serialization keeps its generated serializers in companion objects
# that are only reached reflectively.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}

# Hilt and Dagger generate components that are looked up by name at runtime.
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager { *; }

# WorkManager instantiates workers by class name from the enqueued request.
-keep class * extends androidx.work.ListenableWorker { <init>(...); }

# Keep source file and line numbers so release crash reports stay readable,
# but rename the source file attribute so it does not leak the original paths.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
