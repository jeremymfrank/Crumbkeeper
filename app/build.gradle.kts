plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.crumbkeeper.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.crumbkeeper.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 10100
        versionName = "1.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    val releaseStore = providers.environmentVariable("CRUMBKEEPER_KEYSTORE_PATH").orNull
    signingConfigs {
        if (releaseStore != null) create("distribution") {
            storeFile = file(releaseStore)
            storePassword = providers.environmentVariable("CRUMBKEEPER_STORE_PASSWORD").get()
            keyAlias = providers.environmentVariable("CRUMBKEEPER_KEY_ALIAS").get()
            keyPassword = providers.environmentVariable("CRUMBKEEPER_KEY_PASSWORD").get()
        }
    }
    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = false
            isDebuggable = false
            if (releaseStore != null) signingConfig = signingConfigs.getByName("distribution")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.04.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.exifinterface:exifinterface:1.4.1")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.0")
    // Bundled on-device Latin text recognition; no network/model download required.
    implementation("com.google.mlkit:text-recognition:16.0.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
