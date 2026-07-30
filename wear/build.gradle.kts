import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Siehe mobile/build.gradle.kts — beide Module lesen denselben Keystore,
// weil Handy und Uhr zwingend mit demselben Schlüssel signiert sein müssen.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}

fun signingValue(key: String, env: String): String? =
    keystoreProperties.getProperty(key) ?: System.getenv(env)

val appVersionName = providers.gradleProperty("watchalarm.versionName").get()
val appVersionCode = providers.gradleProperty("watchalarm.versionCode").get().toInt()
val wearVersionCodeOffset = providers.gradleProperty("watchalarm.wearVersionCodeOffset").get().toInt()

val releaseStorePath = signingValue("storeFile", "WATCHALARM_STORE_FILE")
val hasReleaseKeystore = releaseStorePath != null && rootProject.file(releaseStorePath).exists()

android {
    namespace = "com.watchalarm.wear"
    compileSdk = 35

    defaultConfig {
        // Muss identisch mit der Handy-App sein, damit die Data Layer API
        // beide Apps als Paar erkennt.
        applicationId = "com.watchalarm"
        minSdk = 30
        targetSdk = 35
        // Play verlangt für Uhr- und Handy-Artefakt derselben App-Eintragung
        // unterschiedliche versionCodes — daher der feste Offset.
        versionCode = appVersionCode + wearVersionCodeOffset
        versionName = appVersionName
    }

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = rootProject.file(releaseStorePath!!)
                storePassword = signingValue("storePassword", "WATCHALARM_STORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "WATCHALARM_KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "WATCHALARM_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = if (hasReleaseKeystore) {
                signingConfigs.getByName("release")
            } else {
                logger.warn(
                    "WatchAlarm: kein Release-Keystore gefunden — Release wird mit dem " +
                        "Debug-Key signiert und ist für Google Play NICHT verwendbar."
                )
                signingConfigs.getByName("debug")
            }
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
        buildConfig = true
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
