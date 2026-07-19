import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    `maven-publish`
}

// Matriz de targets v1. Android consume el artefacto JVM (annotations no tiene
// dependencias de plataforma). Los targets Apple requieren host macOS con Xcode (CI job macos);
// en hosts sin toolchain Apple, Kotlin los deshabilita con warning y el resto compila igual.
kotlin {
    jvmToolchain(17)
    jvm()
    js(IR) { browser(); nodejs() }
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs { nodejs() }
    linuxX64()
    mingwX64()
    macosX64()
    macosArm64()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain { }
    }
}

// Verificación de metadata: publicar a un repo local del build y revisar que el
// `.module` liste todas las variantes: ./gradlew :annotations:publishAllPublicationsToBuildRepository
publishing {
    repositories {
        maven {
            name = "build"
            url = uri(layout.buildDirectory.dir("repo"))
        }
    }
}
