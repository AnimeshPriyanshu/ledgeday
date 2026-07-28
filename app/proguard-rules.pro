# Keep Hilt
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }

# Keep Room entities
-keep class com.vaultledger.data.local.entity.** { *; }

# Keep serialization
-keep class kotlinx.serialization.** { *; }
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }

# Keep Firebase
-keep class com.google.firebase.** { *; }

# Keep domain models used for serialization
-keep class com.vaultledger.domain.model.** { *; }

# Keep Compose navigation
-keep class * extends androidx.navigation.NavType { *; }

# Remove all logging in release
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
}

# Obfuscation
-obfuscationdictionary /dev/null
-classobfuscationdictionary /dev/null
-packageobfuscationdictionary /dev/null
-repackageclasses 'com.vaultledger'
