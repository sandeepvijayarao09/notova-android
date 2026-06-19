plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kover)
}

android {
    namespace = "com.notova.ai"
    compileSdk = 35

    defaultConfig {
        // minSdk stays 26: both tasks-genai (manifest minSdk 21) and genai-summarization
        // (manifest minSdk 26) are compatible, so no module-wide bump is required.
        minSdk = 26
        consumerProguardFiles("consumer-rules.pro")
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
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    implementation(project(":core"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    // Bridges the ListenableFuture-based ML Kit / MediaPipe APIs into suspend functions.
    implementation(libs.kotlinx.coroutines.guava)

    // OkHttp powers the model downloader (progress streaming).
    implementation(libs.okhttp)

    // Optional on-device engines. Both are guarded at runtime so the app builds and runs
    // even where the native engine / model / AICore is unavailable.
    implementation(libs.mediapipe.tasks.genai)
    implementation(libs.mlkit.genai.summarization)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.core.ktx)
    testImplementation(libs.okhttp.mockwebserver)
}
