plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Read-only probe. A separate application module on purpose: it shares no
// code with :app, so it cannot reach the model clients, the OCR path, or the
// input writer, and its merged manifest carries no INTERNET permission.
android {
    namespace = "com.jev.overseas.probe"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.jev.overseas.probe"
        minSdk = 30
        targetSdk = 35
        versionCode = 1
        versionName = "t1-0.1"
    }

    buildFeatures {
        buildConfig = true
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
    implementation(project(":a11y"))
    testImplementation("junit:junit:4.13.2")
}
