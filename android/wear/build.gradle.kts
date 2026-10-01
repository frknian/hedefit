plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Yayın imzası: keystore.properties (git'e eklenmez) veya ortam değişkenleri.
fun signingValue(name: String): String? =
    providers.gradleProperty(name).orNull ?: providers.environmentVariable(name).orNull

android {
    namespace = "com.hedefit.wear"
    compileSdk = 36

    defaultConfig {
        // Telefonla aynı paket adı: Data Layer eşleşmesi ve Play'de tek listeleme için gerekir.
        applicationId = "com.hedefit.app"
        minSdk = 30
        targetSdk = 36
        // Play, aynı paketin her form faktörü için farklı sürüm kodu ister; telefon kodunun üstünde tut.
        versionCode = 1_000_022
        versionName = "0.2.11"
    }

    signingConfigs {
        create("release") {
            val store = signingValue("HEDEFIT_UPLOAD_STORE_FILE")
            if (store != null) {
                storeFile = file(store)
                storePassword = signingValue("HEDEFIT_UPLOAD_STORE_PASSWORD")
                keyAlias = signingValue("HEDEFIT_UPLOAD_KEY_ALIAS")
                keyPassword = signingValue("HEDEFIT_UPLOAD_KEY_PASSWORD")
            }
        }
    }

    buildFeatures { compose = true }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (signingValue("HEDEFIT_UPLOAD_STORE_FILE") != null) signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.09.00"))
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.fragment:fragment-ktx:1.8.9")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.wear.compose:compose-material:1.5.0")
    implementation("androidx.wear.compose:compose-foundation:1.5.0")
    implementation("com.google.android.gms:play-services-wearable:19.0.0")
    implementation("androidx.health:health-services-client:1.1.0-alpha05")
    implementation("androidx.wear:wear-ongoing:1.1.0")
    implementation("androidx.wear.tiles:tiles:1.5.0")
    implementation("androidx.wear.protolayout:protolayout:1.3.0")
    implementation("androidx.wear.protolayout:protolayout-material:1.3.0")
    implementation("androidx.wear.protolayout:protolayout-expression:1.3.0")
    implementation("androidx.wear.watchface:watchface-complications-data-source-ktx:1.2.1")
    implementation("com.google.guava:guava:33.4.8-android")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-guava:1.10.2")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
