package dev.kmapx.intellij

import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * El motor REAL en el editor sobre el modo CONTRACT (adapter-psi): los métodos de
 * mapeo de un `@Mapper`, con la cascada método > mapper > profile y las abstenciones documentadas.
 */
class ContractMappingInspectionTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(MappingInspection())
    }

    private fun kmxHighlights(): List<String> =
        myFixture.doHighlighting().mapNotNull { it.description }.filter { "KMX" in it }

    fun `test metodo de mapper con T nullable a no-nullable marca KMX003`() {
        myFixture.configureByText(
            "Mapping.kt",
            """
            package a
            import dev.kmapx.annotations.contract.Mapper

            data class Order(val nickname: String?)
            data class OrderDto(val nickname: String)

            @Mapper
            interface OrderMapper {
                fun toDto(order: Order): OrderDto
            }
            """.trimIndent(),
        )
        assertTrue("esperaba KMX003: ${kmxHighlights()}", kmxHighlights().any { "KMX003" in it })
    }

    fun `test el quick fix de contract anota el METODO con target y THROW`() {
        myFixture.configureByText(
            "Mapping.kt",
            """
            package a
            import dev.kmapx.annotations.contract.Mapper

            data class Order(val nickname: String?)
            data class OrderDto(val nickname: String)

            @Mapper
            interface OrderMapper {
                fun toDto(order: Order): OrderDto
            }
            """.trimIndent(),
        )
        myFixture.doHighlighting()
        val fix = myFixture.getAllQuickFixes().first { "OnNull.THROW" in it.familyName }
        myFixture.launchAction(fix)
        val text = myFixture.file.text
        assertTrue(
            "el fix debía anotar el método: $text",
            """@MapField(target = "nickname", onNull = OnNull.THROW)""" in text,
        )
        assertTrue("el fix debía importar MapField: $text", "import dev.kmapx.annotations.MapField" in text)
        assertTrue("el KMX003 debía desaparecer: ${kmxHighlights()}", kmxHighlights().isEmpty())
    }

    fun `test campo sin fuente en contract marca KMX002 con did-you-mean`() {
        myFixture.configureByText(
            "Mapping.kt",
            """
            package a
            import dev.kmapx.annotations.contract.Mapper

            data class Order(val firstnme: String)
            data class OrderDto(val firstname: String)

            @Mapper
            interface OrderMapper {
                fun toDto(order: Order): OrderDto
            }
            """.trimIndent(),
        )
        assertTrue(
            "esperaba KMX002 con did-you-mean: ${kmxHighlights()}",
            kmxHighlights().any { "KMX002" in it && "firstnme" in it },
        )
    }

    fun `test el onNull del Mapper silencia el KMX003 tambien en el editor`() {
        myFixture.configureByText(
            "Mapping.kt",
            """
            package a
            import dev.kmapx.annotations.contract.Mapper
            import dev.kmapx.annotations.OnNull

            data class Order(val nickname: String?)
            data class OrderDto(val nickname: String)

            @Mapper(onNull = OnNull.THROW)
            interface OrderMapper {
                fun toDto(order: Order): OrderDto
            }
            """.trimIndent(),
        )
        assertTrue("no debía marcar nada: ${kmxHighlights()}", kmxHighlights().isEmpty())
    }

    fun `test la MapField de metodo con onNull THROW silencia el KMX003`() {
        myFixture.configureByText(
            "Mapping.kt",
            """
            package a
            import dev.kmapx.annotations.contract.Mapper
            import dev.kmapx.annotations.MapField
            import dev.kmapx.annotations.OnNull

            data class Order(val nickname: String?)
            data class OrderDto(val nickname: String)

            @Mapper
            interface OrderMapper {
                @MapField(target = "nickname", onNull = OnNull.THROW)
                fun toDto(order: Order): OrderDto
            }
            """.trimIndent(),
        )
        assertTrue("no debía marcar nada: ${kmxHighlights()}", kmxHighlights().isEmpty())
    }

    fun `test el profile MapperConfig con onNull THROW silencia el KMX003`() {
        myFixture.configureByText(
            "Mapping.kt",
            """
            package a
            import dev.kmapx.annotations.contract.Mapper
            import dev.kmapx.annotations.contract.MapperConfig
            import dev.kmapx.annotations.OnNull

            @MapperConfig(onNull = OnNull.THROW)
            interface CompanyProfile

            data class Order(val nickname: String?)
            data class OrderDto(val nickname: String)

            @Mapper(config = CompanyProfile::class)
            interface OrderMapper {
                fun toDto(order: Order): OrderDto
            }
            """.trimIndent(),
        )
        assertTrue("no debía marcar nada: ${kmxHighlights()}", kmxHighlights().isEmpty())
    }

    fun `test el ignore del Mapper silencia el KMX002 del campo excluido`() {
        myFixture.configureByText(
            "Mapping.kt",
            """
            package a
            import dev.kmapx.annotations.contract.Mapper

            data class Order(val name: String)
            data class OrderDto(val name: String, val audit: String? = null)

            @Mapper(ignore = ["audit"])
            interface OrderMapper {
                fun toDto(order: Order): OrderDto
            }
            """.trimIndent(),
        )
        assertTrue("no debía marcar nada: ${kmxHighlights()}", kmxHighlights().isEmpty())
    }

    fun `test con useSerialNames la inspeccion de mapeo se abstiene`() {
        // v0.8: patch e @InverseOf YA se inspeccionan (ContractShapesTest); la abstención que
        // queda es useSerialNames — el adapter no lee @SerialName y marcaría falsos KMX002.
        myFixture.configureByText(
            "Mapping.kt",
            """
            package a
            import dev.kmapx.annotations.contract.Mapper

            data class Order(val nickname: String?)
            data class OrderDto(val nickname: String)

            @Mapper(useSerialNames = true)
            interface SerialMapper {
                fun toDto(order: Order): OrderDto
            }
            """.trimIndent(),
        )
        assertTrue("no debía inspeccionar el mapeo: ${kmxHighlights()}", kmxHighlights().isEmpty())
    }
}
