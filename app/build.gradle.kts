plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

fun prop(name: String): String = "\"${project.property(name)}\""

android {
    namespace = "com.roadpulse.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.roadpulse.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        buildConfigField("String", "MAP_STYLE_URL", prop("roadpulse.mapStyleUrl"))
        buildConfigField("String", "MAP_STYLE_DARK_URL", prop("roadpulse.mapStyleDarkUrl"))
        buildConfigField("String", "ROUTING_URL", prop("roadpulse.routingUrl"))
        buildConfigField("String", "GEOCODING_URL", prop("roadpulse.geocodingUrl"))
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.compose.material.icons.extended)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.maplibre)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    // Screenshot tests of the map overlays on the JVM (no device needed).
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi.core)
    testImplementation(libs.roborazzi.compose)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}

tasks.withType<Test>().configureEach {
    System.getProperty("routingBodyOut")?.let { systemProperty("routingBodyOut", it) }
    // Screenshot tests write PNGs to app/build/outputs/roborazzi/ on every run.
    systemProperty("roborazzi.test.record", "true")
}
