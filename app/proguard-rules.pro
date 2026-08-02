# Proguard / R8 rules — only relevant if isMinifyEnabled = true
# Currently off for sideloaded distribution.

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }

# Keep our data model for JSON parsing via org.json reflection
-keep class com.claudetracker.app.data.model.** { *; }
