plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin { jvmToolchain(17) }

// Este módulo NO declara dependencias de KSP, KotlinPoet, Spring ni APIs de compilador.
dependencies {
    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
    testImplementation(project(":adapter-reflect"))
}

tasks.test {
    useJUnitPlatform()
    // Reenvía la system property al JVM de test (para `-Dkmapx.updateDocs=true`,
    // que regenera docs/referencia/diagnosticos.md — mismo patrón que los snapshots).
    System.getProperty("kmapx.updateDocs")?.let { systemProperty("kmapx.updateDocs", it) }
}
