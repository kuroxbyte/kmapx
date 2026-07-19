package dev.kmapx.intellij

import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Las FORMAS restantes de contract en el editor: colecciones (KMX046 con
 * quick-fix que declara el método del elemento), patch (`resolvePatch` real) e
 * `@InverseOf` (las validaciones locales de KMX045).
 */
class ContractShapesTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(MappingInspection())
    }

    private fun kmxHighlights(): List<String> =
        myFixture.doHighlighting().mapNotNull { it.description }.filter { "KMX" in it }

    fun `test metodo de coleccion sin mapeo del elemento marca KMX046 y el fix declara el metodo`() {
        myFixture.configureByText(
            "Mapping.kt",
            """
            package a
            import dev.kmapx.annotations.contract.Mapper

            data class Order(val id: String)
            data class OrderDto(val id: String)

            @Mapper
            interface OrderMapper {
                fun toDtos(orders: List<Order>): List<OrderDto>
            }
            """.trimIndent(),
        )
        assertTrue("esperaba KMX046: ${kmxHighlights()}", kmxHighlights().any { "KMX046" in it })

        val fix = myFixture.getAllQuickFixes().first { "declarar fun toOrderDto" in it.familyName }
        myFixture.launchAction(fix)
        val text = myFixture.file.text
        assertTrue("el fix debía declarar el método: $text", "fun toOrderDto(value: Order): OrderDto" in text)
        assertTrue("el KMX046 debía desaparecer: ${kmxHighlights()}", kmxHighlights().isEmpty())
    }

    fun `test el cruce de contenedor marca KMX046 con detalle`() {
        myFixture.configureByText(
            "Mapping.kt",
            """
            package a
            import dev.kmapx.annotations.contract.Mapper

            data class Order(val id: String)
            data class OrderDto(val id: String)

            @Mapper
            interface OrderMapper {
                fun toDto(order: Order): OrderDto
                fun toDtos(orders: List<Order>): Set<OrderDto>
            }
            """.trimIndent(),
        )
        assertTrue(
            "esperaba KMX046 con detalle de cruce: ${kmxHighlights()}",
            kmxHighlights().any { "KMX046" in it && "outside the closed list" in it },
        )
    }

    fun `test la coleccion que delega en el metodo hermano no marca nada`() {
        myFixture.configureByText(
            "Mapping.kt",
            """
            package a
            import dev.kmapx.annotations.contract.Mapper

            data class Order(val id: String)
            data class OrderDto(val id: String)

            @Mapper
            interface OrderMapper {
                fun toDto(order: Order): OrderDto
                fun toDtos(orders: List<Order>): List<OrderDto>
            }
            """.trimIndent(),
        )
        assertTrue("no debía marcar nada: ${kmxHighlights()}", kmxHighlights().isEmpty())
    }

    fun `test el patch con target que no es data class marca KMX012`() {
        myFixture.configureByText(
            "Mapping.kt",
            """
            package a
            import dev.kmapx.annotations.contract.Mapper

            class Customer(val name: String)
            data class CustomerPatch(val name: String?)

            @Mapper
            interface CustomerMapper {
                fun apply(target: Customer, patch: CustomerPatch): Customer
            }
            """.trimIndent(),
        )
        assertTrue("esperaba KMX012: ${kmxHighlights()}", kmxHighlights().any { "KMX012" in it })
    }

    fun `test el patch valido no marca nada`() {
        myFixture.configureByText(
            "Mapping.kt",
            """
            package a
            import dev.kmapx.annotations.contract.Mapper

            data class Customer(val name: String)
            data class CustomerPatch(val name: String?)

            @Mapper
            interface CustomerMapper {
                fun apply(target: Customer, patch: CustomerPatch): Customer
            }
            """.trimIndent(),
        )
        assertTrue("no debía marcar nada: ${kmxHighlights()}", kmxHighlights().isEmpty())
    }

    fun `test InverseOf sin forward marca KMX045 en el editor`() {
        myFixture.configureByText(
            "Mapping.kt",
            """
            package a
            import dev.kmapx.annotations.contract.Mapper
            import dev.kmapx.annotations.contract.InverseOf

            data class Order(val id: String)
            data class OrderDto(val id: String)

            @Mapper
            interface OrderMapper {
                @InverseOf("noExiste")
                fun fromDto(dto: OrderDto): Order
            }
            """.trimIndent(),
        )
        assertTrue(
            "esperaba KMX045 con el forward faltante: ${kmxHighlights()}",
            kmxHighlights().any { "KMX045" in it && "noExiste" in it },
        )
    }

    fun `test InverseOf valido no marca nada`() {
        myFixture.configureByText(
            "Mapping.kt",
            """
            package a
            import dev.kmapx.annotations.contract.Mapper
            import dev.kmapx.annotations.contract.InverseOf

            data class Order(val id: String)
            data class OrderDto(val id: String)

            @Mapper
            interface OrderMapper {
                fun toDto(order: Order): OrderDto
                @InverseOf("toDto")
                fun fromDto(dto: OrderDto): Order
            }
            """.trimIndent(),
        )
        assertTrue("no debía marcar nada: ${kmxHighlights()}", kmxHighlights().isEmpty())
    }
}
