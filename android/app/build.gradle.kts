import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val clawfaceBaseUrl = (localProps.getProperty("clawface.baseUrl") ?: "").replace("\"", "\\\"")
val clawfaceToken = (localProps.getProperty("clawface.token") ?: "").replace("\"", "\\\"")

android {
    namespace = "com.openclaw.clawface"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.openclaw.clawface"
        minSdk = 26
        targetSdk = 34
        versionCode = 3
        versionName = "0.3.0"
        buildConfigField("String", "CLAWFACE_BASE_URL", "\"$clawfaceBaseUrl\"")
        buildConfigField("String", "CLAWFACE_TOKEN", "\"$clawfaceToken\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
