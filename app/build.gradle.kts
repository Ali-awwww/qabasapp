plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.secrets)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.google.services)
}

android {
  namespace = "com.qabas.app"
  compileSdk = 36

    defaultConfig {
    applicationId = "com.aistudio.qabas.kxmpzq"
    minSdk = 24
    targetSdk = 36
    // قناة التحديث: CI يحقن versionCode/versionName تلقائياً لكل بناء عبر -PappVersionCode
    // و -PappVersionName، فتتزايد الأكواد والإصدارات مع كل دفع ناجح (أساس دلتا xdelta).
    versionCode = (project.findProperty("appVersionCode") as? String)?.toIntOrNull() ?: 104
    versionName = (project.findProperty("appVersionName") as? String)?.takeIf { it.isNotBlank() } ?: "1.3.5"
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    // Safe secret resolution for GitHub OAuth keys
    val resolveSecret = { key: String ->
      val prop = project.findProperty(key) as? String
      if (!prop.isNullOrBlank()) prop
      else {
        val env = System.getenv(key)
        if (!env.isNullOrBlank()) env
        else {
          var found = ""
          val envFile = file("${rootDir}/.env")
          if (envFile.exists()) {
            envFile.forEachLine { line ->
              val trimmed = line.trim()
              if (trimmed.startsWith("$key=")) {
                found = trimmed.substringAfter("=").trim().removeSurrounding("\"").removeSurrounding("'")
              }
            }
          }
          found
        }
      }
    }

    buildConfigField("String", "AW_CLIENT_ID", "\"${resolveSecret("AW_CLIENT_ID")}\"")
    buildConfigField("String", "AW_CLIENT_SECRET", "\"${resolveSecret("AW_CLIENT_SECRET")}\"")
    buildConfigField("String", "QF_CLIENT_ID", "\"${resolveSecret("QF_CLIENT_ID")}\"")
    buildConfigField("String", "QF_CLIENT_SECRET", "\"${resolveSecret("QF_CLIENT_SECRET")}\"")
    buildConfigField("String", "GITHUB_CLIENT_ID", "\"${resolveSecret("GITHUB_CLIENT_ID")}\"")
    buildConfigField("String", "GITHUB_CLIENT_SECRET", "\"${resolveSecret("GITHUB_CLIENT_SECRET")}\"")
  }

  signingConfigs {
    create("debugConfig") {
      storeFile = file("${rootDir}/debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = false
      isShrinkResources = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("debugConfig")
    }
    debug {
      signingConfig = signingConfigs.getByName("debugConfig")
    }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }

  buildFeatures {
    compose = true
    buildConfig = true
  }

  testOptions { unitTests { isIncludeAndroidResources = true } }
  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
  }

  packaging {
    jniLibs {
      pickFirsts += listOf(
        "lib/arm64-v8a/libc++_shared.so",
        "lib/armeabi-v7a/libc++_shared.so",
        "lib/x86/libc++_shared.so",
        "lib/x86_64/libc++_shared.so"
      )
    }
  }
}

secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
  ignoreList.add("FIREBASE_APPCHECK_DEBUG_TOKEN")
  ignoreList.add("AW_CLIENT_ID")
  ignoreList.add("AW_CLIENT_SECRET")
  ignoreList.add("QF_CLIENT_ID")
  ignoreList.add("QF_CLIENT_SECRET")
  ignoreList.add("GITHUB_CLIENT_ID")
  ignoreList.add("GITHUB_CLIENT_SECRET")
}

// Ensure google-services.json always exists before GoogleServices tasks run
tasks.register("ensureGoogleServicesJson") {
    doLast {
        val googleServicesFile = file("google-services.json")
        if (!googleServicesFile.exists()) {
            googleServicesFile.writeText(
                """
                {
                  "project_info": {
                    "project_number": "297110176795",
                    "project_id": "gen-lang-client-0140553990",
                    "storage_bucket": "gen-lang-client-0140553990.firebasestorage.app"
                  },
                  "client": [
                    {
                      "client_info": {
                        "mobilesdk_app_id": "1:297110176795:android:931a343801fb2e9979e4be",
                        "android_client_info": {
                          "package_name": "com.aistudio.qabas.kxmpzq"
                        }
                      },
                      "oauth_client": [],
                      "api_key": [{ "current_key": "AIzaSyDyb2FmTOcx9eNi5gwT4x4oAknpXRZfca8" }],
                      "services": {}
                    }
                  ],
                  "configuration_version": "1"
                }
                """.trimIndent()
            )
        }
    }
}
tasks.matching { it.name.contains("GoogleServices", ignoreCase = true) && it.name != "ensureGoogleServicesJson" }.configureEach {
    dependsOn("ensureGoogleServicesJson")
}

tasks.withType<Test> {
    failFast = true
}

dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.foundation)
  implementation(libs.androidx.compose.foundation.layout)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.ui.text.google.fonts)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.core.splashscreen)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.androidx.browser)
  implementation(libs.androidx.media3.exoplayer)
  implementation(libs.androidx.media3.ui)
  implementation(libs.lottie.compose)
  implementation(libs.coil.compose)
  implementation(libs.converter.moshi)
  implementation(platform(libs.firebase.bom))
  implementation(libs.firebase.firestore)
  implementation(libs.firebase.auth)
  implementation(libs.firebase.ai)
  implementation(libs.firebase.appcheck.debug)
  implementation(libs.firebase.appcheck.playintegrity)
  implementation(libs.firebase.messaging)
  implementation(libs.androidx.work.runtime.ktx)
  implementation(libs.androidx.credentials)
  implementation(libs.androidx.credentials.play.services)
  implementation(libs.androidx.security.crypto)
  implementation(libs.googleid)
  // implementation(libs.firebase.analytics)
  // implementation(libs.firebase.crashlytics)
  // implementation(libs.firebase.appcheck.recaptcha)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.logging.interceptor)
  implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
  implementation(libs.retrofit)
  implementation(libs.ffmpeg.kit.full)
  implementation(libs.smart.exception.java)
  implementation(libs.billing.ktx)
  // Play In-App Update — تحديث سلس بلا "مصادر غير معروفة" (يتطلب نشر التطبيق على Google Play)
  implementation(libs.play.app.update)
  // implementation(libs.opencv) // Unused / removed to reduce APK size by >130MB and enable smooth emulator preview
  implementation(libs.androidx.camera.camera2)
  implementation(libs.androidx.camera.lifecycle)
  implementation(libs.androidx.camera.view)
  implementation(libs.androidx.camera.core)
  implementation(libs.androidx.camera.video)
  // implementation(libs.onnx.runtime.android) // Unused / removed to reduce APK size by >70MB
  implementation(platform(libs.supabase.bom))
  implementation(libs.supabase.postgrest)
  implementation(libs.supabase.auth)
  implementation(libs.supabase.storage)
  implementation(libs.supabase.functions)
  implementation(libs.ktor.client.android)
  implementation(libs.ktor.client.okhttp)
  implementation(libs.kotlinx.serialization.json)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.roborazzi)
  testImplementation(libs.roborazzi.compose)
  testImplementation(libs.roborazzi.junit.rule)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
  "ksp"(libs.moshi.kotlin.codegen)
}
