plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
    explicitApi()
}

// Pack de converters para kotlinx.serialization: JsonElement <-> String. Segundo consumidor del
// SPI (valida que los packs generalizan). NO necesita el plugin de serialización — JsonElement
// trae su propio serializer. El JSON POR-TIPO (Meta<->String) es un @Converter tuyo, por diseño.
dependencies {
    implementation(project(":spi"))
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
    testImplementation(libs.kctfork.ksp)
    testImplementation(project(":annotations"))
    testImplementation(project(":frontend-ksp"))
}

tasks.test { useJUnitPlatform() }
