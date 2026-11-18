plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.pulsepad.controller"
    compileSdk = 37

    // A tagged release build passes -PversionName / -PversionCode so the published
    // APK reports the version its tag names. That matters for the in-app updater,
    // which compares its own versionName against the newest release tag: if the
    // two disagreed, the app would offer an update that is really itself.
    val releaseVersionName = providers.gradleProperty("versionName").orNull ?: "1.0.0"
    val releaseVersionCode = providers.gradleProperty("versionCode").orNull?.toIntOrNull() ?: 1

    // A stable signing key, supplied by the environment so it never enters the
    // repository. Without it the release build falls back to the debug key, which
    // is fine for local testing but changes between machines - and an APK signed
    // with a different key cannot update an installed copy.
    val releaseKeystore = providers.environmentVariable("PULSEPAD_KEYSTORE_PATH").orNull
    val releaseStorePassword = providers.environmentVariable("PULSEPAD_KEYSTORE_PASSWORD").orNull
    val releaseKeyAlias = providers.environmentVariable("PULSEPAD_KEY_ALIAS").orNull
    val releaseKeyPassword = providers.environmentVariable("PULSEPAD_KEY_PASSWORD").orNull
    val hasReleaseKey = releaseKeystore != null && releaseStorePassword != null &&
        releaseKeyAlias != null && releaseKeyPassword != null &&
        file(releaseKeystore).isFile

    signingConfigs {
        if (hasReleaseKey) {
            create("release") {
                storeFile = file(releaseKeystore!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    defaultConfig {
        applicationId = "com.pulsepad.controller"
        minSdk = 28
        targetSdk = 35
        versionCode = releaseVersionCode
        versionName = releaseVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Prefer the stable release key; fall back to the debug key so
            // `assembleRelease` still runs locally without any setup.
            signingConfig = if (hasReleaseKey) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE*",
                "META-INF/NOTICE*"
            )
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    lint {
        abortOnError = false
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    // Hilt's aggregating processor parses Kotlin metadata; this keeps it in step
    // with the Kotlin version AGP compiles with.
    ksp(libs.kotlin.metadata.jvm)
    implementation(libs.androidx.hilt.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)

    implementation(libs.sceneview)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.compose.bom))
}
