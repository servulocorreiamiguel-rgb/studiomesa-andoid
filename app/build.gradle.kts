plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "pt.studiomesa.app"
    compileSdk = 35

    defaultConfig {
        // Use a separate application ID for the repaired test builds so they
        // install alongside any older APK signed with a different temporary key.
        // Keep this ID stable for subsequent test builds.
        applicationId = "pt.studiomesa.preview"
        minSdk = 24
        targetSdk = 35
        versionCode = 4
        versionName = "1.2.0"
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
            resValue("string", "app_name", "StudioMesa · Gestão")
        }
        create("cliente") {
            dimension = "tipo"
            applicationIdSuffix = ".cliente"
            versionNameSuffix = "-cliente"
            resValue("string", "app_name", "StudioMesa · Cliente")
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
