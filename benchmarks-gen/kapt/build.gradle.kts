plugins {
    alias(libs.plugins.kotlin.jvm)
    kotlin("kapt")
}
kotlin { jvmToolchain(17) }
// Módulo de benchmark de GENERACIÓN — solo MapStruct/kapt. Mismo modelo que el módulo ksp.
dependencies {
    implementation(libs.mapstruct)
    kapt(libs.mapstruct.processor)
}
