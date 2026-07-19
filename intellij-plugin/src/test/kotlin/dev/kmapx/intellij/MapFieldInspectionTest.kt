package dev.kmapx.intellij

import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * La inspección KMX en el editor, verificada headless. Las aserciones usan los CÓDIGOS
 * (KMXnnn) — los textos vienen de las factories del core y ya tienen su propio contrato.
 */
class MapFieldInspectionTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(MapFieldInspection())
    }

    private fun kmxHighlights(): List<String> =
        myFixture.doHighlighting().mapNotNull { it.description }.filter { "KMX" in it }

    fun `test LITERAL sin default marca KMX038 y el quick fix agrega el default`() {
        myFixture.configureByText(
            "Dto.kt",
            """
            package a
            import dev.kmapx.annotations.MapField
            import dev.kmapx.annotations.OnNull

            data class Dto(@MapField(onNull = OnNull.LITERAL) val nickname: String)
            """.trimIndent(),
        )
        assertTrue("esperaba KMX038: ${kmxHighlights()}", kmxHighlights().any { "KMX038" in it })

        val fix = myFixture.getAllQuickFixes().first { it.familyName.contains("agregar default") }
        myFixture.launchAction(fix)
        assertTrue(
            "el fix debía agregar default: ${myFixture.file.text}",
            """default = """"" in myFixture.file.text,
        )
    }

    fun `test default con THROW marca KMX039 warning y quitar default lo resuelve`() {
        myFixture.configureByText(
            "Dto.kt",
            """
            package a
            import dev.kmapx.annotations.MapField
            import dev.kmapx.annotations.OnNull

            data class Dto(@MapField(onNull = OnNull.THROW, default = "x") val nickname: String)
            """.trimIndent(),
        )
        assertTrue("esperaba KMX039: ${kmxHighlights()}", kmxHighlights().any { "KMX039" in it })

        val fix = myFixture.getAllQuickFixes().first { it.familyName.contains("quitar default") }
        myFixture.launchAction(fix)
        assertTrue("el fix debía quitar el default: ${myFixture.file.text}", "default" !in myFixture.file.text)
        assertTrue("los KMX debían desaparecer: ${kmxHighlights()}", kmxHighlights().isEmpty())
    }

    fun `test target en sede de campo marca KMX036 y el fix lo quita`() {
        myFixture.configureByText(
            "Dto.kt",
            """
            package a
            import dev.kmapx.annotations.MapField

            data class Dto(@MapField(target = "nickname", from = "nick") val nickname: String)
            """.trimIndent(),
        )
        assertTrue("esperaba KMX036: ${kmxHighlights()}", kmxHighlights().any { "KMX036" in it })

        val fix = myFixture.getAllQuickFixes().first { it.familyName.contains("quitar target") }
        myFixture.launchAction(fix)
        assertTrue("el fix debía quitar target: ${myFixture.file.text}", "target" !in myFixture.file.text)
    }

    fun `test ignore junto a otros aspectos marca KMX043`() {
        myFixture.configureByText(
            "Dto.kt",
            """
            package a
            import dev.kmapx.annotations.MapField

            data class Dto(@MapField(ignore = true, from = "other") val nickname: String? = null)
            """.trimIndent(),
        )
        assertTrue("esperaba KMX043: ${kmxHighlights()}", kmxHighlights().any { "KMX043" in it })
    }

    fun `test target inexistente sugiere did-you-mean y el fix corrige el string`() {
        myFixture.addFileToProject(
            "a/CustomerDto.kt",
            """
            package a
            data class CustomerDto(val displayName: String, val age: Int)
            """.trimIndent(),
        )
        myFixture.configureByText(
            "CustomerMapper.kt",
            """
            package a
            import dev.kmapx.annotations.contract.Mapper
            import dev.kmapx.annotations.MapField

            @Mapper
            interface CustomerMapper {
                @MapField(target = "displayNme", from = "name")
                fun toDto(c: Customer): CustomerDto
            }
            """.trimIndent(),
        )
        assertTrue("esperaba KMX011 con did-you-mean: ${kmxHighlights()}",
            kmxHighlights().any { "KMX011" in it && "displayName" in it })

        val fix = myFixture.getAllQuickFixes().first { it.familyName.contains("displayName") }
        myFixture.launchAction(fix)
        assertTrue(
            "el fix debía corregir el string: ${myFixture.file.text}",
            """target = "displayName"""" in myFixture.file.text,
        )
    }

    fun `test una MapField valida no marca nada`() {
        myFixture.addFileToProject(
            "a/CustomerDto.kt",
            "package a\ndata class CustomerDto(val displayName: String)\n",
        )
        myFixture.configureByText(
            "CustomerMapper.kt",
            """
            package a
            import dev.kmapx.annotations.contract.Mapper
            import dev.kmapx.annotations.MapField

            @Mapper
            interface CustomerMapper {
                @MapField(target = "displayName", from = "name")
                fun toDto(c: Customer): CustomerDto
            }
            """.trimIndent(),
        )
        assertTrue("no debía marcar nada: ${kmxHighlights()}", kmxHighlights().isEmpty())
    }

    fun `test un segmento intermedio roto de una ruta reporta did-you-mean (v06)`() {
        myFixture.addFileToProject(
            "a/Customer.kt",
            """
            package a
            data class Address(val city: String)
            data class Customer(val address: Address)
            """.trimIndent(),
        )
        myFixture.configureByText(
            "M.kt",
            """
            package a
            import dev.kmapx.annotations.contract.Mapper
            import dev.kmapx.annotations.MapField

            @Mapper
            interface M {
                @MapField(target = "city", from = "address.cty")
                fun toDto(c: Customer): CityDto
            }
            """.trimIndent(),
        )
        val highlights = myFixture.doHighlighting().mapNotNull { it.description }.filter { "KMX" in it }
        assertTrue(
            "esperaba el segmento cty con did-you-mean city: ${'$'}highlights",
            highlights.any { "cty" in it && "city" in it },
        )
        val fix = myFixture.getAllQuickFixes().first { it.familyName == "kmapx: cambiar a \"city\"" }
        myFixture.launchAction(fix)
        assertTrue("el fix debía reparar SOLO el segmento", "from = \"address.city\"" in myFixture.file.text)
    }
}
