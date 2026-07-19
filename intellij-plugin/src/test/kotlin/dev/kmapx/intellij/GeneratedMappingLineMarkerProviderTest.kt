package dev.kmapx.intellij

import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Markers verificados con el IDE headless. Los "generados" son stubs en el fixture — el marker
 * solo mira nombres de archivo/receivers, no compila kmapx (la detección es PSI puro).
 */
class GeneratedMappingLineMarkerProviderTest : BasePlatformTestCase() {

    fun `test clase MapTo tiene gutter hacia las funciones generadas`() {
        myFixture.addFileToProject(
            "gen/PersonMappings.kt",
            """
            package a
            fun Person.toPersonDto(): Int = 1
            fun Person.asSummary(): Int = 2
            """.trimIndent(),
        )
        myFixture.configureByText(
            "Person.kt",
            """
            package a
            import dev.kmapx.annotations.embedded.MapTo
            @MapTo(PersonDto::class)
            class Person(val name: String)
            """.trimIndent(),
        )
        val gutters = myFixture.findAllGutters()
        assertTrue(
            "esperaba el gutter de kmapx: ${gutters.map { it.tooltipText }}",
            gutters.any { it.tooltipText?.contains("kmapx") == true },
        )
    }

    fun `test sin import de kmapx no hay gutter (anotacion homonima de otra lib)`() {
        myFixture.addFileToProject("gen/OtherMappings.kt", "package a\nfun Other.toDto(): Int = 1\n")
        myFixture.configureByText(
            "Other.kt",
            """
            package a
            import com.example.otra.MapTo
            @MapTo
            class Other(val name: String)
            """.trimIndent(),
        )
        assertTrue(myFixture.findAllGutters().none { it.tooltipText?.contains("kmapx") == true })
    }

    fun `test metodo de Mapper tiene gutter hacia su override en el Impl`() {
        myFixture.addFileToProject(
            "gen/CustomerMapperImpl.kt",
            """
            package a
            object CustomerMapperImpl : CustomerMapper {
                override fun toDto(c: Int): Int = c
            }
            """.trimIndent(),
        )
        myFixture.configureByText(
            "CustomerMapper.kt",
            """
            package a
            import dev.kmapx.annotations.contract.Mapper
            @Mapper
            interface CustomerMapper {
                fun toDto(c: Int): Int
            }
            """.trimIndent(),
        )
        val gutters = myFixture.findAllGutters()
        assertTrue(
            "esperaba gutter de interfaz Y de método: ${gutters.map { it.tooltipText }}",
            gutters.count { it.tooltipText?.contains("kmapx") == true } >= 2,
        )
    }

    fun `test sin archivo generado no hay marker - mejor ausencia que salto roto`() {
        myFixture.configureByText(
            "Fresh.kt",
            """
            package a
            import dev.kmapx.annotations.embedded.MapTo
            @MapTo(FreshDto::class)
            class Fresh(val name: String)
            """.trimIndent(),
        )
        assertTrue(myFixture.findAllGutters().none { it.tooltipText?.contains("kmapx") == true })
    }

    fun `test el TARGET tiene gutter inverso hacia quien lo produce (v07)`() {
        myFixture.addFileToProject(
            "a/Person.kt",
            """
            package a
            import dev.kmapx.annotations.embedded.MapTo
            @MapTo(PersonDto::class)
            class Person(val name: String)
            """.trimIndent(),
        )
        // El DTO NO importa kmapx (dominio/target limpio) y aun así recibe el marker inverso.
        myFixture.configureByText(
            "PersonDto.kt",
            """
            package a
            class PersonDto(val name: String)
            """.trimIndent(),
        )
        val gutters = myFixture.findAllGutters()
        assertTrue(
            "esperaba el gutter inverso: ${'$'}{gutters.map { it.tooltipText }}",
            gutters.any { it.tooltipText?.contains("mapeos que producen") == true },
        )
    }
}
