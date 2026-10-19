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
        versionCode = 3
        versionName = "1.1.0"
    }

    buildFeatures {
        buildConfig = true
    }

    flavorDimensions += listOf("plano", "tipo")
    productFlavors {
        create("essencial") {
            dimension = "plano"
            applicationIdSuffix = ".essencial"
            versionNameSuffix = "-essencial"
        }
        create("profissional") {
            dimension = "plano"
            applicationIdSuffix = ".profissional"
            versionNameSuffix = "-profissional"
        }
        create("premium") {
            dimension = "plano"
            applicationIdSuffix = ".premium"
            versionNameSuffix = "-premium"
        }
        create("restaurante") {
            dimension = "tipo"
            applicationIdSuffix = ".restaurante"
            versionNameSuffix = "-restaurante"
            resValue("string", "app_name", "Bicho Mau - Restaurante")
        }
        create("cliente") {
            dimension = "tipo"
            applicationIdSuffix = ".cliente"
            versionNameSuffix = "-cliente"
            resValue("string", "app_name", "Bicho Mau - Cliente")
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
