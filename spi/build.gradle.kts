plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
    explicitApi()
}

// El SPI del processor: interfaces que un tercero implementa para CONTRIBUIR entradas al motor
// (converters, alias). JVM-only a propósito — vive en el classpath de KSP, nunca en tus targets.
