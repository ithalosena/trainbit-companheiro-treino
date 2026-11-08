import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    testImplementation(kotlin("test"))
}

// Fixtures and assets live in the app module so both modules share one copy.
sourceSets {
    test {
        resources.srcDir("../app/src/test/resources")
        resources.srcDir("../app/src/main/assets")
    }
}

tasks.test {
    useJUnitPlatform()
}
