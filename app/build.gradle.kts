plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.sumit.ruhi"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.sumit.ruhi"
        minSdk = 26
        targetSdk = 34
        versionCode = 6
        versionName = "6.0"
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
        getByName("debug") {
            signingConfig = signingConfigs.getByName("ruhi")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}
