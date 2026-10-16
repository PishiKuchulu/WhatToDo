# ProGuard/R8 rules for What ToDo
#
# NOTE: androidx.room and androidx.work ship their own consumer keep rules, so
# only app-side classes accessed via reflection are listed here explicitly.

# --- Room ---
# Database is instantiated by Room's generated code; entities/DAOs are kept via
# their annotations (targeted, not whole-package keeps).
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
# Keep Room field annotations visible at runtime and their fields' members.
-keepclassmembers class * {
    @androidx.room.PrimaryKey <fields>;
    @androidx.room.ColumnInfo <fields>;
    @androidx.room.Embedded <fields>;
    @androidx.room.Relation <fields>;
    @androidx.room.Ignore <fields>;
}
# Converters (data.local.Converters) is referenced directly via
# @TypeConverters(Converters::class) in PishiDatabase, so R8 keeps it through
# the normal call graph; no keep needed here.

# --- WorkManager ---
# ReminderWorker and BirthdayReminderWorker extend CoroutineWorker (a
# ListenableWorker) and are instantiated reflectively by WorkManager.
# Only the (Context, WorkerParameters) constructor must be kept.
-keepclassmembers class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# --- Gson (backup JSON) ---
# BackupUseCases.kt uses only Gson's tree model (JsonObject/JsonArray/JsonParser)
# with manual field mapping for backup export/import. There is NO reflective
# POJO (de)serialization (no fromJson(..., X::class.java)), so the whole Gson
# library must NOT be kept; only the referenced API surface is kept.
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.Gson { <methods>; }
-keep class com.google.gson.GsonBuilder { <methods>; }
-keep class com.google.gson.JsonParser { <methods>; }
-keep class com.google.gson.JsonObject { <methods>; }
-keep class com.google.gson.JsonArray { <methods>; }
-keep class com.google.gson.JsonElement { <methods>; }
-keep class com.google.gson.JsonPrimitive { <methods>; }
-keep class com.google.gson.JsonNull { <methods>; }
-keepclassmembers enum * { *; }
