package dev.kmapx.incremental

import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Último caso del checklist: incrementalidad. kctfork no ejercita el procesamiento
 * incremental de KSP (compila todo de cero), así que este test arma un proyecto consumidor
 * REAL vía Gradle TestKit que hace `includeBuild` de este repo, compila dos veces y verifica
 * que tocar una clase no relacionada NO regenera `<S>Mappings.kt`
 * (`Dependencies(aggregating = false, sourceFile)`).
 */
class IncrementalGenerationTest {

    @TempDir
    lateinit var projectDir: File

    private val kmapxRoot: String = System.getProperty("kmapx.rootDir")
    private val kotlinVersion: String = System.getProperty("kmapx.kotlinVersion")
    private val kspVersion: String = System.getProperty("kmapx.kspVersion")

    private fun file(path: String): File = File(projectDir, path).apply { parentFile.mkdirs() }

    @BeforeEach
    fun scaffold() {
        file("settings.gradle.kts").writeText(
            """
            pluginManagement {
                repositories { gradlePluginPortal(); mavenCentral(); google() }
            }
            dependencyResolutionManagement {
                repositories { mavenCentral(); google() }
            }
            rootProject.name = "kmapx-incremental-consumer"
            includeBuild("${kmapxRoot.replace("\\", "\\\\")}") {
                dependencySubstitution {
                    substitute(module("io.github.kuroxbyte:kmapx-annotations")).using(project(":annotations"))
                    substitute(module("io.github.kuroxbyte:kmapx-frontend-ksp")).using(project(":frontend-ksp"))
                }
            }
            """.trimIndent(),
        )
        file("build.gradle.kts").writeText(
            """
            plugins {
                kotlin("jvm") version "$kotlinVersion"
                id("com.google.devtools.ksp") version "$kspVersion"
            }
            kotlin { jvmToolchain(17) }
            dependencies {
                implementation("io.github.kuroxbyte:kmapx-annotations:0.2.0")
                ksp("io.github.kuroxbyte:kmapx-frontend-ksp:0.2.0")
            }
            """.trimIndent(),
        )
        file("gradle.properties").writeText(
            """
            ksp.useKSP2=true
            ksp.incremental=true
            org.gradle.jvmargs=-Xmx2g
            """.trimIndent(),
        )
        file("src/main/kotlin/sample/Person.kt").writeText(
            """
            package sample
            import dev.kmapx.annotations.embedded.MapTo

            data class PersonDto(val name: String)

            @MapTo(PersonDto::class)
            data class Person(val name: String)
            """.trimIndent(),
        )
        file("src/main/kotlin/sample/Unrelated.kt").writeText(
            """
            package sample

            data class Unrelated(val x: Int)
            """.trimIndent(),
        )
    }

    private fun runKsp() {
        GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("kspKotlin", "--stacktrace")
            .forwardOutput()
            .build()
    }

    @Test
    fun `tocar una clase no relacionada no regenera el archivo de mappings`() {
        runKsp()
        val generated = File(projectDir, "build/generated/ksp/main/kotlin/sample/PersonMappings.kt")
        assertTrue(generated.exists(), "PersonMappings.kt debió generarse en el primer build")
        val firstStamp = generated.lastModified()

        // Tocar una clase NO relacionada: el mapping no debe regenerarse.
        Thread.sleep(1_100) // resolución de mtime del filesystem
        file("src/main/kotlin/sample/Unrelated.kt").writeText(
            """
            package sample

            data class Unrelated(val x: Int, val y: Int)
            """.trimIndent(),
        )
        runKsp()
        assertEquals(
            firstStamp, generated.lastModified(),
            "PersonMappings.kt fue regenerado al tocar una clase no relacionada (incrementalidad rota)",
        )

        // Control (el assert anterior no es vacuo): tocar la clase anotada SÍ regenera.
        Thread.sleep(1_100)
        file("src/main/kotlin/sample/Person.kt").writeText(
            """
            package sample
            import dev.kmapx.annotations.embedded.MapTo

            data class PersonDto(val name: String)

            @MapTo(PersonDto::class)
            data class Person(val name: String, val age: Int = 0)
            """.trimIndent(),
        )
        runKsp()
        assertNotEquals(
            firstStamp, generated.lastModified(),
            "el control falló: tocar la clase anotada debía regenerar PersonMappings.kt",
        )
    }
}
