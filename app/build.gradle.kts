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
        buildConfigField("String", "BACKEND_URL", prop("roadpulse.backendUrl"))
        // The developer's own Docker stack; only debug builds may use it (plain HTTP to 10.0.2.2).
        buildConfigField("String", "LOCAL_ROUTING_URL", "\"\"")
        buildConfigField("String", "LOCAL_BACKEND_URL", "\"\"")
        buildConfigField("String", "LOCAL_MAP_STYLE_URL", "\"\"")
        buildConfigField("String", "LOCAL_MAP_STYLE_DARK_URL", "\"\"")
    }

    buildTypes {
        debug {
            buildConfigField("String", "LOCAL_ROUTING_URL", prop("roadpulse.local.routingUrl"))
            buildConfigField("String", "LOCAL_BACKEND_URL", prop("roadpulse.local.backendUrl"))
            buildConfigField("String", "LOCAL_MAP_STYLE_URL", prop("roadpulse.local.mapStyleUrl"))
            buildConfigField("String", "LOCAL_MAP_STYLE_DARK_URL", prop("roadpulse.local.mapStyleDarkUrl"))
        }
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
    implementation(project(":shared"))
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
    testImplementation(libs.okhttp.mockwebserver)
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
    // Optional end-to-end test against a running backend (see LiveBackendTest).
    System.getenv("ROADPULSE_BACKEND_URL")?.let { environment("ROADPULSE_BACKEND_URL", it) }
    System.getenv("ROADPULSE_MODERATOR_KEY")?.let { environment("ROADPULSE_MODERATOR_KEY", it) }
    System.getenv("ROADPULSE_LONG_TEST")?.let { environment("ROADPULSE_LONG_TEST", it) }
    System.getenv("ROADPULSE_PLACES_LAT")?.let { environment("ROADPULSE_PLACES_LAT", it) }
    System.getenv("ROADPULSE_PLACES_LON")?.let { environment("ROADPULSE_PLACES_LON", it) }
    // Screenshot tests write PNGs to app/build/outputs/roborazzi/ on every run.
    systemProperty("roborazzi.test.record", "true")
}
