plugins {
    alias(libs.plugins.boostlingo.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.boostlingo.android.quickstart"

    signingConfigs {
        create("release") {
            storeFile = file("../release.keystore")
            storePassword = System.getenv("KEYSTORE_PASSWORD")
            keyAlias = System.getenv("KEY_ALIAS")
            keyPassword = System.getenv("KEY_PASSWORD")
        }
    }

    defaultConfig {
        applicationId = "com.boostlingo.android.quickstart"
        targetSdk = 36
        versionCode = 200
        versionName = "2.0.0"
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)

    // Jetpack Compose (demo UI)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.bundles.compose)
    implementation(libs.androidx.activity.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Material Components — supplies the XML window theme (Theme.Boostlingo)
    implementation(libs.android.material)

    // SignalR
    implementation(libs.signalr)

    // Rx
    implementation(libs.bundles.rxjava)

    // Retrofit
    implementation(libs.bundles.retrofit)

    // OkHttp
    implementation(libs.bundles.okhttp)

    // Twilio (Voice / Video / AudioSwitch)
    implementation(libs.bundles.twilio)

    if (findProject(":boostlingo") != null) {
        implementation(project(":boostlingo"))
    } else {
        implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("boostlingo-release.aar"))))
    }
}
