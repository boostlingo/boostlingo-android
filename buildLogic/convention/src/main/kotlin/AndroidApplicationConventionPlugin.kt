import com.android.build.api.dsl.ApplicationExtension
import com.boostlingo.configureKotlinAndroid
import com.boostlingo.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

@Suppress("unused")
class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            // AGP 9 provides built-in Kotlin support, so org.jetbrains.kotlin.android
            // is intentionally NOT applied. See https://kotl.in/gradle/agp-built-in-kotlin
            apply(plugin = "com.android.application")

            extensions.configure<ApplicationExtension> {
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
