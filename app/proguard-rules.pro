# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Hilt
-keep class * extends androidx.hilt.navigation.compose.** { *; }

# Firebase
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Gson / Kotlin Serialization / Data models
-keepclassmembers class com.sypos.mobile.data.local.entity.** { *; }
-keepclassmembers class com.sypos.mobile.data.local.** { *; }
