# Keep the Compose runtime reflection entry points used by the tooling/inspection.
-keep class androidx.compose.runtime.** { *; }
-dontwarn androidx.compose.runtime.**

# The island services and receivers are instantiated by the framework from the manifest.
-keep class dev.island.service.** { *; }
-keep class dev.island.data.notifications.IslandNotificationListener { *; }
-keep class dev.island.core.BootReceiver { *; }

# Notification listener metadata is read reflectively by the system UI.
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod

# Keep line numbers for readable crash reports without shipping source files.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
