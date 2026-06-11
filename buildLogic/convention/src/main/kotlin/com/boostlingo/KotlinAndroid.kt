package com.boostlingo

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.kotlin.dsl.assign
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension

/**
 * Configure base Android + Kotlin options for the library module.
 *
 * Deployment target is `minSdk 23` / JVM target 11 for the third-party-consumed
 * SDK. JVM unit tests run on the JUnit 5 (Jupiter) platform.
 */
internal fun Project.configureKotlinAndroid(androidExtension: LibraryExtension) {
    androidExtension.apply {
        compileSdk = 36

        defaultConfig {
            minSdk = 23
            testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }

        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_11
            targetCompatibility = JavaVersion.VERSION_11
        }

        testOptions {
            unitTests.all { it.useJUnitPlatform() }
            // JVM unit tests touch android.util.Log (via the Logger abstraction);
            // return default values instead of throwing "not mocked" so library
            // logic can be tested off-device.
            unitTests.isReturnDefaultValues = true
        }
    }

    configureKotlin()
}

/**
 * Configure base Android + Kotlin options for the application (quickstart demo) module.
 */
internal fun Project.configureKotlinAndroid(androidExtension: ApplicationExtension) {
    androidExtension.apply {
        compileSdk = 36

        defaultConfig {
            minSdk = 23
            testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }

        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_11
            targetCompatibility = JavaVersion.VERSION_11
        }

        testOptions {
            unitTests.all { it.useJUnitPlatform() }
            // JVM unit tests touch android.util.Log (via the Logger abstraction);
            // return default values instead of throwing "not mocked" so library
            // logic can be tested off-device.
            unitTests.isReturnDefaultValues = true
        }
    }

    configureKotlin()
}

/**
 * Configure base Kotlin options shared by every module.
 */
private fun Project.configureKotlin() = configure<KotlinAndroidProjectExtension> {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}
