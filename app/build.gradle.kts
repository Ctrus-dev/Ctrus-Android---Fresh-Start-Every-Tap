plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
}

android {
    namespace = "mo.dev.ctrus"
    compileSdk = 37

    defaultConfig {
        applicationId = "mo.dev.ctrus"
        minSdk = 27
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    // Without this, Android has no way to know the app ships pt-rPT/es resources, so it never
    // shows up under system Settings > Apps > [Ctrus] > App languages (Samsung's "Idiomas da
    // aplicação") — AGP generates the LocaleConfig resource + manifest reference automatically
    // from the values-*/ directories present, instead of hand-maintaining an XML list that can
    // drift out of sync with them.
    androidResources {
        generateLocaleConfig = true
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.06.01"))

    implementation("androidx.core:core-ktx:1.19.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.activity:activity-compose:1.13.0")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.9.8")
    implementation("androidx.datastore:datastore-preferences:1.2.1")

    // Persistence (Room = SwiftData equivalent)
    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    ksp("androidx.room:room-compiler:2.8.4")

    // Strategy config blobs (SoftUnblockStrategyData / StrategyTimerData / StrategyPauseTimerData)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")

    // Scheduling (DeviceActivityCenter equivalent: schedule/break/pause/strategy-timer/grant-expiry alarms)
    implementation("androidx.work:work-runtime-ktx:2.11.2")

    // Recovery-code backend (recover.ctrus.net)
    implementation("com.squareup.okhttp3:okhttp:5.5.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
