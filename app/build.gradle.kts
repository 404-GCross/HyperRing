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
}
