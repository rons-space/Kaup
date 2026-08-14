import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.serialization)
}

// #178: the Android modules pin this through android.compileOptions, which
// AGP's built-in Kotlin propagates to jvmTarget for them. This module does not
// use built-in Kotlin, so its JVM-flavoured targets are pinned by hand from the
// same catalog value rather than defaulting to whatever JDK runs Gradle.
val javaTarget = JvmTarget.fromTarget(libs.versions.javaTarget.get())

kotlin {

    android {
        namespace = "app.kaup.shared"
        compileSdk = 36
        minSdk = 24
        withHostTest {}
        compilerOptions {
            jvmTarget.set(javaTarget)
        }
    }

    jvm {
        compilerOptions {
            jvmTarget.set(javaTarget)
        }
    }

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
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
