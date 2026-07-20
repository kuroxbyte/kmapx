package dev.kmapx.intellij

import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * El "delombok" de kmapx: la intención escribe el `<Source>Mappings.kt` REAL (motor + emitter
 * del build) como fuente y elimina la `@MapTo` — el mapeo pasa a ser código del usuario.
 */
class MaterializeMappingIntentionTest : BasePlatformTestCase() {

    fun `test materializa el mapeo y elimina la anotacion`() {
        myFixture.configureByText(
            "Person.kt",
            """
            package a
            import dev.kmapx.annotations.embedded.MapTo

            data class PersonDto(val name: String, val age: Int)

            @MapTo(PersonDto::class)
            data class Per<caret>son(val name: String, val age: Int)
            """.trimIndent(),
        )
        val intention = myFixture.findSingleIntention("kmapx: materializar el mapeo")
        myFixture.launchAction(intention)

        val generated = myFixture.findFileInTempDir("PersonMappings.kt")
        assertNotNull("debía crear PersonMappings.kt", generated)
        val content = String(generated!!.contentsToByteArray())
        assertTrue("debía contener la extension: $content", "fun Person.toPersonDto(): PersonDto" in content)

        val source = myFixture.file.text
        assertFalse("debía eliminar la @MapTo: $source", "@MapTo" in source)
    }

    fun `test contract - materializa el Impl y elimina la anotacion Mapper`() {
        myFixture.configureByText(
            "Mappers.kt",
            """
            package a
            import dev.kmapx.annotations.contract.Mapper

            data class Customer(val name: String)
            data class CustomerDto(val name: String)

            @Mapper
            interface Customer<caret>Mapper {
                fun toDto(c: Customer): CustomerDto
            }
            """.trimIndent(),
        )
        val intention = myFixture.findSingleIntention("kmapx: materializar el mapeo")
        myFixture.launchAction(intention)

        val generated = myFixture.findFileInTempDir("CustomerMapperImpl.kt")
        assertNotNull("debía crear CustomerMapperImpl.kt", generated)
        val content = String(generated!!.contentsToByteArray())
        assertTrue("debía implementar la interfaz: $content", "CustomerMapper" in content && "toDto" in content)
        assertFalse("debía eliminar la @Mapper", "@Mapper" in myFixture.file.text)
    }

    fun `test contract - la MapField del metodo y sus imports tambien se limpian`() {
        myFixture.configureByText(
            "Mappers.kt",
            """
            package a
            import dev.kmapx.annotations.MapField
            import dev.kmapx.annotations.contract.Mapper

            data class Customer(val firstName: String)
            data class CustomerDto(val displayName: String)

            @Mapper
            interface Customer<caret>Mapper {
                @MapField(target = "displayName", from = "firstName")
                fun toDto(c: Customer): CustomerDto
            }
            """.trimIndent(),
        )
        myFixture.launchAction(myFixture.findSingleIntention("kmapx: materializar el mapeo"))
        val text = myFixture.file.text
        assertFalse("la @MapField muerta debía irse: $text", "@MapField" in text)
        assertFalse("el import de MapField debía irse: $text", "import dev.kmapx.annotations.MapField" in text)
        assertFalse("el import de Mapper debía irse: $text", "import dev.kmapx.annotations.contract.Mapper" in text)
        val impl = myFixture.findFileInTempDir("CustomerMapperImpl.kt")
        assertTrue(
            "el renombre vive en el impl materializado",
            "displayName = c.firstName" in String(impl!!.contentsToByteArray()),
        )
    }

    fun `test contract - con formas no soportadas se abstiene`() {
        myFixture.configureByText(
            "Mappers.kt",
            """
            package a
            import dev.kmapx.annotations.contract.Mapper

            data class Customer(val name: String)
            data class CustomerDto(val name: String)
            data class CustomerPatch(val name: String?)

            @Mapper
            interface Customer<caret>Mapper {
                fun toDto(c: Customer): CustomerDto
                fun apply(target: Customer, patch: CustomerPatch): Customer
            }
            """.trimIndent(),
        )
        val intention = myFixture.findSingleIntention("kmapx: materializar el mapeo")
        myFixture.launchAction(intention)

        assertNull("con patch en la interfaz no debía materializar", myFixture.findFileInTempDir("CustomerMapperImpl.kt"))
        assertTrue("la @Mapper debía quedarse", "@Mapper" in myFixture.file.text)
    }

    fun `test materializar borra el generado stale de build-generated`() {
        myFixture.addFileToProject(
            "build/generated/ksp/main/kotlin/a/PersonMappings.kt",
            "package a\n\npublic fun Person.toPersonDto(): PersonDto = PersonDto(name = name)\n",
        )
        myFixture.configureByText(
            "Person.kt",
            """
            package a
            import dev.kmapx.annotations.embedded.MapTo

            data class PersonDto(val name: String)

            @MapTo(PersonDto::class)
            data class Per<caret>son(val name: String)
            """.trimIndent(),
        )
        myFixture.launchAction(myFixture.findSingleIntention("kmapx: materializar el mapeo"))

        assertNotNull("el materializado debe existir", myFixture.findFileInTempDir("PersonMappings.kt"))
        assertNull(
            "el stale de build/generated debía borrarse (Redeclaration)",
            myFixture.findFileInTempDir("build/generated/ksp/main/kotlin/a/PersonMappings.kt"),
        )
    }

    fun `test materializar elimina el import huerfano de la anotacion`() {
        myFixture.configureByText(
            "Person.kt",
            """
            package a
            import dev.kmapx.annotations.embedded.MapTo

            data class PersonDto(val name: String)

            @MapTo(PersonDto::class)
            data class Per<caret>son(val name: String)
            """.trimIndent(),
        )
        myFixture.launchAction(myFixture.findSingleIntention("kmapx: materializar el mapeo"))
        assertFalse(
            "el import de MapTo quedó huérfano y debía irse: ${myFixture.file.text}",
            "import dev.kmapx.annotations.embedded.MapTo" in myFixture.file.text,
        )
    }

    fun `test el import se queda si otra clase del archivo sigue anotada`() {
        myFixture.configureByText(
            "Person.kt",
            """
            package a
            import dev.kmapx.annotations.embedded.MapTo

            data class PersonDto(val name: String)
            data class OtherDto(val name: String)

            @MapTo(OtherDto::class)
            data class Other(val name: String)

            @MapTo(PersonDto::class)
            data class Per<caret>son(val name: String)
            """.trimIndent(),
        )
        myFixture.launchAction(myFixture.findSingleIntention("kmapx: materializar el mapeo"))
        val text = myFixture.file.text
        assertTrue("Other sigue anotada: el import debía quedarse: $text", "import dev.kmapx.annotations.embedded.MapTo" in text)
        assertTrue("solo la @MapTo de Person debía irse", "@MapTo(OtherDto::class)" in text)
    }

    fun `test con diagnosticos pendientes no materializa`() {
        myFixture.configureByText(
            "Person.kt",
            """
            package a
            import dev.kmapx.annotations.embedded.MapTo

            data class PersonDto(val name: String, val nickname: String)

            @MapTo(PersonDto::class)
            data class Per<caret>son(val name: String, val nickname: String?)
            """.trimIndent(),
        )
        val intention = myFixture.findSingleIntention("kmapx: materializar el mapeo")
        myFixture.launchAction(intention)

        assertNull("con KMX003 pendiente no debía escribir nada", myFixture.findFileInTempDir("PersonMappings.kt"))
        assertTrue("la @MapTo debía quedarse", "@MapTo" in myFixture.file.text)
    }
}
