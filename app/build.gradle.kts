plugins {
    id("com.android.application")
    kotlin("android")
    kotlin("plugin.serialization")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.ithalo.hybridcoach"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.ithalo.hybridcoach"
        minSdk = 26
        targetSdk = 35
        versionCode = 22
        versionName = "0.14.1"
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

// Release is the product TrainBit.exe with its own id, so it installs beside the debug build. The debug build keeps
// the original id (com.ithalo.hybridcoach): the test install on Ithalo's phone holds his progress. A build type can
// only add a suffix to the id, so the release id is set through the variant API (see docs/ASSUMPTIONS.md).
androidComponents {
    onVariants(selector().withBuildType("release")) { variant ->
        variant.applicationId.set("com.ithalo.trainbit")
    }
}

dependencies {
    implementation(project(":core"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    implementation(platform("androidx.compose:compose-bom:2024.10.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.navigation:navigation-compose:2.8.3")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Phase 3: FusedLocationProvider (GPS) and settings storage (see docs/ASSUMPTIONS.md).
    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}
