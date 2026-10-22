plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Commercial builds are opt-in. Ordinary debug builds retain the existing preview package IDs.
val commercialRelease = providers.gradleProperty("studiomesaCommercialRelease").orNull == "true"
val releaseApplicationId = providers.gradleProperty("studiomesaReleaseApplicationId").orNull
val releaseKeystorePath = providers.environmentVariable("STUDIOMESA_KEYSTORE_PATH").orNull
val releaseKeystorePassword = providers.environmentVariable("ANDROID_KEYSTORE_PASSWORD").orNull
val releaseKeyAlias = providers.environmentVariable("ANDROID_KEY_ALIAS").orNull
val releaseKeyPassword = providers.environmentVariable("ANDROID_KEY_PASSWORD").orNull

if (commercialRelease) {
    val applicationIdPattern = Regex("^[A-Za-z][A-Za-z0-9_]*(\\\\.[A-Za-z][A-Za-z0-9_]*)+$")
    if (releaseApplicationId.isNullOrBlank() || !applicationIdPattern.matches(releaseApplicationId)) {
        throw GradleException(
            "A release requires -PstudiomesaReleaseApplicationId with the final, valid base package ID. " +
                "Choose it before publishing; Google Play package IDs cannot be changed after publication."
        )
    }
    if (releaseKeystorePath.isNullOrBlank() || !file(releaseKeystorePath).isFile) {
        throw GradleException("A release requires a signing keystore at STUDIOMESA_KEYSTORE_PATH.")
    }
    val missingSigningValues = listOf(
        "ANDROID_KEYSTORE_PASSWORD" to releaseKeystorePassword,
        "ANDROID_KEY_ALIAS" to releaseKeyAlias,
        "ANDROID_KEY_PASSWORD" to releaseKeyPassword
    ).filter { it.second.isNullOrBlank() }.map { it.first }
    if (missingSigningValues.isNotEmpty()) {
        throw GradleException("Missing release signing environment values: ${missingSigningValues.joinToString()}.")
    }
}

android {
    namespace = "pt.studiomesa.app"
    compileSdk = 36

    defaultConfig {
        // Use a separate application ID for the repaired test builds so they
        // install alongside any older APK signed with a different temporary key.
        // Keep this ID stable for subsequent test builds.
        applicationId = if (commercialRelease) requireNotNull(releaseApplicationId) else "pt.studiomesa.preview"
        minSdk = 24
        targetSdk = 36
        versionCode = 7
        versionName = "1.5.0"
    }

    signingConfigs {
        if (commercialRelease) {
            create("commercial") {
                storeFile = file(requireNotNull(releaseKeystorePath))
                storePassword = requireNotNull(releaseKeystorePassword)
                keyAlias = requireNotNull(releaseKeyAlias)
                keyPassword = requireNotNull(releaseKeyPassword)
            }
        }
    }

    buildTypes {
        getByName("release") {
            if (commercialRelease) {
                signingConfig = signingConfigs.getByName("commercial")
                isDebuggable = false
            }
        }
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

dependencies {
    testImplementation("junit:junit:4.13.2")
}
