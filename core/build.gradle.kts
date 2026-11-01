plugins {
    alias(libs.plugins.kotlin.jvm)
}

group = "io.stonecapture"
version = "0.1.0"

kotlin {
    jvmToolchain(21)
    compilerOptions {
        // The Android app module targets Java 17 bytecode.
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    testImplementation(kotlin("test"))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}
