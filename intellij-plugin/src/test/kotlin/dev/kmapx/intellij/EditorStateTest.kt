package dev.kmapx.intellij

import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * El ESTADO del editor alimentando al motor: la config global del build
 * (propuesta C, [KmapxBuildConfig]) y el índice colaborativo del proyecto (propuesta B,
 * [ProjectMappingIndex] → KMX004/KMX007 en el editor con quick-fix).
 */
class EditorStateTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(MappingInspection())
    }

    private fun kmxHighlights(): List<String> =
        myFixture.doHighlighting().mapNotNull { it.description }.filter { "KMX" in it }

    fun `test el kmapx onNull global de gradle properties silencia el KMX003 - la salvedad cerrada`() {
        myFixture.addFileToProject("gradle.properties", "kmapx.onNull=throw")
        myFixture.configureByText(
            "Mapping.kt",
            """
            package a
            import dev.kmapx.annotations.embedded.MapTo

            data class Dto(val nickname: String)

            @MapTo(Dto::class)
            data class Src(val nickname: String?)
            """.trimIndent(),
        )
        assertTrue("el global THROW debía silenciar: ${kmxHighlights()}", kmxHighlights().isEmpty())
    }

    fun `test el bloque kmapx del build gradle kts tambien aporta el global`() {
        myFixture.addFileToProject(
            "build.gradle.kts",
            """
            plugins { id("io.github.kuroxbyte.kmapx") }
            kmapx {
                onNull = "throw"
            }
            """.trimIndent(),
        )
        myFixture.configureByText(
            "Mapping.kt",
            """
            package a
            import dev.kmapx.annotations.embedded.MapTo

            data class Dto(val nickname: String)

            @MapTo(Dto::class)
            data class Src(val nickname: String?)
            """.trimIndent(),
        )
        assertTrue("el global del DSL debía silenciar: ${kmxHighlights()}", kmxHighlights().isEmpty())
    }

    fun `test el par anidado sin mapeo marca KMX007 y el fix declara el MapTo que falta`() {
        myFixture.configureByText(
            "Mapping.kt",
            """
            package a
            import dev.kmapx.annotations.embedded.MapTo

            data class Address(val city: String)
            data class AddressDto(val city: String)

            data class Dto(val address: AddressDto)

            @MapTo(Dto::class)
            data class Src(val address: Address)
            """.trimIndent(),
        )
        assertTrue("esperaba KMX007: ${kmxHighlights()}", kmxHighlights().any { "KMX007" in it })

        val fix = myFixture.getAllQuickFixes().first { "@MapTo(AddressDto::class)" in it.familyName }
        myFixture.launchAction(fix)
        val text = myFixture.file.text
        assertTrue("el fix debía anotar Address: $text", "@MapTo(AddressDto::class)\ndata class Address" in text)
        assertTrue("el KMX007 debía desaparecer: ${kmxHighlights()}", kmxHighlights().isEmpty())
    }

    fun `test el mapeo declarado en OTRO archivo resuelve el anidado sin marcar`() {
        myFixture.addFileToProject(
            "a/Address.kt",
            """
            package a
            import dev.kmapx.annotations.embedded.MapTo

            data class AddressDto(val city: String)

            @MapTo(AddressDto::class)
            data class Address(val city: String)
            """.trimIndent(),
        )
        myFixture.configureByText(
            "Mapping.kt",
            """
            package a
            import dev.kmapx.annotations.embedded.MapTo

            data class Dto(val address: AddressDto)

            @MapTo(Dto::class)
            data class Src(val address: Address)
            """.trimIndent(),
        )
        assertTrue("no debía marcar nada: ${kmxHighlights()}", kmxHighlights().isEmpty())
    }

    fun `test tipos incompatibles marcan KMX004 y un Converter del proyecto lo resuelve`() {
        myFixture.configureByText(
            "Mapping.kt",
            """
            package a
            import dev.kmapx.annotations.embedded.MapTo

            data class Dto(val total: Int)

            @MapTo(Dto::class)
            data class Src(val total: Long)
            """.trimIndent(),
        )
        assertTrue("esperaba KMX004 (narrowing): ${kmxHighlights()}", kmxHighlights().any { "KMX004" in it })

        myFixture.addFileToProject(
            "a/Converters.kt",
            """
            package a
            import dev.kmapx.annotations.Converter

            @Converter
            fun longToInt(value: Long): Int = value.toInt()
            """.trimIndent(),
        )
        assertTrue("el converter debía resolver el par: ${kmxHighlights()}", kmxHighlights().isEmpty())
    }

    fun `test el widening tambien vive en el editor`() {
        myFixture.configureByText(
            "Mapping.kt",
            """
            package a
            import dev.kmapx.annotations.embedded.MapTo

            data class Dto(val total: Long)

            @MapTo(Dto::class)
            data class Src(val total: Int)
            """.trimIndent(),
        )
        assertTrue("Int→Long es widening implícito: ${kmxHighlights()}", kmxHighlights().isEmpty())
    }
}
