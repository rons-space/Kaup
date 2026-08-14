plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {

    android {
        namespace = "app.kaup.shared"
        compileSdk = 36
        minSdk = 24
        withHostTest {}
    }

    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.core)
            implementation(libs.kotlinx.datetime)
            // kmp-app-updater is deliberately absent: it used to sit here in
            // commonMain, which put a sideload updater into the F-Droid and
            // Play Store builds. It is declared githubImplementation-only in
            // :android-app now (ADR-014).
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
        }
    }
}
