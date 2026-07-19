plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin { jvmToolchain(17) }

dependencies {
    testImplementation(gradleTestKit())
    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
}

tasks.test {
    useJUnitPlatform()
    // El test arma un proyecto consumidor temporal que hace includeBuild de este repo:
    systemProperty("kmapx.rootDir", rootDir.absolutePath)
    systemProperty("kmapx.kotlinVersion", libs.versions.kotlin.get())
    systemProperty("kmapx.kspVersion", libs.versions.ksp.get())
}
