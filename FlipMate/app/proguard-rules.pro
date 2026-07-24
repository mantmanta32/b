# Keep encrypted credential implementation and model metadata stable in release builds.
-keep class com.flipmate.app.domain.model.** { *; }
-dontwarn okhttp3.**
