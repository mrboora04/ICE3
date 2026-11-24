# Plate release rules (R8 on since 2.14).
# Libraries ship their own rules (Room, WorkManager, Glance, Compose, Kotlin). These add what's
# specific to Plate: anything Android or a library creates by name, and readable crash traces.

# Crash reports keep file and line numbers.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Data: Room entities and enums are stored by name (Meal.LUNCH, Sex.MALE…); keep them as written.
-keep class com.mrboora.plate.data.** { *; }
-keepclassmembers enum com.mrboora.plate.** { *; }

# Home-screen widgets: receivers, the widget classes and the button action are created by name.
-keep class com.mrboora.plate.widget.** { *; }

# Background jobs (daily menu update, selfie reminder) are created by class name.
-keep class * extends androidx.work.ListenableWorker { public <init>(android.content.Context, androidx.work.WorkerParameters); }
