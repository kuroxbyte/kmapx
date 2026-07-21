package dev.kmapx.gradle

import com.google.devtools.ksp.gradle.KspExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType

/**
 * `id("io.github.kuroxbyte.kmapx")`. Reemplaza el cableado manual (annotations + runtime + `ksp(...)` por
 * módulo y por target) por una línea. Aplica KSP, agrega las dependencias correctas según el plugin
 * de Kotlin presente (JVM o Multiplatform) y traduce el bloque `kmapx { }` a opciones del processor.
 */
public class KmapxPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        val ext = project.extensions.create("kmapx", KmapxExtension::class.java)
        project.plugins.apply("com.google.devtools.ksp")

        val annotations = "io.github.kuroxbyte:kmapx-annotations:$KMAPX_VERSION"
        val runtime = "io.github.kuroxbyte:kmapx-runtime:$KMAPX_VERSION"
        val processor = "io.github.kuroxbyte:kmapx-frontend-ksp:$KMAPX_VERSION"

        // JVM (sirve también a Android): implementation + ksp en la config estándar.
        project.plugins.withId("org.jetbrains.kotlin.jvm") {
            project.dependencies.add("implementation", annotations)
            project.dependencies.add("implementation", runtime)
            project.dependencies.add("ksp", processor)
        }

        // KMP: las anotaciones/runtime van a commonMain; el processor (JVM-only) se cablea POR
        // target — un `kspJvm`/`kspJs`/`kspLinuxX64`… por cada target no-común.
        project.plugins.withId("org.jetbrains.kotlin.multiplatform") {
            val kotlin = project.extensions.getByType(KotlinMultiplatformExtension::class.java)
            kotlin.sourceSets.getByName("commonMain").dependencies {
                implementation(annotations)
                implementation(runtime)
            }
            project.afterEvaluate {
                kotlin.targets
                    .filter { it.platformType != KotlinPlatformType.common }
                    .forEach { target ->
                        val cfg = "ksp${target.name.replaceFirstChar { c -> c.uppercase() }}"
                        project.dependencies.add(cfg, processor)
                    }
            }
        }

        // Bloque `kmapx { }` → opciones del processor KSP.
        project.afterEvaluate {
            val ksp = project.extensions.getByType(KspExtension::class.java)
            val formats = ext.report.getOrElse(emptyList())
            if (formats.isNotEmpty()) ksp.arg("kmapx.report", formats.joinToString(","))
            ext.moduleName.orNull?.let { ksp.arg("kmapx.module", it) }

            // Config global — solo se emite la opción si el usuario la fijó (defaults del processor).
            ext.useSerialNames.orNull?.let { ksp.arg("kmapx.useSerialNames", it.toString()) }
            ext.warningsAsErrors.orNull?.let { ksp.arg("kmapx.warningsAsErrors", it.toString()) }
            ext.onNull.orNull?.let { ksp.arg("kmapx.onNull", it.name.lowercase()) }
        }
    }

    public companion object {
        /** Coordenadas de los artefactos kmapx a consumir. Sube con cada release. */
        public const val KMAPX_VERSION: String = "0.2.0"
    }
}
