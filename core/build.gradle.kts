plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.watchalarm.core"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    testOptions {
        unitTests {
            // Robolectric braucht die Ressourcen des Moduls: SleepDuration
            // formatiert über getString(), das ohne sie nur crasht.
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    api("androidx.core:core-ktx:1.15.0")
    api("com.google.android.gms:play-services-wearable:18.2.0")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")

    // Robolectric statt reiner JVM-Tests: Der zu prüfende Kern hängt an
    // SharedPreferences (AlarmStore), org.json (Alarm) und getString()
    // (SleepDuration). Alles davon ist im android.jar der Unit-Tests nur ein
    // Stub, der beim Aufruf wirft.
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
}
