plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "work.ranjit.nfctags.wear"
    compileSdk = 36

    defaultConfig {
        applicationId = "work.ranjit.nfctags"
        minSdk = 30
        targetSdk = 36
        versionCode = 2018
        versionName = "2.3.4"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
      compose = true
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
  val composeBom = platform(libs.androidx.compose.bom)
  implementation(composeBom)

  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)

  // Compose for Wear OS
  implementation(libs.wear.compose.material)
  implementation(libs.wear.compose.foundation)
  implementation(libs.wear.compose.navigation)

  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material.icons.extended)

  // Wearable Data Layer API
  implementation(libs.play.services.wearable)
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2")

  // JSON Serialization
  implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
}
