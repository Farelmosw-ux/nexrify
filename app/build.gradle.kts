import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.gms.google-services")
}

android {
    namespace = "id.nexrify.music"
    compileSdk = 34

    defaultConfig {
        applicationId = "id.nexrify.music"
        minSdk = 26
        targetSdk = 34
        versionCode = 43
        versionName = "4.0.8"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Hanya arm64-v8a: library native ML Kit/TFLite cukup dipaket 1x (bukan 2x), ini penghemat terbesar.
        // Kalau mau dukung HP 32-bit lama, tambahkan lagi "armeabi-v7a" (APK jadi lebih besar).
        ndk { abiFilters += listOf("arm64-v8a") }
        // Buang string bahasa library (Firebase, Material, dll) yang tidak dipakai; app punya i18n sendiri.
        resourceConfigurations += listOf("en", "in")
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    val signingProperties = Properties()
    val signingPropertiesFile = rootProject.file("key.properties")
    if (signingPropertiesFile.exists()) {
        signingPropertiesFile.inputStream().use { signingProperties.load(it) }
    }
    val signingKeysPresent = listOf("storeFile", "storePassword", "keyAlias", "keyPassword")
        .all { !signingProperties.getProperty(it).isNullOrBlank() }

    signingConfigs {
        if (signingPropertiesFile.exists() && signingKeysPresent) {
            create("release") {
                storeFile = file(signingProperties.getProperty("storeFile"))
                storePassword = signingProperties.getProperty("storePassword")
                keyAlias = signingProperties.getProperty("keyAlias")
                keyPassword = signingProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            if (signingConfigs.findByName("release") != null) signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
    packaging {
        // Kompres library native di dalam APK (default AGP menyimpannya mentah => APK sangat besar).
        jniLibs { useLegacyPackaging = true }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/INDEX.LIST"
            excludes += "META-INF/DEPENDENCIES"
            excludes += "META-INF/LICENSE*"
            excludes += "META-INF/NOTICE*"
            excludes += "META-INF/*.version"
            excludes += "META-INF/com/android/build/gradle/**"
            excludes += "DebugProbesKt.bin"
            excludes += "kotlin-tooling-metadata.json"
            excludes += "okhttp3/internal/publicsuffix/NOTICE"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.0")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.0")

    val composeBom = platform("androidx.compose:compose-bom:2024.05.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // Media3 (ExoPlayer + MediaSession)
    implementation("androidx.media3:media3-exoplayer:1.3.1")
    implementation("androidx.media3:media3-session:1.3.1")
    implementation("androidx.media3:media3-common:1.3.1")

    // Coil Image Loading
    implementation("io.coil-kt:coil-compose:2.6.0")

    // Desugaring API Java 8-11 (URLDecoder, dll) untuk Android 8-12, dibutuhkan NewPipeExtractor
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs_nio:2.0.4")

    // NewPipeExtractor
    implementation("com.github.TeamNewPipe:NewPipeExtractor:v0.26.5")

    // OkHttp (for networking & downloader)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Potong latar foto untuk Poster lagu (model ikut di APK, jalan offline)
    implementation("com.google.mlkit:segmentation-selfie:16.0.0-beta6")

    // Firebase Cloud Messaging: notifikasi push yang muncul di layar kunci walau aplikasi ditutup
    implementation(platform("com.google.firebase:firebase-bom:33.5.1"))
    implementation("com.google.firebase:firebase-messaging")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
