plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "tech.briankeya.opencaption.shared"
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
}

dependencies {
    // Unit tests run on the JVM, so use a real org.json instead of the Android stub.
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
