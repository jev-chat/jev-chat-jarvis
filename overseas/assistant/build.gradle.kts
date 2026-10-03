import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Release signing: a properties file kept OUTSIDE the repo (storeFile /
// storePassword / keyAlias / keyPassword), named by the JEV_OVERSEAS_KEYSTORE_PROPS
// environment variable. Without it, release builds are unsigned.
val releaseProps = Properties().apply {
    System.getenv("JEV_OVERSEAS_KEYSTORE_PROPS")?.let { file(it) }?.takeIf { it.exists() }
        ?.let { f -> FileInputStream(f).use { load(it) } }
}

// The overseas assistant (WhatsApp, English). Separate from the upstream :app.
// Network access goes only to openrouter.ai; the merged manifest has INTERNET only.
android {
    namespace = "com.jev.overseas.assistant"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.jev.overseas"
        minSdk = 30
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    buildFeatures {
        buildConfig = true
    }

    signingConfigs {
        if (releaseProps.isNotEmpty()) {
            create("release") {
                storeFile = file(releaseProps.getProperty("storeFile"))
                storePassword = releaseProps.getProperty("storePassword")
                keyAlias = releaseProps.getProperty("keyAlias")
                keyPassword = releaseProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
        }
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
    implementation(project(":core"))
    implementation(project(":a11y"))
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    testImplementation("junit:junit:4.13.2")
}
