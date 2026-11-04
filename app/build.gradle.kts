plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.example.auralocalai"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.example.auralocalai"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        ndk {
            abiFilters.add("arm64-v8a")
        }
    }

    // One fixed signing key for every build (local and GitHub), so a new version installs over the
    // previous one without "app not installed" conflicts. This is a debug key, not a Play Store key.
    signingConfigs {
        getByName("debug") {
            storeFile = file("signing/shared-debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    buildFeatures {
      compose = true
      aidl = false
      buildConfig = true
      shaders = false
    }

    lint {
      abortOnError = false
      checkReleaseBuilds = false
    }

    

    packaging {
      resources {
        excludes += "/META-INF/{AL2.0,LGPL2.1}"
      }
      jniLibs {
        useLegacyPackaging = true
      }
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
  val composeBom = platform(libs.androidx.compose.bom)
  implementation(composeBom)
  androidTestImplementation(composeBom)

  // Core Android dependencies
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)

  // Arch Components
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)

  // Compose
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  // Tooling
  debugImplementation(libs.androidx.compose.ui.tooling)
  // Instrumented tests
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  debugImplementation(libs.androidx.compose.ui.test.manifest)

  // Local tests: jUnit, coroutines, Android runner, MockWebServer
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")

  // Instrumented tests: jUnit rules and runners
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.espresso.core)

  // Navigation
  implementation(libs.androidx.navigation3.ui)
  implementation(libs.androidx.navigation3.runtime)
  implementation(libs.androidx.lifecycle.viewmodel.navigation3)

  // Google AI Edge LiteRT for local LLM inference.
  // 0.18.x is required by current .litertlm bundles (litert-community ships
  // "needs LiteRT-LM 0.18.0 or newer" artifacts) and adds the chat-template
  // metadata handling plus ConversationConfig.chatTemplate / ModelInfo.
  implementation("com.google.ai.edge.litertlm:litertlm-android:0.18.1")

  // AndroidX Security Crypto for hardware-backed EncryptedSharedPreferences
  implementation("androidx.security:security-crypto:1.1.0-alpha06")


  implementation("androidx.compose.material:material-icons-core")
  implementation("androidx.compose.material:material-icons-extended")

  // Biometric app lock (fingerprint / device PIN)
  implementation("androidx.biometric:biometric:1.1.0")

  // WorkManager for the optional daily news download (daily briefing)
  implementation("androidx.work:work-runtime-ktx:2.9.1")

  // OkHttp for streaming network downloads
  implementation("com.squareup.okhttp3:okhttp:4.12.0")

  // Kotlinx Serialization JSON support
  implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

  // Offline OCR and Image Loading
  implementation("com.google.mlkit:text-recognition:16.0.1")
  // Offline translation of news headlines (ML Kit Translate + Language ID)
  implementation("com.google.mlkit:translate:17.0.3")
  implementation("com.google.mlkit:language-id:17.0.6")
  // Offline OCR for Cyrillic (Bulgarian); language data is downloaded once on first use.
  implementation("cz.adaptech.tesseract4android:tesseract4android-openmp:4.9.0")
  implementation("io.coil-kt:coil-compose:2.6.0")
}
