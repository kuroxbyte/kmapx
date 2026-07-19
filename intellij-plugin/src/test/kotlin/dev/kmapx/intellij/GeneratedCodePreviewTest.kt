package dev.kmapx.intellij

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.psiUtil.findDescendantOfType

/**
 * El preview usa el plan del motor y el [PlanEmitter] REAL:
 * lo que se muestra es lo que el build generará.
 */
class GeneratedCodePreviewTest : BasePlatformTestCase() {

    fun `test el preview de un MapTo emite la extension real, widening incluido`() {
        myFixture.configureByText(
            "Mapping.kt",
            """
            package a
            import dev.kmapx.annotations.embedded.MapTo

            data class Dto(val name: String, val total: Long)

            @MapTo(Dto::class)
            data class Src(val name: String, val total: Int)
            """.trimIndent(),
        )
        val source = myFixture.file.findDescendantOfType<KtClassOrObject> { it.name == "Src" }!!
        val code = GeneratedCodePreview.renderMapTos(source)
        assertNotNull("esperaba preview para Src", code)
        assertTrue("debía emitir la extension real: $code", "public fun Src.toDto(): Dto" in code!!)
        assertTrue("debía incluir el widening: $code", "total = total.toLong()" in code)
    }

    fun `test un plan invalido se muestra como diagnosticos comentados`() {
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
        val source = myFixture.file.findDescendantOfType<KtClassOrObject> { it.name == "Src" }!!
        val code = GeneratedCodePreview.renderMapTos(source)
        assertNotNull(code)
        assertTrue("debía mostrar el KMX003 comentado: $code", "// [KMX003]" in code!!)
    }

    fun `test el preview de un metodo de mapper emite su equivalente embedded`() {
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
            }
            """.trimIndent(),
        )
        val method = myFixture.file.findDescendantOfType<KtNamedFunction> { it.name == "toDto" }!!
        val code = GeneratedCodePreview.renderMapperMethod(method)
        assertNotNull("esperaba preview para el método", code)
        assertTrue("debía emitir el mapeo del método: $code", "fun Order.toDto(): OrderDto" in code!!)
    }

    fun `test la intencion esta disponible sobre un MapTo`() {
        myFixture.configureByText(
            "Mapping.kt",
            """
            package a
            import dev.kmapx.annotations.embedded.MapTo

            data class Dto(val name: String)

            @MapTo(Dto::class)
            data class Sr<caret>c(val name: String)
            """.trimIndent(),
        )
        val available = myFixture.availableIntentions.map { it.text }
        assertTrue(
            "esperaba la intención de preview: $available",
            available.any { it == "kmapx: ver el código generado" },
        )
    }
}
