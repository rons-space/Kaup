plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "app.kaup.android"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.kaup.android"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "0.1-alpha"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // #178: see the note in :core-data.
    compileOptions {
        sourceCompatibility = JavaVersion.toVersion(libs.versions.javaTarget.get())
        targetCompatibility = JavaVersion.toVersion(libs.versions.javaTarget.get())
    }

    flavorDimensions += "distribution"
    productFlavors {
        // ADR-014: the flavors differ in exactly one thing, how the app is
        // updated. github self-updates from GitHub Releases, fdroid and
        // playstore leave it to their store. Anything else that diverges
        // between them belongs in a flavor source set, not behind a runtime
        // check, so the F-Droid build cannot carry code it must not ship.
        create("github") {
            dimension = "distribution"
            applicationIdSuffix = ".github"
        }
        create("fdroid") {
            dimension = "distribution"
            applicationIdSuffix = ".fdroid"
        }
        create("playstore") {
            dimension = "distribution"
        }
    }

    sourceSets {
        getByName("main") { kotlin.srcDir("src/main/kotlin") }
        getByName("test") { kotlin.srcDir("src/test/kotlin") }
        getByName("github") { kotlin.srcDir("src/github/kotlin") }
        getByName("fdroid") { kotlin.srcDir("src/fdroid/kotlin") }
        getByName("playstore") { kotlin.srcDir("src/playstore/kotlin") }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        compose = true
        // Needed by the #203 guardrail: DatabaseModule compares
        // BuildConfig.VERSION_NAME against the ADR-018 Phase 1 window before
        // arming the destructive fallback.
        buildConfig = true
    }
}

dependencies {
    implementation(project(":shared-kmp"))
    implementation(project(":core:core-data"))
    implementation(project(":core:core-ui"))
    implementation(project(":core:core-network"))
    implementation(project(":feature:feature-auth"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.compose.material3.adaptive.navigation.suite)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.work)
    
    implementation(libs.androidx.work.runtime.ktx)

    ksp(libs.androidx.hilt.compiler)
    
    // Room runtime needed for DatabaseModule initialization
    implementation(libs.androidx.room.runtime)

    // #159, encryption at rest. Declared here rather than in :core-data
    // because DatabaseModule is what builds the database and holds the
    // passphrase; :core-data only declares the schema.
    implementation(libs.sqlcipher.android)
    implementation(libs.androidx.sqlite)
    
    // DataStore needed for PreferencesModule
    implementation(libs.androidx.datastore.preferences)

    testImplementation(libs.junit)

    // Sideload updater, github flavor only. Declaring it per flavor is what
    // keeps a self-update path out of the Play Store build, which Play policy
    // forbids, and out of the F-Droid build, which does not need it.
    "githubImplementation"(libs.kmp.app.updater.core)
}
