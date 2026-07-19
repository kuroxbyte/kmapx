package dev.kmapx.intellij

import com.intellij.testFramework.fixtures.BasePlatformTestCase

/** La intención creadora: DTO espejo + `@MapTo` en un gesto. */
class CreateMirrorDtoIntentionTest : BasePlatformTestCase() {

    fun `test crea el DTO espejo y anota la clase con MapTo`() {
        myFixture.configureByText(
            "Order.kt",
            """
            package a

            data class Or<caret>der(val id: String, val total: Int)
            """.trimIndent(),
        )
        val intention = myFixture.availableIntentions
            .first { it.text == "kmapx: crear el DTO espejo con @MapTo" }
        myFixture.launchAction(intention)
        val text = myFixture.file.text
        assertTrue("debía crear el espejo: $text", "data class OrderDto(val id: String, val total: Int)" in text)
        assertTrue("debía anotar la clase: $text", "@MapTo(OrderDto::class)" in text)
        assertTrue(
            "debía importar MapTo: $text",
            "import dev.kmapx.annotations.embedded.MapTo" in text,
        )
    }

    fun `test no se ofrece si la clase ya declara un MapTo`() {
        myFixture.configureByText(
            "Order.kt",
            """
            package a
            import dev.kmapx.annotations.embedded.MapTo

            data class Existing(val id: String)

            @MapTo(Existing::class)
            data class Or<caret>der(val id: String)
            """.trimIndent(),
        )
        assertTrue(
            "no debía ofrecerse sobre una clase ya mapeada",
            myFixture.availableIntentions.none { it.text == "kmapx: crear el DTO espejo con @MapTo" },
        )
    }

    fun `test no se ofrece si el espejo ya existe`() {
        myFixture.addFileToProject("a/OrderDto.kt", "package a\ndata class OrderDto(val id: String)\n")
        myFixture.configureByText(
            "Order.kt",
            """
            package a

            data class Or<caret>der(val id: String)
            """.trimIndent(),
        )
        assertTrue(
            "no debía ofrecerse si OrderDto ya existe",
            myFixture.availableIntentions.none { it.text == "kmapx: crear el DTO espejo con @MapTo" },
        )
    }
}
