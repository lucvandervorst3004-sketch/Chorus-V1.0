# Keep Spotify App Remote protocol classes stable. The SDK uses generated protocol
# models and callbacks that are safer to leave untouched by R8.
-keep class com.spotify.** { *; }
-dontwarn com.spotify.**

# Keep Gson signatures so generic type adapters continue to resolve correctly.
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
