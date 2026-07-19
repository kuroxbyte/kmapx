package dev.kmapx.intellij

import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Los ENUMS en el editor: el `PsiAdapter` traduce entries y fallback, y
 * KMX026/KMX047/KMX023 (seguros por definición: no hay estado cross-proyecto) aparecen al
 * escribir, con quick-fixes que espejan los `Fix:` del compilador.
 */
class EnumEditorTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(MappingInspection())
    }

    private fun kmxHighlights(): List<String> =
        myFixture.doHighlighting().mapNotNull { it.description }.filter { "KMX" in it }

    fun `test entry del source sin par marca KMX026 en el editor`() {
        myFixture.configureByText(
            "Mapping.kt",
            """
            package a
            import dev.kmapx.annotations.embedded.MapTo

            enum class StatusDto { OPEN }

            @MapTo(StatusDto::class)
            enum class Status { OPEN, LEGACY }
            """.trimIndent(),
        )
        assertTrue("esperaba KMX026: ${kmxHighlights()}", kmxHighlights().any { "KMX026" in it })
    }

    fun `test el fix de KMX026 anota el entry con el MapEntry candidato`() {
        myFixture.configureByText(
            "Mapping.kt",
            """
            package a
            import dev.kmapx.annotations.embedded.MapTo

            enum class StatusDto { OPEN, ARCHIVED }

            @MapTo(StatusDto::class)
            enum class Status { OPEN, ARCHIVD }
            """.trimIndent(),
        )
        myFixture.doHighlighting()
        val fix = myFixture.getAllQuickFixes().first { """@MapEntry(target = "ARCHIVED")""" in it.familyName }
        myFixture.launchAction(fix)
        val text = myFixture.file.text
        assertTrue(
            "el fix debía anotar el entry: $text",
            Regex("""@MapEntry\(target = "ARCHIVED"\)\s+ARCHIVD""").containsMatchIn(text),
        )
        assertTrue("el fix debía importar MapEntry: $text", "import dev.kmapx.annotations.MapEntry" in text)
        assertTrue("el KMX026 debía desaparecer: ${kmxHighlights()}", kmxHighlights().isEmpty())
    }

    fun `test el fallback de clase roto marca KMX047 y el fix repara el string`() {
        myFixture.configureByText(
            "Mapping.kt",
            """
            package a
            import dev.kmapx.annotations.MapEntry
            import dev.kmapx.annotations.embedded.MapTo

            enum class StatusDto { OPEN, UNKNOWN }

            @MapTo(StatusDto::class)
            @MapEntry(target = "UNKNWN")
            enum class Status { OPEN, LEGACY }
            """.trimIndent(),
        )
        assertTrue("esperaba KMX047: ${kmxHighlights()}", kmxHighlights().any { "KMX047" in it })

        val fix = myFixture.getAllQuickFixes().first { it.familyName == "kmapx: cambiar a \"UNKNOWN\"" }
        myFixture.launchAction(fix)
        assertTrue(
            "el fix debía reparar el fallback: ${myFixture.file.text}",
            """@MapEntry(target = "UNKNOWN")""" in myFixture.file.text,
        )
        assertTrue("los KMX debían desaparecer: ${kmxHighlights()}", kmxHighlights().isEmpty())
    }

    fun `test entry extra del target es warning KMX023, no error`() {
        myFixture.configureByText(
            "Mapping.kt",
            """
            package a
            import dev.kmapx.annotations.embedded.MapTo

            enum class StatusDto { OPEN, CANCELLED }

            @MapTo(StatusDto::class)
            enum class Status { OPEN }
            """.trimIndent(),
        )
        assertTrue("esperaba KMX023: ${kmxHighlights()}", kmxHighlights().any { "KMX023" in it })
    }

    fun `test un par de enums con override y fallback validos no marca nada`() {
        myFixture.configureByText(
            "Mapping.kt",
            """
            package a
            import dev.kmapx.annotations.MapEntry
            import dev.kmapx.annotations.embedded.MapTo

            enum class StatusDto { OPEN, CRIMSON, UNKNOWN }

            @MapTo(StatusDto::class)
            @MapEntry(target = "UNKNOWN")
            enum class Status { OPEN, @MapEntry(target = "CRIMSON") RED, LEGACY }
            """.trimIndent(),
        )
        assertTrue("no debía marcar nada: ${kmxHighlights()}", kmxHighlights().isEmpty())
    }

    fun `test el metodo de mapper entre enums tambien marca KMX026`() {
        myFixture.configureByText(
            "Mapping.kt",
            """
            package a
            import dev.kmapx.annotations.contract.Mapper

            enum class Legacy { OPEN, ARCHIVED_V1 }
            enum class Status { OPEN }

            @Mapper
            interface StatusMapper {
                fun toStatus(legacy: Legacy): Status
            }
            """.trimIndent(),
        )
        assertTrue("esperaba KMX026 en contract: ${kmxHighlights()}", kmxHighlights().any { "KMX026" in it })
    }
}
