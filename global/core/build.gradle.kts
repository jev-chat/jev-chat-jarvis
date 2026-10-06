import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("org.jetbrains.kotlin.jvm")
}

// Pure Kotlin: no Android types and no third-party libraries. Everything here
// runs in plain JVM unit tests, including replay of recorded ScreenDumps.
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<KotlinCompile>().configureEach {
    kotlinOptions.jvmTarget = "17"
}

tasks.test {
    // Recorded dumps live at the repository root so other modules can use them.
    systemProperty("jev.fixtures", rootProject.file("fixtures").absolutePath)
}

// Development tool: runs the pilot corpus against the live models.
// Needs an OpenRouter key and spends a little money, so it is never part of `test`.
tasks.register<JavaExec>("liveEval") {
    group = "verification"
    description = "Runs the corpus against the live models (needs a key)."
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.jev.overseas.core.live.LiveEvalKt")
    systemProperty("jev.fixtures", rootProject.file("fixtures").absolutePath)
    args = ((project.findProperty("evalArgs") as String?) ?: "analysis").split(" ")
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
