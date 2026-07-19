import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    `maven-publish`
}

// El runtime deja de ser vacío: UNA interfaz (`Converts`) que sostiene los converters calificados.
// Misma matriz de targets que `annotations`; cero dependencias de plataforma.
// Regla de crecimiento: cualquier adición futura requiere una decisión de diseño deliberada.
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

publishing {
    repositories {
        maven {
            name = "build"
            url = uri(layout.buildDirectory.dir("repo"))
        }
    }
}
