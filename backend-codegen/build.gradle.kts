plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin { jvmToolchain(17) }

dependencies {
    api(project(":core"))
    implementation(libs.kotlinpoet)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
    testImplementation(project(":adapter-reflect"))
}

tasks.test {
    useJUnitPlatform()
    // Reenvía la system property al JVM de test (para `-Dkmapx.updateSnapshots=true`).
    System.getProperty("kmapx.updateSnapshots")?.let { systemProperty("kmapx.updateSnapshots", it) }
}
