import com.android.build.api.dsl.LibraryExtension
import com.boostlingo.configureKotlinAndroid
import com.boostlingo.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

@Suppress("unused")
class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            // AGP 9 provides built-in Kotlin support, so org.jetbrains.kotlin.android
            // is intentionally NOT applied. See https://kotl.in/gradle/agp-built-in-kotlin
            apply(plugin = "com.android.library")
            apply(plugin = "org.jetbrains.kotlin.plugin.parcelize")

            extensions.configure<LibraryExtension> {
                configureKotlinAndroid(this)
            }

            dependencies {
                "testImplementation"(libs.findLibrary("kotlin-test-junit5").get())
                "testImplementation"(libs.findLibrary("mockk").get())
                "androidTestImplementation"(libs.findLibrary("androidx-test-ext-junit").get())
                "androidTestImplementation"(libs.findLibrary("androidx-test-espresso-core").get())
            }
        }
    }
}
