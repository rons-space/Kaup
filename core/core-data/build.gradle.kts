plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
}

android {
    namespace = "app.kaup.core.data"
    compileSdk = 36

    defaultConfig {
        minSdk = 24
    }

    // #178: pin the bytecode level instead of inheriting it from whichever JDK
    // runs Gradle. Under AGP 9 built-in Kotlin, Kotlin's jvmTarget defaults to
    // targetCompatibility, so this pins both compilers with one setting.
    compileOptions {
        sourceCompatibility = JavaVersion.toVersion(libs.versions.javaTarget.get())
        targetCompatibility = JavaVersion.toVersion(libs.versions.javaTarget.get())
    }

    // Robolectric needs the merged resources and the manifest to stand up an
    // Android runtime on the JVM. Without this the tests fail at startup rather
    // than on an assertion.
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

// Robolectric cannot run on the JDK that builds this project. It instruments
// bytecode with ASM and walks the type hierarchy of everything it loads, which
// reaches the running JDK's own class files; ASM refuses any class file version
// newer than it knows about, and CI runs JDK 26. The symptom is every test in
// the module failing identically inside ClassReader, before any assertion.
//
// Forking just the test JVM onto the toolchain that matches the bytecode target
// keeps that contained: compilation and the rest of the build stay on 26. The
// workflow installs both JDKs.
// Resolved at project scope, not inside configureEach: inside the block
// `extensions` is the Test task's own container, which holds nothing but extra
// properties.
val testJavaLauncher = project.extensions
    .getByType(JavaToolchainService::class.java)
    .launcherFor {
        languageVersion.set(JavaLanguageVersion.of(libs.versions.javaTarget.get()))
    }

tasks.withType<Test>().configureEach {
    javaLauncher.set(testJavaLauncher)
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

// kspDebugKotlin and kspReleaseKotlin both run Room's processor over the same
// main sources, so both export the schema into the single directory configured
// above. Gradle runs them concurrently, and Room reads an existing schema file
// before writing to skip the write when the content is unchanged. That read can
// land in the window where the other task has truncated the file and not yet
// flushed it, and Room fails the build with "Empty schema file".
//
// This is why the dev build broke after the version 5 bump while the identical
// tree passed on the pull request: the race is timing dependent, so it only
// bites some of the time. Both tasks produce byte-identical output, so ordering
// them costs nothing and is not a lost parallelism opportunity.
//
// The complete fix is the androidx.room Gradle plugin, which gives each variant
// its own schema directory and declares it as a real task input and output.
// That is tracked separately; it is a dependency change, and this file needs to
// stop failing builds now.
tasks.matching { it.name == "kspReleaseKotlin" }.configureEach {
    mustRunAfter("kspDebugKotlin")
}

dependencies {
    implementation(project(":shared-kmp"))
    
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    
    // Preferences DataStore
    implementation(libs.androidx.datastore.preferences)

    // Coroutines for DAO flows
    implementation(libs.kotlinx.coroutines.core)

    // JSR-330 for @Inject / @Singleton
    implementation(libs.javax.inject)

    // #174. These tests run on the JVM under Robolectric rather than as
    // instrumented tests, because CI has no emulator: ./gradlew build never
    // runs connectedAndroidTest, so anything in androidTest would be code that
    // never executes and therefore never gates anything.
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
}
