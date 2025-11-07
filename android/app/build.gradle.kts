import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("com.jaredsburrows.license") version "0.9.8"
    id("com.github.ben-manes.versions") version "0.53.0"
}

android {
    namespace = "app.gemicom"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.gemicom"
        minSdk = 28
        targetSdk = 34
        versionCode = 9
        versionName = "v2025.11"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
        }
    }
    buildFeatures {
        buildConfig = true
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    sourceSets.getByName("main") {
        jniLibs.srcDirs("../../jniLibs")
    }
}
kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.material)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.xerial)
    implementation(libs.commons.io)
    implementation(libs.bundles.kodein)
    implementation(libs.bundles.coil)
    implementation(libs.bundles.logging)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    testImplementation(libs.appdirs)

    testImplementation(libs.kotlinx.coroutines.test)
    debugImplementation("com.squareup.leakcanary:leakcanary-android:2.14")
}
