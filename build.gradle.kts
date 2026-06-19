// Top-level build file — plugins declared via the version catalog, applied in modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.detekt)
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.kover)
}

subprojects {
    apply(plugin = "io.gitlab.arturbosch.detekt")
    apply(plugin = "org.jlleitschuh.gradle.ktlint")

    detekt {
        buildUponDefaultConfig = true
        config.setFrom(rootProject.files("config/detekt.yml"))
        parallel = true
    }
}

// Aggregate Kover coverage across every module that produces testable code.
dependencies {
    kover(project(":core"))
    kover(project(":ai"))
    kover(project(":data"))
    kover(project(":integrations"))
    kover(project(":feature:record"))
    kover(project(":feature:notes"))
    kover(project(":app"))
}
