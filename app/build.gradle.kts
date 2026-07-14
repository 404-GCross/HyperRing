plugins {
    id("com.android.application")
}

android {
    namespace = "io.github.gcross.hyperring"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.gcross.hyperring"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    signingConfigs {
        getByName("debug") {
            storeFile = rootProject.file("keystore/hyperring-debug.keystore")
            storePassword = "hyperring"
            keyAlias = "hyperring-debug"
            keyPassword = "hyperring"
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("debug")
        }
    }
}
