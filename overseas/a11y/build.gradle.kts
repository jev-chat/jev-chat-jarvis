plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

// Shared accessibility reading: walk a window's node tree, measure whether the
// message list was moving, wait for it to settle. Declares no permissions and
// never performs an action on a node.
android {
    namespace = "com.jev.overseas.a11y"
    compileSdk = 35

    defaultConfig {
        minSdk = 30
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
    api(project(":core"))
    testImplementation("junit:junit:4.13.2")
}
