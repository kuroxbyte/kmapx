plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.ksp)
}

// LINKEAR binarios Apple (test runners) requiere Xcode completo — hosts con solo
// Command Line Tools compilan klibs pero no linkean. Los targets Apple de este módulo (que SÍ
// tiene tests) se habilitan con -Pkmapx.ci.apple=true (job macOS de CI, o dev local con Xcode).
val appleCi = providers.gradleProperty("kmapx.ci.apple").orNull == "true"

kotlin {
    jvmToolchain(17)
    jvm()
    js(IR) { nodejs() }
    linuxX64()
    if (appleCi) {
        macosArm64()
        iosSimulatorArm64()
    }

    sourceSets {
        commonMain {
            dependencies {
                implementation(project(":annotations"))
                // La interfaz `Converts` que implementan los objects de @UseConverter.
                implementation(project(":runtime"))
            }
        }
        // Los tests de runtime viven en commonTest y corren POR TARGET contra el
        // código generado de cada uno (jvm ejecuta, js vía Node; linuxX64 cross-compila en
        // hosts no-Linux). Es la prueba de que el mismo @MapTo es equivalente en todos.
        commonTest {
            dependencies {
                implementation(libs.kotlin.test)
            }
        }
    }
}

// Reporte de cobertura bajo demanda (-Pkmapx.report=json,html). Apagado por defecto:
// con la opción activa el processor usa aggregating=true (costo documentado en la spec).
providers.gradleProperty("kmapx.report").orNull?.let { formats ->
    ksp {
        arg("kmapx.report", formats)
        arg("kmapx.module", "integration-tests")
    }
}

// KSP en KMP se configura POR TARGET (no "ksp(...)" a secas — esa es la config de jvm puro).
// El código se genera en el source set de cada target; commonMain compartido queda fuera de v1.
dependencies {
    add("kspJvm", project(":frontend-ksp"))
    add("kspJs", project(":frontend-ksp"))
    add("kspLinuxX64", project(":frontend-ksp"))
    if (appleCi) {
        add("kspMacosArm64", project(":frontend-ksp"))
        add("kspIosSimulatorArm64", project(":frontend-ksp"))
    }
}
