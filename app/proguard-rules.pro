# Release builds currently ship without minification (isMinifyEnabled = false).
# Keep kotlinx.serialization metadata if minification is enabled later.
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class io.github.eyalshimshi.shidur.model.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
