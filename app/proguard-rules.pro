# SceneView / Filament
-keep class com.google.android.filament.** { *; }
-keep class io.github.sceneview.** { *; }
-dontwarn com.google.android.filament.**
-dontwarn io.github.sceneview.**

# Kotlin serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.pulsepad.controller.** {
    *** Companion;
}
-keepclasseswithmembers class com.pulsepad.controller.** {
    kotlinx.serialization.KSerializer serializer(...);
}
