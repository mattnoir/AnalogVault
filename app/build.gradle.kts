plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.analogvault"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.analogvault"
        minSdk = 26
        targetSdk = 35
        versionCode = 50
        versionName = "0.5.0"

        // Substituted into android:label. Overridden by the debug build type so
        // the two installs are distinguishable in the launcher.
        manifestPlaceholders["appLabel"] = "Analog Vault"
    }

    signingConfigs {
        create("release") {
            val keystorePath = System.getenv("KEYSTORE_PATH")
            if (keystorePath != null) {
                storeFile     = file(keystorePath)
                storePassword = System.getenv("STORE_PASSWORD") ?: ""
                keyAlias      = System.getenv("KEY_ALIAS") ?: ""
                keyPassword   = System.getenv("KEY_PASSWORD") ?: ""
            }
        }
    }

    buildTypes {
        debug {
            // Debug installs under its own application id so it can sit beside a
            // release-signed build on the same device. Without this, installing a
            // debug build onto a phone that already has the released app fails
            // with INSTALL_FAILED_UPDATE_INCOMPATIBLE, and the only way through
            // is uninstalling the real app and its vault data.
            applicationIdSuffix = ".debug"
            versionNameSuffix   = "-debug"
            // Same icon, same position in an alphabetical launcher — the label is
            // the only thing telling the test install apart from the real one.
            manifestPlaceholders["appLabel"] = "Analog Vault ·debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (System.getenv("KEYSTORE_PATH") != null)
                signingConfigs.getByName("release")
            else
                signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures { compose = true; buildConfig = true }
}

// Export Room schemas (committed under app/schemas) so future migrations can be
// written and tested against the exact historical table definitions.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)          // needed for Theme.AppCompat
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.compose)  // For LocalLifecycleOwner (moved here in newer versions)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.exifinterface)   // EXIF rotation when downscaling photo thumbnails

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.icons.extended)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.camerax.core)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)

    // Pulled in transitively by compose-ui. Declared here purely to override the
    // transitive 1.0.1, whose libandroidx.graphics.path.so is not 16 KB-aligned.
    implementation(libs.androidx.graphics.path)

    implementation(libs.retrofit.core)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp.logging)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Reminders (expiry / undeveloped rolls / chemical age)
    implementation(libs.work.runtime)
    implementation(libs.hilt.work)
    ksp(libs.hilt.androidx.compiler)

    implementation(libs.coil.compose)
    implementation(libs.coroutines.android)

    // Location only — no Maps SDK, no billing
    implementation(libs.play.services.location)

    // OpenStreetMap
    implementation(libs.osmdroid)
}
