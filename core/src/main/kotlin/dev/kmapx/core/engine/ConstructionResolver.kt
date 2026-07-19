package dev.kmapx.core.engine

import dev.kmapx.core.diagnostics.Diagnostic
import dev.kmapx.core.diagnostics.Diagnostics
import dev.kmapx.core.diagnostics.MLocation
import dev.kmapx.core.model.MClass

/**
 * Elige CÓMO se construye el target. Única responsabilidad: la política de resolución de
 * mecanismo (se detiene en el primer match):
 * 1. `@MapConstructor` · 2. `@MapFactory` · 3. primario visible · 4. KMX005.
 * Más de un candidato anotado (o mezcla) → KMX006.
 */
internal class ConstructionResolver {

    fun resolve(
        target: MClass,
        targetLocation: MLocation,
        diagnostics: MutableList<Diagnostic>,
    ): Mechanism? {
        val annotatedCtors = target.constructors.filter { it.annotatedMapConstructor }
        val factories = target.factories

        if (annotatedCtors.size + factories.size > 1) {
            diagnostics += Diagnostics.ambiguousConstruction(
                target = targetLocation,
                candidates = annotatedCtors.map { ctor ->
                    "@MapConstructor(${ctor.params.joinToString { it.name }})"
                } + factories.map { "@MapFactory ${it.simpleName}" },
            )
            return null
        }

        annotatedCtors.singleOrNull()?.let { return Mechanism.Ctor(it) }
        factories.singleOrNull()?.let { return Mechanism.Factory(it) }
        target.primaryConstructor?.takeIf { it.visible }?.let { return Mechanism.Ctor(it) }

        diagnostics += Diagnostics.noResolvableConstructor(targetLocation)
        return null
    }
}
