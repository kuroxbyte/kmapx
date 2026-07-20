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

    fun `test sealed - subtipo source sin par marca KMX010`() {
        myFixture.configureByText(
            "Mapping.kt",
            """
            package a
            import dev.kmapx.annotations.embedded.MapTo

            sealed interface EventDto {
                data class Paid(val amount: Long) : EventDto
            }

            @MapTo(EventDto::class)
            sealed interface Event {
                data class Paid(val amount: Long) : Event
                data class Refunded(val amount: Long) : Event
            }
            """.trimIndent(),
        )
        assertTrue("esperaba KMX010 (Refunded sin par): ${kmxHighlights()}", kmxHighlights().any { "KMX010" in it })
    }

    fun `test sealed - jerarquias paralelas completas no marcan nada`() {
        myFixture.configureByText(
            "Mapping.kt",
            """
            package a
            import dev.kmapx.annotations.embedded.MapTo

            sealed interface EventDto {
                data class Paid(val amount: Long) : EventDto
                data object Cancelled : EventDto
            }

            @MapTo(EventDto::class)
            sealed interface Event {
                data class Paid(val amount: Long) : Event
                data object Cancelled : Event
            }
            """.trimIndent(),
        )
        assertTrue("no debía marcar: ${kmxHighlights()}", kmxHighlights().isEmpty())
    }

    fun `test sealed - sin subtipos visibles el editor se abstiene`() {
        myFixture.configureByText(
            "Mapping.kt",
            """
            package a
            import dev.kmapx.annotations.embedded.MapTo

            sealed interface EventDto

            @MapTo(EventDto::class)
            sealed interface Event {
                data class Paid(val amount: Long) : Event
            }
            """.trimIndent(),
        )
        assertTrue("abstención esperada (jerarquía vacía): ${kmxHighlights()}", kmxHighlights().isEmpty())
    }

    fun `test SuppressKmapx silencia el warning KMX023 tambien en el editor`() {
        val body = """
            package a
            import dev.kmapx.annotations.embedded.MapTo
            %s
            enum class ColorDto { RED, GREEN, EXTRA }

            %s
            @MapTo(ColorDto::class)
            enum class Color { RED, GREEN }
        """.trimIndent()
        myFixture.configureByText("Mapping.kt", body.format("", ""))
        assertTrue("esperaba el warning KMX023: ${kmxHighlights()}", kmxHighlights().any { "KMX023" in it })

        myFixture.configureByText(
            "Mapping2.kt",
            body.format("import dev.kmapx.annotations.SuppressKmapx", "@SuppressKmapx(\"KMX023\")"),
        )
        assertTrue("el @SuppressKmapx debía silenciarlo: ${kmxHighlights()}", kmxHighlights().none { "KMX023" in it })
    }

    fun `test multi-fuente - el parametro suplementario cubre el campo sin fuente`() {
        myFixture.configureByText(
            "Mapping.kt",
            """
            package a
            import dev.kmapx.annotations.contract.Mapper

            data class CreateReq(val name: String)
            data class Product(val name: String, val id: String)

            @Mapper
            interface Factory {
                fun create(req: CreateReq, id: String): Product
            }
            """.trimIndent(),
        )
        assertTrue("id viene del parámetro suplementario: ${kmxHighlights()}", kmxHighlights().isEmpty())
    }

    fun `test multi-fuente - sin el suplementario el KMX002 reaparece`() {
        myFixture.configureByText(
            "Mapping.kt",
            """
            package a
            import dev.kmapx.annotations.contract.Mapper

            data class CreateReq(val name: String)
            data class Product(val name: String, val id: String)

            @Mapper
            interface Factory {
                fun create(req: CreateReq): Product
            }
            """.trimIndent(),
        )
        assertTrue("esperaba KMX002 para id: ${kmxHighlights()}", kmxHighlights().any { "KMX002" in it })
    }

    fun `test BiMapTo - campo de un solo lado marca KMX028`() {
        myFixture.configureByText(
            "Mapping.kt",
            """
            package a
            import dev.kmapx.annotations.embedded.BiMapTo

            data class WarehouseDto(val code: String)

            @BiMapTo(WarehouseDto::class)
            data class Warehouse(val code: String, val internalFlag: Boolean)
            """.trimIndent(),
        )
        assertTrue("esperaba KMX028 (internalFlag sin camino de vuelta): ${kmxHighlights()}", kmxHighlights().any { "KMX028" in it })
    }

    fun `test BiMapTo - par simetrico no marca nada`() {
        myFixture.configureByText(
            "Mapping.kt",
            """
            package a
            import dev.kmapx.annotations.embedded.BiMapTo

            data class WarehouseDto(val code: String, val name: String)

            @BiMapTo(WarehouseDto::class)
            data class Warehouse(val code: String, val name: String)
            """.trimIndent(),
        )
        assertTrue("simétrico, no debía marcar: ${kmxHighlights()}", kmxHighlights().isEmpty())
    }

    fun `test InverseOf - asimetria del forward marca KMX028 en el editor`() {
        myFixture.configureByText(
            "Mapping.kt",
            """
            package a
            import dev.kmapx.annotations.contract.Mapper
            import dev.kmapx.annotations.contract.InverseOf

            data class Customer(val name: String, val secret: String)
            data class CustomerDto(val name: String)

            @Mapper
            interface M {
                fun toDto(c: Customer): CustomerDto
                @InverseOf("toDto")
                fun fromDto(d: CustomerDto): Customer
            }
            """.trimIndent(),
        )
        assertTrue("esperaba KMX028 (secret sin fuente en la vuelta): ${kmxHighlights()}", kmxHighlights().any { "KMX028" in it })
    }

    fun `test InverseOf - par invertible no marca nada`() {
        myFixture.configureByText(
            "Mapping.kt",
            """
            package a
            import dev.kmapx.annotations.MapField
            import dev.kmapx.annotations.contract.Mapper
            import dev.kmapx.annotations.contract.InverseOf

            data class Customer(val firstName: String)
            data class CustomerDto(val displayName: String)

            @Mapper
            interface M {
                @MapField(target = "displayName", from = "firstName")
                fun toDto(c: Customer): CustomerDto
                @InverseOf("toDto")
                fun fromDto(d: CustomerDto): Customer
            }
            """.trimIndent(),
        )
        assertTrue("el renombre se invierte solo, no debía marcar: ${kmxHighlights()}", kmxHighlights().isEmpty())
    }

    fun `test converter calificado en el campo evita el KMX004 falso`() {
        myFixture.configureByText(
            "Mapping.kt",
            """
            package a
            import dev.kmapx.annotations.MapField
            import dev.kmapx.annotations.embedded.MapTo
            import dev.kmapx.runtime.Converts

            object PriceUsd : Converts<Long, String> { override fun convert(value: Long) = "${'$'}${'$'}value" }

            data class Dto(@MapField(converter = PriceUsd::class) val price: String)

            @MapTo(Dto::class)
            data class Src(val price: Long)
            """.trimIndent(),
        )
        assertTrue("el paso 0 debía resolver Long→String: ${kmxHighlights()}", kmxHighlights().isEmpty())
    }

    fun `test converter calificado en el metodo tambien resuelve`() {
        myFixture.configureByText(
            "Mapping.kt",
            """
            package a
            import dev.kmapx.annotations.MapField
            import dev.kmapx.annotations.contract.Mapper
            import dev.kmapx.runtime.Converts

            object PriceUsd : Converts<Long, String> { override fun convert(value: Long) = "${'$'}${'$'}value" }

            data class Src(val price: Long)
            data class Dto(val price: String)

            @Mapper
            interface M {
                @MapField(target = "price", converter = PriceUsd::class)
                fun toDto(s: Src): Dto
            }
            """.trimIndent(),
        )
        assertTrue("la sede de método transporta el converter: ${kmxHighlights()}", kmxHighlights().isEmpty())
    }

    fun `test ruta anidada valida no marca falsos`() {
        myFixture.configureByText(
            "Mapping.kt",
            """
            package a
            import dev.kmapx.annotations.MapField
            import dev.kmapx.annotations.embedded.MapTo

            data class Address(val city: String)
            data class Dto(@MapField(from = "address.city") val city: String)

            @MapTo(Dto::class)
            data class Src(val address: Address)
            """.trimIndent(),
        )
        assertTrue("la ruta resuelve sin falsos: ${kmxHighlights()}", kmxHighlights().isEmpty())
    }

    fun `test ruta anidada con segmento nullable exige estrategia - KMX003`() {
        myFixture.configureByText(
            "Mapping.kt",
            """
            package a
            import dev.kmapx.annotations.MapField
            import dev.kmapx.annotations.embedded.MapTo

            data class Address(val city: String)
            data class Dto(@MapField(from = "address.city") val city: String)

            @MapTo(Dto::class)
            data class Src(val address: Address?)
            """.trimIndent(),
        )
        assertTrue("segmento nullable → KMX003: ${kmxHighlights()}", kmxHighlights().any { "KMX003" in it })
    }

    fun `test dos MapTo con el mismo nombre de funcion marcan KMX013`() {
        myFixture.configureByText(
            "Mapping.kt",
            """
            package a
            import dev.kmapx.annotations.embedded.MapTo

            data class Dto(val name: String)

            @MapTo(Dto::class)
            @MapTo(Dto::class, name = "toDto")
            data class Src(val name: String)
            """.trimIndent(),
        )
        assertTrue("colisión de nombre → KMX013: ${kmxHighlights()}", kmxHighlights().any { "KMX013" in it })
    }

    fun `test componentModel SPRING sin el framework marca KMX030`() {
        myFixture.configureByText(
            "Mapping.kt",
            """
            package a
            import dev.kmapx.annotations.contract.ComponentModel
            import dev.kmapx.annotations.contract.Mapper

            data class Src(val name: String)
            data class Dto(val name: String)

            @Mapper(componentModel = ComponentModel.SPRING)
            interface M { fun toDto(s: Src): Dto }
            """.trimIndent(),
        )
        assertTrue("sin spring-context en el classpath → KMX030: ${kmxHighlights()}", kmxHighlights().any { "KMX030" in it })
    }

    fun `test useSerialNames - el alias SerialName cubre el matching`() {
        val body = """
            package a
            import dev.kmapx.annotations.embedded.MapTo
            import kotlinx.serialization.SerialName

            data class Dto(val nick: String)

            @MapTo(Dto::class%s)
            data class Src(@SerialName("nick") val nickname: String)
        """.trimIndent()
        // La anotación @SerialName solo necesita existir como PSI: declaramos el stub.
        myFixture.addFileToProject(
            "kotlinx/serialization/SerialName.kt",
            "package kotlinx.serialization\nannotation class SerialName(val value: String)",
        )
        myFixture.configureByText("Mapping.kt", body.format(", useSerialNames = true"))
        assertTrue("con el opt-in, el alias matchea: ${kmxHighlights()}", kmxHighlights().isEmpty())

        myFixture.configureByText("Mapping2.kt", body.format(""))
        assertTrue("sin opt-in, KMX002: ${kmxHighlights()}", kmxHighlights().any { "KMX002" in it })
    }

    fun `test sealed - subtipos en OTRO archivo se encuentran via light classes`() {
        myFixture.addFileToProject(
            "a/Dtos.kt",
            """
            package a
            sealed interface EventDto
            data class PaidDto2(val amount: Long) : EventDto
            """.trimIndent(),
        )
        myFixture.configureByText(
            "Mapping.kt",
            """
            package a
            import dev.kmapx.annotations.embedded.MapTo
            import dev.kmapx.annotations.MapSubtype

            @MapTo(EventDto::class)
            sealed interface Event {
                @MapSubtype(target = PaidDto2::class)
                data class Paid(val amount: Long) : Event
            }
            """.trimIndent(),
        )
        assertTrue("el subtipo cross-file debía resolverse: ${kmxHighlights()}", kmxHighlights().isEmpty())
    }

    fun `test config que no es MapperConfig marca KMX044`() {
        myFixture.configureByText(
            "Mapping.kt",
            """
            package a
            import dev.kmapx.annotations.contract.Mapper

            interface NotAProfile

            data class Src(val name: String)
            data class Dto(val name: String)

            @Mapper(config = NotAProfile::class)
            interface M { fun toDto(s: Src): Dto }
            """.trimIndent(),
        )
        assertTrue("config sin @MapperConfig → KMX044: ${kmxHighlights()}", kmxHighlights().any { "KMX044" in it })
    }

    fun `test Patch tri-estado del runtime no marca KMX004 en el patch`() {
        myFixture.configureByText(
            "Mapping.kt",
            """
            package a
            import dev.kmapx.annotations.contract.Mapper
            import dev.kmapx.runtime.Patch

            data class Product(val title: String, val note: String?)
            data class ProductPatch(val title: String?, val note: Patch<String?>)

            @Mapper
            interface M {
                fun apply(target: Product, patch: ProductPatch): Product
            }
            """.trimIndent(),
        )
        assertTrue("Patch<T> es del runtime, no unresolved: ${kmxHighlights()}", kmxHighlights().isEmpty())
    }

    fun `test value class se desenvuelve sin KMX004 - unwrap y wrap`() {
        myFixture.configureByText(
            "Mapping.kt",
            """
            package a
            import dev.kmapx.annotations.embedded.MapTo

            @JvmInline value class OrderId(val value: String)

            data class Dto(val id: String, val sku: OrderId)

            @MapTo(Dto::class)
            data class Src(val id: OrderId, val sku: String)
            """.trimIndent(),
        )
        assertTrue("el unwrap/wrap de value class no debía marcar: ${kmxHighlights()}", kmxHighlights().isEmpty())
    }
}
