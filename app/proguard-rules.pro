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

# Room entities and the schema JSON are read by generated code; Room's own rules cover the rest.
-keep class com.auracurve.app.data.** { *; }

# AuraCurve roadmap 01, step 3: verbose, debug and info logging is stripped from the release build
# (with proguard-android-optimize.txt R8 removes the calls; their arguments go too where they have
# no other use). Warnings and errors stay: they are what a bug report needs.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
