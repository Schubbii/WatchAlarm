import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Release-Schlüssel: bevorzugt aus keystore.properties im Projektwurzel-
// verzeichnis (gehört NICHT ins Git), alternativ aus Umgebungsvariablen — so
// läuft dieselbe Konfiguration lokal und in der CI. Fehlt beides, fällt der
// Release-Build auf den Debug-Key zurück, damit er trotzdem baut.
// WICHTIG: Handy und Uhr müssen mit demselben Schlüssel signiert sein, sonst
// verweigert die Data Layer API die Kommunikation.
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

val releaseStorePath = signingValue("storeFile", "WATCHALARM_STORE_FILE")
val hasReleaseKeystore = releaseStorePath != null && rootProject.file(releaseStorePath).exists()

android {
    namespace = "com.watchalarm.mobile"
    compileSdk = 36

    defaultConfig {
        // Muss identisch mit der Wear-App sein, damit die Data Layer API
        // beide Apps als Paar erkennt.
        applicationId = "com.Rise.Alarm"
        minSdk = 26
        targetSdk = 36
        versionCode = appVersionCode
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
        // Liefert BuildConfig.VERSION_NAME für die Versionsanzeige in der UI,
        // damit die Version nur noch an einer Stelle gepflegt wird.
        buildConfig = true
    }
}

dependencies {
    implementation(project(":core"))

    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
}
