plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.watchalarm.wear"
    compileSdk = 35

    defaultConfig {
        // Muss identisch mit der Handy-App sein, damit die Data Layer API
        // beide Apps als Paar erkennt.
        applicationId = "com.watchalarm"
        minSdk = 30
        targetSdk = 34
        versionCode = 3
        versionName = "1.6"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Mit dem Standard-Debug-Key signieren, damit der Release-Build
            // ohne eigenes Keystore installierbar ist. Handy und Uhr nutzen
            // denselben Key -> Data Layer koppelt weiterhin.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    lint {
        // Fehlalarm: wir nutzen ComponentActivity/Compose, keine Fragments.
        disable += "InvalidFragmentVersionForActivityResult"
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
    }
}

dependencies {
    implementation(project(":core"))

    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.wear.compose:compose-material:1.4.0")
    implementation("androidx.wear.compose:compose-foundation:1.4.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
}
