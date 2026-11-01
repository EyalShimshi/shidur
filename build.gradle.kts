// Kotlin is built into AGP 9, so the org.jetbrains.kotlin.android plugin is not applied.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.compose) apply false
}
