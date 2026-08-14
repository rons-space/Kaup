plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "app.kaup.core.network"
    compileSdk = 36

    defaultConfig {
        minSdk = 24
    }

    // #178: see the note in :core-data. Pinned here rather than in a root
    // subprojects block so each module states its own contract.
    compileOptions {
        sourceCompatibility = JavaVersion.toVersion(libs.versions.javaTarget.get())
        targetCompatibility = JavaVersion.toVersion(libs.versions.javaTarget.get())
    }
}

dependencies {
    // CONTEXT.md: core modules may depend on :shared-kmp only. The
    // :core-data dependency that used to sit here was never imported, and the
    // sync queue is reached through repository interfaces, not DAOs.
    implementation(project(":shared-kmp"))

    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.datetime)
}
