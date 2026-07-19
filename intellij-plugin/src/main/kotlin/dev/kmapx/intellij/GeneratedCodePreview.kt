package dev.kmapx.intellij

import dev.kmapx.codegen.PlanEmitter
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtNamedFunction

/**
 * El código EXACTO que kmapx generará, calculado con el plan del
 * [EditorMappingResolver] (el mismo motor) y materializado con el [PlanEmitter] REAL del
 * backend: lo que se ve en el popup es lo que saldrá del build, no una imitación.
 *
 * Los planes inválidos no se emiten (contrato del backend): sus diagnósticos aparecen como
 * comentarios — el preview también sirve para LEER los errores del par completo.
 */
internal object GeneratedCodePreview {

    /** El archivo `<Source>Mappings.kt` de una clase `@MapTo` (modo embedded). null = no aplica. */
    fun renderMapTos(source: KtClassOrObject): String? {
        val resolved = EditorMappingResolver.mapTos(source)
        if (resolved.isEmpty()) return null
        return render(
            valid = resolved.filter { it.plan.valid }.map { it.plan },
            invalid = resolved.filterNot { it.plan.valid }.map { it.plan },
        )
    }

    /** El equivalente EMBEDDED de un método de mapeo de `@Mapper` (el impl delega en él). */
    fun renderMapperMethod(method: KtNamedFunction): String? {
        val mapper = method.parent?.parent as? KtClass ?: return null
        val resolved = EditorMappingResolver.mapperMethods(mapper)
            .firstOrNull { it.method == method } ?: return null
        return render(
            valid = listOf(resolved.plan).filter { it.valid },
            invalid = listOf(resolved.plan).filterNot { it.valid },
        )
    }

    private fun render(
        valid: List<dev.kmapx.core.plan.MappingPlan>,
        invalid: List<dev.kmapx.core.plan.MappingPlan>,
    ): String? {
        if (valid.isEmpty() && invalid.isEmpty()) return null
        val emitted = valid
            .groupBy { it.source.qualifiedName } // el emitter exige un source por archivo
            .values
            .joinToString("\n") { PlanEmitter().emit(it).content }
            .takeIf { valid.isNotEmpty() }
        val pending = invalid
            .flatMap { it.diagnostics }
            .joinToString("\n") { "// ${it.render()}" }
            .takeIf { invalid.isNotEmpty() }
        return listOfNotNull(emitted, pending).joinToString("\n")
    }
}
