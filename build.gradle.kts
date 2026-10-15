import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.DetektCreateBaselineTask

plugins {
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.sqldelight) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.spotless) apply false
}

val staticAnalysisModules = setOf(":composeApp", ":shared")

subprojects {
    if (path in staticAnalysisModules) {
        val libs = rootProject.extensions.getByType<VersionCatalogsExtension>().named("libs")
        apply(plugin = libs.findPlugin("detekt").get().get().pluginId)
        apply(plugin = libs.findPlugin("ktlint").get().get().pluginId)
        apply(plugin = libs.findPlugin("spotless").get().get().pluginId)

        extensions.configure(io.gitlab.arturbosch.detekt.extensions.DetektExtension::class) {
            config.setFrom(files("${rootDir}/config/detekt/detekt.yml"))
            buildUponDefaultConfig = true
            autoCorrect = false
            basePath = rootDir.absolutePath
        }
    }
}

tasks.register<Detekt>("detektAll") {
    group = "verification"
    description = "Runs detekt across composeApp and shared."
    staticAnalysisModules.forEach { module ->
        dependsOn("$module:detekt")
    }
}

tasks.register<DetektCreateBaselineTask>("detektBaseline") {
    group = "verification"
    description = "Generates a detekt baseline for composeApp and shared."
    staticAnalysisModules.forEach { module ->
        dependsOn("$module:detektBaseline")
    }
}
