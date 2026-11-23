plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "dev.omalauncher"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.omalauncher"
        minSdk = 29
        targetSdk = 35
        versionCode = 3
        versionName = "1.0.2"
    }

    // The Lite launcher uses platform Android Views; no Compose runtime is packaged.
    buildFeatures {
        buildConfig = true
        compose = false
    }

    sourceSets {
        getByName("main") {
            java.setSrcDirs(listOf("src/lite/kotlin"))
            assets.setSrcDirs(emptyList<String>())
            jniLibs.setSrcDirs(emptyList<String>())
        }
    }

    flavorDimensions += "store"
    productFlavors {
        create("foss") {
            dimension = "store"
            isDefault = true
        }
        create("play") {
            dimension = "store"
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    buildTypes {
        debug {
            isPseudoLocalesEnabled = true
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }
}

dependencies {
    testImplementation(libs.junit)
}
