package dev.kmapx.intellij

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.jetbrains.kotlin.psi.KtParameter

/** Referencias de los strings de `@MapField` (sede de método), verificadas con el IDE headless. */
class MapFieldReferenceTest : BasePlatformTestCase() {

    fun `test target resuelve a la propiedad del tipo de retorno`() {
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
                @MapField(target = "display<caret>Name", from = "name")
                fun toDto(c: Customer): CustomerDto
            }
            """.trimIndent(),
        )
        val ref = myFixture.getReferenceAtCaretPosition()
        assertNotNull("esperaba una referencia en el string target=", ref)
        val resolved = ref!!.resolve()
        assertTrue(
            "esperaba resolver al parámetro displayName del constructor, fue: $resolved",
            (resolved as? KtParameter)?.name == "displayName",
        )
    }

    fun `test from resuelve a la propiedad del primer parametro`() {
        myFixture.addFileToProject(
            "a/Customer.kt",
            """
            package a
            data class Customer(val name: String)
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
                @MapField(target = "displayName", from = "na<caret>me")
                fun toDto(c: Customer): CustomerDto
            }
            """.trimIndent(),
        )
        val resolved = myFixture.getReferenceAtCaretPosition()?.resolve()
        assertTrue(
            "esperaba resolver a Customer.name, fue: $resolved",
            (resolved as? KtParameter)?.name == "name",
        )
    }

    fun `test el completado dentro del string ofrece las propiedades del target`() {
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
                @MapField(target = "<caret>")
                fun toDto(c: Customer): CustomerDto
            }
            """.trimIndent(),
        )
        val lookups = myFixture.completeBasic()?.mapNotNull { it.lookupString }.orEmpty()
        assertTrue("esperaba displayName y age en el completado: $lookups",
            "displayName" in lookups && "age" in lookups)
    }

    fun `test cada segmento de una ruta resuelve navegando los tipos (v06)`() {
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
                @MapField(target = "city", from = "address.ci<caret>ty")
                fun toDto(c: Customer): CityDto
            }
            """.trimIndent(),
        )
        val resolved = myFixture.getReferenceAtCaretPosition()?.resolve()
        assertTrue(
            "el segundo segmento debía resolver a Address.city, fue: $resolved",
            (resolved as? KtParameter)?.name == "city",
        )
    }

    fun `test los strings de ignore referencian la propiedad del target (v06)`() {
        myFixture.addFileToProject(
            "a/CustomerDto.kt",
            """
            package a
            data class CustomerDto(val name: String, val audit: String? = null)
            """.trimIndent(),
        )
        myFixture.configureByText(
            "Customer.kt",
            """
            package a
            import dev.kmapx.annotations.embedded.MapTo

            @MapTo(CustomerDto::class, ignore = ["au<caret>dit"])
            data class Customer(val name: String)
            """.trimIndent(),
        )
        val resolved = myFixture.getReferenceAtCaretPosition()?.resolve()
        assertTrue(
            "el string de ignore debía resolver a CustomerDto.audit, fue: $resolved",
            (resolved as? KtParameter)?.name == "audit",
        )
    }

    fun `test el string de MapEntry resuelve al entry del enum destino (v07)`() {
        myFixture.addFileToProject(
            "a/StatusDto.kt",
            """
            package a
            enum class StatusDto { OPEN, HOME_GARDEN }
            """.trimIndent(),
        )
        myFixture.configureByText(
            "Status.kt",
            """
            package a
            import dev.kmapx.annotations.MapEntry
            import dev.kmapx.annotations.embedded.MapTo

            @MapTo(StatusDto::class)
            enum class Status { OPEN, @MapEntry(target = "HOME_<caret>GARDEN") HOME_AND_GARDEN }
            """.trimIndent(),
        )
        val resolved = myFixture.getReferenceAtCaretPosition()?.resolve()
        assertTrue(
            "esperaba resolver a StatusDto.HOME_GARDEN, fue: ${'$'}resolved",
            (resolved as? org.jetbrains.kotlin.psi.KtEnumEntry)?.name == "HOME_GARDEN",
        )
    }

    fun `test el from en sede de campo resuelve via el indice inverso (v07)`() {
        myFixture.addFileToProject(
            "a/Customer.kt",
            """
            package a
            import dev.kmapx.annotations.embedded.MapTo

            @MapTo(CustomerDto::class)
            data class Customer(val name: String)
            """.trimIndent(),
        )
        myFixture.configureByText(
            "CustomerDto.kt",
            """
            package a
            import dev.kmapx.annotations.MapField

            data class CustomerDto(@MapField(from = "na<caret>me") val displayName: String)
            """.trimIndent(),
        )
        val resolved = myFixture.getReferenceAtCaretPosition()?.resolve()
        assertTrue(
            "esperaba resolver a Customer.name, fue: ${'$'}resolved",
            (resolved as? KtParameter)?.name == "name",
        )
    }

    fun `test InverseOf resuelve al metodo forward y el rename actualiza el string`() {
        myFixture.configureByText(
            "Mappers.kt",
            """
            package a
            import dev.kmapx.annotations.contract.Mapper
            import dev.kmapx.annotations.contract.InverseOf

            data class Customer(val name: String)
            data class CustomerDto(val name: String)

            @Mapper
            interface M {
                fun toD<caret>to(c: Customer): CustomerDto
                @InverseOf("toDto")
                fun fromDto(d: CustomerDto): Customer
            }
            """.trimIndent(),
        )
        myFixture.renameElementAtCaret("toView")
        assertTrue(
            "el rename debía actualizar el string: ${myFixture.file.text}",
            "@InverseOf(\"toView\")" in myFixture.file.text,
        )
    }

    fun `test InverseOf vacio completa solo los forwards con firma inversa`() {
        myFixture.configureByText(
            "Mappers.kt",
            """
            package a
            import dev.kmapx.annotations.contract.Mapper
            import dev.kmapx.annotations.contract.InverseOf

            data class Customer(val name: String)
            data class CustomerDto(val name: String)
            data class Other(val x: Int)

            @Mapper
            interface M {
                fun toDto(c: Customer): CustomerDto
                fun toOther(c: Customer): Other
                @InverseOf("<caret>")
                fun fromDto(d: CustomerDto): Customer
            }
            """.trimIndent(),
        )
        val lookups = myFixture.completeBasic()?.mapNotNull { it.lookupString }.orEmpty()
        if (lookups.isEmpty()) {
            // Candidato único: insertado directamente.
            assertTrue("debía autocompletar toDto: ${myFixture.file.text}", "@InverseOf(\"toDto\")" in myFixture.file.text)
        } else {
            assertTrue("toDto es el único con firma inversa: ${'$'}lookups", "toDto" in lookups && "toOther" !in lookups)
        }
    }

    fun `test MapEntry completa los entries del enum destino`() {
        myFixture.configureByText(
            "Enums.kt",
            """
            package a
            import dev.kmapx.annotations.MapEntry
            import dev.kmapx.annotations.embedded.MapTo

            enum class ColorDto { RED, CRIMSON, LEGACY }

            @MapTo(ColorDto::class)
            enum class Color {
                RED,
                @MapEntry(target = "CRIM<caret>") DARK_RED,
            }
            """.trimIndent(),
        )
        val items = myFixture.completeBasic()
        if (items == null) {
            // Un único candidato: el IDE lo inserta directamente.
            assertTrue("debía autocompletar CRIMSON: ${myFixture.file.text}", "\"CRIMSON\"" in myFixture.file.text)
        } else {
            val lookups = items.mapNotNull { it.lookupString }
            assertTrue("esperaba los entries del destino: ${'$'}lookups", "CRIMSON" in lookups)
        }
    }

    fun `test el completado funciona con el string VACIO`() {
        myFixture.configureByText(
            "Enums.kt",
            """
            package a
            import dev.kmapx.annotations.MapEntry
            import dev.kmapx.annotations.embedded.MapTo

            enum class ColorDto { RED, CRIMSON, LEGACY }

            @MapTo(ColorDto::class)
            enum class Color {
                RED,
                @MapEntry(target = "<caret>") DARK_RED,
            }
            """.trimIndent(),
        )
        val lookups = myFixture.completeBasic()?.mapNotNull { it.lookupString }.orEmpty()
        assertTrue(
            "el string vacío debía ofrecer los entries: ${'$'}lookups",
            "CRIMSON" in lookups && "LEGACY" in lookups,
        )
    }

    fun `test from vacio completa las propiedades del source`() {
        myFixture.configureByText(
            "Mappers.kt",
            """
            package a
            import dev.kmapx.annotations.MapField
            import dev.kmapx.annotations.contract.Mapper

            data class Customer(val firstName: String, val age: Int)
            data class CustomerDto(val displayName: String, val age: Int)

            @Mapper
            interface M {
                @MapField(target = "displayName", from = "<caret>")
                fun toDto(c: Customer): CustomerDto
            }
            """.trimIndent(),
        )
        val lookups = myFixture.completeBasic()?.mapNotNull { it.lookupString }.orEmpty()
        assertTrue("from vacío debía ofrecer las propiedades del source: ${'$'}lookups", "firstName" in lookups)
    }
}
