package dev.kmapx.arch

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.verify.assertFalse
import org.junit.jupiter.api.Test

/**
 * Candado 2: la frontera del core como test, no como convención.
 * Si alguien agrega "temporalmente" una dependencia de compilador al core, este test la detecta
 * aunque Gradle la hubiera permitido.
 */
class CoreBoundaryTest {

    private val forbiddenPrefixes = listOf(
        "com.google.devtools.ksp",
        "com.squareup.kotlinpoet",
        "org.springframework",
        "org.jetbrains.kotlin.ir",
        "org.jetbrains.kotlin.backend",
    )

    @Test
    fun `el core no importa APIs de compilador ni frameworks`() {
        Konsist
            .scopeFromProject(moduleName = "core")
            .files
            .assertFalse { file ->
                file.imports.any { import ->
                    forbiddenPrefixes.any { prefix -> import.name.startsWith(prefix) }
                }
            }
    }

    @Test
    fun `frontend-ksp no importa KotlinPoet`() {
        Konsist
            .scopeFromProject(moduleName = "frontend-ksp")
            .files
            .assertFalse { file ->
                file.imports.any { it.name.startsWith("com.squareup.kotlinpoet") }
            }
    }

    @Test
    fun `los modos embedded y contract no se importan entre si`() {
        // La separación de modos es LÓGICA (paquetes), no física (módulos) — se descartó
        // subproyectos. Esta regla es lo que la vuelve auditable: cada modo solo puede hablar
        // con la frontera compartida (su paquete raíz). Aplica a frontend-ksp Y a annotations.
        Konsist
            .scopeFromProject()
            .files
            .assertFalse { file ->
                val pkg = file.packagee?.name.orEmpty()
                when {
                    pkg.startsWith("dev.kmapx.") && ".embedded" in pkg ->
                        file.imports.any { it.name.startsWith("dev.kmapx.") && ".contract." in it.name }
                    pkg.startsWith("dev.kmapx.") && ".contract" in pkg ->
                        file.imports.any { it.name.startsWith("dev.kmapx.") && ".embedded." in it.name }
                    else -> false
                }
            }
    }

    @Test
    fun `annotations no importa nada fuera de kotlin stdlib`() {
        // Los imports INTRA-módulo (dev.kmapx.annotations.*) son legítimos desde la separación
        // embedded/contract; la regla protege contra dependencias EXTERNAS.
        Konsist
            .scopeFromProject(moduleName = "annotations")
            .files
            .assertFalse { file ->
                file.imports.any {
                    !it.name.startsWith("kotlin.") && !it.name.startsWith("dev.kmapx.annotations.")
                }
            }
    }
}
