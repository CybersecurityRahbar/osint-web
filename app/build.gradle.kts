plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val previewKeystorePath = providers.environmentVariable("PREVIEW_KEYSTORE_PATH").orNull?.takeIf { it.isNotBlank() }
val previewKeystorePassword = providers.environmentVariable("PREVIEW_KEYSTORE_PASSWORD").orNull?.takeIf { it.isNotBlank() }
val previewKeyAlias = providers.environmentVariable("PREVIEW_KEY_ALIAS").orNull?.takeIf { it.isNotBlank() } ?: "osint-preview"
val hasStablePreviewSigning = previewKeystorePath != null && previewKeystorePassword != null

android {
    namespace = "com.cyberphantom.osintworkbench"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.cyberphantom.osintworkbench"
        manifestPlaceholders["appLabel"] = "OSINT Workbench"
        minSdk = 26
        targetSdk = 36
        versionCode = 7
        versionName = "0.5.2"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    if (hasStablePreviewSigning) {
        signingConfigs.create("previewStable") {
            storeFile = file(previewKeystorePath!!)
            storePassword = previewKeystorePassword
            keyAlias = previewKeyAlias
            keyPassword = previewKeystorePassword
            storeType = "pkcs12"
        }
    }

    // Preview releases all use this same package ID and stable signing key.
    // The previous .preview050 test build used a temporary CI key, so .preview051
    // is a one-time side-by-side transition; future v0.5.1+ builds update in place.
    buildTypes {
        getByName("debug") {
            applicationIdSuffix = ".preview051"
            versionNameSuffix = "-preview"
            if (hasStablePreviewSigning) {
                signingConfig = signingConfigs.getByName("previewStable")
            }
            manifestPlaceholders["appLabel"] = "OSINT Workbench Preview 0.5.1"
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.04.01"))
    implementation("androidx.activity:activity-compose:1.12.4")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("com.googlecode.libphonenumber:libphonenumber:9.0.41")
    implementation("io.coil-kt:coil-compose:2.7.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    testImplementation("junit:junit:4.13.2")
    // Use the real JSON implementation in local JVM tests instead of Android's stubbed org.json classes.
    testImplementation("org.json:json:20250517")
}
