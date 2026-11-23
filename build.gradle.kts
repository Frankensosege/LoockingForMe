// Top-level build file where you can add configuration options common to all sub-projects/modules.
// kotlin.plugin.compose는 Kotlin 2.0.0+에서만 사용 가능 — 1.9.22에서는 제거
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.ksp) apply false
}
