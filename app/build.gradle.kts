plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "pt.studiomesa.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "pt.studiomesa.app"
        minSdk = 24
        targetSdk = 35
        versionCode = 2
        versionName = "0.2.0"
    }

    buildTypes {
        debug {
            // Installs alongside the original app so testing does not erase its local data.
            applicationIdSuffix = ".preview"
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
