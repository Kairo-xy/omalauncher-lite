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
        versionCode = 2
        versionName = "1.0.1"
    }
    buildFeatures { buildConfig = true; compose = true }
    sourceSets {
        getByName("main") {
            java.setSrcDirs(listOf("src/lite/kotlin"))
            assets.setSrcDirs(emptyList<String>())
            jniLibs.setSrcDirs(emptyList<String>())
        }
    }
    flavorDimensions += "store"
    productFlavors {
        create("foss") { dimension = "store"; isDefault = true }
        create("play") { dimension = "store" }
    }
    testOptions { unitTests.isReturnDefaultValues = true }
    buildTypes {
        debug { isPseudoLocalesEnabled = true }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }
}
dependencies {
    implementation(libs.androidx.activity.compose)
    implementation(libs.compose.runtime)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui)
    testImplementation(libs.junit)
}
