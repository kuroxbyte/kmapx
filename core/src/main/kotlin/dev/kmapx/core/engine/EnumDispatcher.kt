package dev.kmapx.core.engine

import dev.kmapx.core.diagnostics.Diagnostics
import dev.kmapx.core.diagnostics.MLocation
import dev.kmapx.core.diagnostics.Suggestions
import dev.kmapx.core.model.MClass
import dev.kmapx.core.plan.Construction
import dev.kmapx.core.plan.Emission
import dev.kmapx.core.plan.EnumBranch
import dev.kmapx.core.plan.MappingPlan

/**
 * Despacho entre enums paralelos: `when` por IGUALDAD, sin `else`. Única responsabilidad.
 * Emparejamiento por nombre idéntico; override `@MapEntry(target = "...")`. Entry del source sin
 * par → KMX026 (con did-you-mean si el override apunta a la nada), salvo que el enum declare el
 * FALLBACK de sede de clase — rama explícita hacia él, nunca un `else`; entry
 * extra del target → warning KMX023 (el `when` sigue exhaustivo sobre el source).
 */
internal class EnumDispatcher {

    fun resolve(source: MClass, target: MClass, emission: Emission, ctx: Ctx): MappingPlan {
        val targetNames = target.enumEntries.map { it.name }.toSet()
        val branches = mutableListOf<EnumBranch>()
        val matched = mutableSetOf<String>()

        // El fallback se valida UNA vez por mapeo; inválido → KMX047 (y los entries sin par
        // caen a KMX026 como siempre — todos los errores en una pasada).
        val fallback = source.enumFallback?.takeIf { wanted ->
            (wanted in targetNames).also { valid ->
                if (!valid) {
                    ctx.diagnostics += Diagnostics.enumFallbackMissing(
                        sourceEnum = MLocation(source.type.qualifiedName),
                        fallback = wanted,
                        targetEnum = target.type.qualifiedName,
                        didYouMean = Suggestions.closest(wanted, target.enumEntries.map { it.name }),
                    )
                }
            }
        }

        for (entry in source.enumEntries) {
            val entryLocation = MLocation("${source.type.qualifiedName}.${entry.name}")
            val wanted = entry.targetOverride ?: entry.name
            if (wanted !in targetNames) {
                // Sin override EXPLÍCITO, el fallback de clase es el consentimiento (mismo
                // principio que el ignore); un override roto sigue siendo KMX026.
                if (entry.targetOverride == null && fallback != null) {
                    matched += fallback
                    branches += EnumBranch(entry.name, fallback)
                    continue
                }
                ctx.diagnostics += Diagnostics.enumEntryWithoutCounterpart(
                    sourceEntry = entryLocation,
                    targetEnum = target.type.qualifiedName,
                    // El did-you-mean SOLO cuando un override explícito apunta a la nada:
                    didYouMean = if (entry.targetOverride != null) {
                        Suggestions.closest(entry.targetOverride, target.enumEntries.map { it.name })
                    } else emptyList(),
                )
                continue
            }
            matched += wanted
            branches += EnumBranch(entry.name, wanted)
        }

        target.enumEntries
            .filter { it.name !in matched }
            .forEach {
                ctx.diagnostics += Diagnostics.targetSubtypeUnmatched(
                    MLocation("${target.type.qualifiedName}.${it.name}"), source.type.qualifiedName,
                )
            }

        return MappingPlan(
            source = source.type,
            target = target.type,
            emission = emission,
            construction = Construction.EnumDispatch(branches),
            diagnostics = ctx.diagnostics,
        )
    }
}
