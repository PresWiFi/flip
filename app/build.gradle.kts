plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.blakeriding.flip"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.blakeriding.flip"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    // Release key: from ~/.gradle/gradle.properties (flipKeystore, flipKeystorePassword) or the
    // FLIP_KEYSTORE / FLIP_KEYSTORE_PASSWORD environment variables (GitHub Actions).
    // Without it, release builds fall back to the debug key, and those can't update a phone that has a GitHub release installed.
    val keystore = providers.gradleProperty("flipKeystore").orElse(providers.environmentVariable("FLIP_KEYSTORE")).orNull
    val keystorePassword = providers.gradleProperty("flipKeystorePassword").orElse(providers.environmentVariable("FLIP_KEYSTORE_PASSWORD")).orNull
    signingConfigs {
        if (keystore != null) {
            create("release") {
                storeFile = file(keystore)
                storePassword = keystorePassword
                keyAlias = "flip"
                keyPassword = keystorePassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

// Screenshot tests only run when asked: ./gradlew testDebugUnitTest -PscreensDir=/some/folder
tasks.withType<Test>().configureEach {
    systemProperty("screens.dir", providers.gradleProperty("screensDir").getOrElse(""))
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.play.code.scanner)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.ui.test.junit4)
}
