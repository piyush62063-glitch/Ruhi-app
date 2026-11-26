plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.sumit.ruhi"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.sumit.ruhi"
        minSdk = 24
        targetSdk = 34
        versionCode = 21
        versionName = "17.0-vosk"
    }
    signingConfigs {
        create("ruhi") {
            storeFile = file("ruhi.keystore")
            storePassword = "ruhi123"
            keyAlias = "ruhi"
            keyPassword = "ruhi123"
        }
    }
    buildTypes {
        getByName("debug") { signingConfig = signingConfigs.getByName("ruhi") }
        getByName("release") { signingConfig = signingConfigs.getByName("ruhi") }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    packaging { jniLibs { useLegacyPackaging = true } }
}
dependencies {
    implementation("com.alphacephei:vosk-android:0.3.47")
    implementation("net.java.dev.jna:jna:5.13.0@aar")
}
