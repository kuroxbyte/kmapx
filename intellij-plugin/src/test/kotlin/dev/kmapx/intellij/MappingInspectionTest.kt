package dev.kmapx.intellij

import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * El motor REAL corriendo en el editor (adapter-psi), verificado headless.
 * Los casos espejan a los del harness de compilación: mismo motor, misma matriz.
 */
class MappingInspectionTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(MappingInspection())
    }

    private fun kmxHighlights(): List<String> =
        myFixture.doHighlighting().mapNotNull { it.description }.filter { "KMX" in it }

    fun `test T nullable a T no-nullable marca KMX003 en el editor`() {
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
        assertTrue("esperaba KMX003: ${kmxHighlights()}", kmxHighlights().any { "KMX003" in it })
    }

    fun `test el quick fix de KMX003 anota el parametro del target con THROW`() {
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
        myFixture.doHighlighting()
        val fix = myFixture.getAllQuickFixes().first { "OnNull.THROW" in it.familyName }
        myFixture.launchAction(fix)
        val text = myFixture.file.text
        assertTrue("el fix debía anotar el target: $text", "@MapField(onNull = OnNull.THROW)" in text)
        assertTrue("el fix debía importar MapField: $text", "import dev.kmapx.annotations.MapField" in text)
        assertTrue("el KMX003 debía desaparecer: ${kmxHighlights()}", kmxHighlights().isEmpty())
    }

    fun `test campo sin fuente marca KMX002 con did-you-mean del motor`() {
        myFixture.configureByText(
            "Mapping.kt",
            """
            package a
            import dev.kmapx.annotations.embedded.MapTo

            data class Dto(val firstname: String)

            @MapTo(Dto::class)
            data class Src(val firstnme: String)
            """.trimIndent(),
        )
        assertTrue(
            "esperaba KMX002 con did-you-mean: ${kmxHighlights()}",
            kmxHighlights().any { "KMX002" in it && "firstnme" in it },
        )
    }

    fun `test la politica de MAPEO onNull THROW silencia el KMX003 tambien en el editor`() {
        myFixture.configureByText(
            "Mapping.kt",
            """
            package a
            import dev.kmapx.annotations.embedded.MapTo
            import dev.kmapx.annotations.OnNull

            data class Dto(val nickname: String)

            @MapTo(Dto::class, onNull = OnNull.THROW)
            data class Src(val nickname: String?)
            """.trimIndent(),
        )
        assertTrue("no debía marcar nada: ${kmxHighlights()}", kmxHighlights().isEmpty())
    }

    fun `test la estrategia del campo silencia el KMX003`() {
        myFixture.configureByText(
            "Mapping.kt",
            """
            package a
            import dev.kmapx.annotations.embedded.MapTo
            import dev.kmapx.annotations.MapField
            import dev.kmapx.annotations.OnNull

            data class Dto(@MapField(onNull = OnNull.THROW) val nickname: String)

            @MapTo(Dto::class)
            data class Src(val nickname: String?)
            """.trimIndent(),
        )
        assertTrue("no debía marcar nada: ${kmxHighlights()}", kmxHighlights().isEmpty())
    }

    fun `test el ignore del MapTo silencia el KMX002 del campo excluido`() {
        myFixture.configureByText(
            "Mapping.kt",
            """
            package a
            import dev.kmapx.annotations.embedded.MapTo

            data class Dto(val name: String, val audit: String? = null)

            @MapTo(Dto::class, ignore = ["audit"])
            data class Src(val name: String)
            """.trimIndent(),
        )
        assertTrue("no debía marcar nada: ${kmxHighlights()}", kmxHighlights().isEmpty())
    }

    fun `test un mapeo valido no marca nada`() {
        myFixture.configureByText(
            "Mapping.kt",
            """
            package a
            import dev.kmapx.annotations.embedded.MapTo

            data class Dto(val name: String, val tags: List<String>)

            @MapTo(Dto::class)
            data class Src(val name: String, val tags: List<String>)
            """.trimIndent(),
        )
        assertTrue("no debía marcar nada: ${kmxHighlights()}", kmxHighlights().isEmpty())
    }
}
