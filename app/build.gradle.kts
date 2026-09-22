plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.alberto.ninnananna"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.alberto.ninnananna"
        minSdk = 26
        targetSdk = 34
        versionCode = 12
        versionName = "7.5"
    }

    signingConfigs {
        // Debug keystore committed to repo to keep sideload installs upgradeable
        // across ephemeral CI runners (otherwise each build gets a random debug key).
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        create("release") {
            val keystoreFile = System.getenv("KEYSTORE_FILE")
            if (keystoreFile != null) {
                storeFile = file(keystoreFile)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // If there are no keystore variables, sign with the debug key
            // to still produce an installable APK (not suitable for Google Play).
            signingConfig = if (System.getenv("KEYSTORE_FILE") != null) {
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

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.02.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("androidx.datastore:datastore-preferences:1.0.0")

    // Player audio locale
    implementation("androidx.media3:media3-exoplayer:1.3.1")
    implementation("androidx.media3:media3-common:1.3.1")

    // Background download (WorkManager + permanent foreground notification)
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // Cast to Google Chromecast / Cast devices
    implementation("com.google.android.gms:play-services-cast-framework:21.4.0")
    implementation("androidx.mediarouter:mediarouter:1.6.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    // MaterialComponents theme for the MediaRouteButton (the app theme is not AppCompat)
    implementation("com.google.android.material:material:1.11.0")
    // Mini local HTTP server to serve audio files over LAN (required by Chromecast)
    implementation("org.nanohttpd:nanohttpd:2.3.1")

    // YouTube audio download (on-device, no backend)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.github.TeamNewPipe:NewPipeExtractor:v0.26.5")
}