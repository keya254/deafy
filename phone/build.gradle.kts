plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.deafy.phone"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.deafy.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        ndk {
            // Physical phones are arm64. Keeps the native libraries small.
            abiFilters += listOf("arm64-v8a")
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(project(":shared"))

    // Bundles the watch app so Galaxy Wearable installs it on the paired watch automatically.
    wearApp(project(":wear"))

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material:material-icons-core")

    // Wearable Data Layer (MessageClient / CapabilityClient) for watch link.
    implementation("com.google.android.gms:play-services-wearable:18.2.0")

    // On-device sound classification (YAMNet via MediaPipe Tasks).
    implementation("com.google.mediapipe:tasks-audio:0.10.14")

    // Offline speech recognition (Whisper via sherpa-onnx), see libs/ and README.
    implementation(files("libs/sherpa-onnx-2.0.1.aar"))
}
