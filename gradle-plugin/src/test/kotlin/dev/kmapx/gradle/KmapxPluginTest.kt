package dev.kmapx.gradle

import com.google.devtools.ksp.gradle.KspExtension
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.testfixtures.ProjectBuilder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class KmapxPluginTest {

    @Test
    fun `aplicar dev-kmapx crea la extension y aplica KSP`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("io.github.kuroxbyte.kmapx")

        assertNotNull(project.extensions.findByName("kmapx"), "falta la extensión kmapx")
        assertTrue(project.plugins.hasPlugin("com.google.devtools.ksp"), "no aplicó KSP")
    }

    @Test
    fun `en un modulo JVM cablea implementation annotations+runtime y ksp del processor`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("org.jetbrains.kotlin.jvm")
        project.plugins.apply("io.github.kuroxbyte.kmapx")

        val impl = project.configurations.getByName("implementation").dependencies
            .map { "${it.group}:${it.name}" }
        assertTrue("io.github.kuroxbyte:kmapx-annotations" in impl, impl.toString())
        assertTrue("io.github.kuroxbyte:kmapx-runtime" in impl, impl.toString())

        val ksp = project.configurations.getByName("ksp").dependencies.map { "${it.group}:${it.name}" }
        assertEquals(listOf("io.github.kuroxbyte:kmapx-frontend-ksp"), ksp)
    }

    @Test
    fun `el bloque kmapx traduce la config global a opciones de KSP`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("org.jetbrains.kotlin.jvm")
        project.plugins.apply("io.github.kuroxbyte.kmapx")

        project.extensions.configure(KmapxExtension::class.java) {
            it.onNull.set(OnNull.THROW)
            it.useSerialNames.set(true)
            it.warningsAsErrors.set(true)
            it.report("json")
        }
        (project as ProjectInternal).evaluate()  // dispara el afterEvaluate del plugin

        val args = project.extensions.getByType(KspExtension::class.java).arguments
        assertEquals("throw", args["kmapx.onNull"])
        assertEquals("true", args["kmapx.useSerialNames"])
        assertEquals("true", args["kmapx.warningsAsErrors"])
        assertEquals("json", args["kmapx.report"])
    }
}
