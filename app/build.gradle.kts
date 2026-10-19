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
        versionName = "1.0.0"
    }

    buildFeatures {
        buildConfig = true
    }

    flavorDimensions += "plano"
    productFlavors {
        create("essencial") {
            dimension = "plano"
            applicationIdSuffix = ".essencial"
            versionNameSuffix = "-essencial"
            resValue("string", "app_name", "Bicho Mau • Essencial")
        }
        create("profissional") {
            dimension = "plano"
            applicationIdSuffix = ".profissional"
            versionNameSuffix = "-profissional"
            resValue("string", "app_name", "Bicho Mau • Profissional")
        }
        create("premium") {
            dimension = "plano"
            applicationIdSuffix = ".premium"
            versionNameSuffix = "-premium"
            resValue("string", "app_name", "Bicho Mau • Premium")
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
