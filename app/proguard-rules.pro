# R8 rules for the release build (roadmap 06, P6-6).
#
# Most libraries here (Hilt, Room, Coil, OkHttp, Media3, WorkManager, kotlinx.serialization) ship
# their own consumer rules, and everything declared in the manifest is kept by AAPT. What is left is
# what is reached by name at run time.

# Stack traces in bug reports and the diagnostics report stay readable.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# kotlinx.serialization: the generated serializers of our own models are found by name
# (Json.decodeFromString(Preset.serializer(), ...) is direct, but nested/polymorphic lookups are not).
-keepclassmembers class com.auracurve.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.auracurve.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.auracurve.app.**$$serializer { *; }
-keep,includedescriptorclasses class com.auracurve.app.player.**$$serializer { *; }

# Room (Teil-Roadmap 4, m-4): AppDatabase_Impl is created by name (Room.databaseBuilder), which
# room-runtime's consumer rules already keep. Its generated DAOs read and write the entities' fields
# directly, without reflection; the entities are kept anyway - only they, not the whole data package -
# so their classes and fields stay recognizable next to the exported schema (app/schemas) in a
# stack trace. DAOs, migrations, repositories and :core's preset/correction JSON code (same package
# name, serialized through the generated serializers kept above) are shrunk and obfuscated like the
# rest of the app.
-keep @androidx.room.Entity class com.auracurve.app.data.** {
    <init>(...);
    <fields>;
}

# AuraCurve roadmap 01, step 3: verbose, debug and info logging is stripped from the release build
# (with proguard-android-optimize.txt R8 removes the calls; their arguments go too where they have
# no other use). Warnings and errors stay: they are what a bug report needs.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
