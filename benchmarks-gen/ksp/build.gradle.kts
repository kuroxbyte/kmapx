plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ksp)
}
kotlin { jvmToolchain(17) }
// Módulo de benchmark de GENERACIÓN — solo kmapx/KSP. No publicado, no en CI.
dependencies {
    implementation(project(":annotations"))
    implementation(project(":runtime"))
    ksp(project(":frontend-ksp"))
}
