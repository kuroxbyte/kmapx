package dev.kmapx.core.engine

import dev.kmapx.core.diagnostics.Diagnostics
import dev.kmapx.core.diagnostics.MLocation
import dev.kmapx.core.model.MClass
import dev.kmapx.core.model.MType
import dev.kmapx.core.model.TypeKind
import dev.kmapx.core.plan.Branch
import dev.kmapx.core.plan.Construction
import dev.kmapx.core.plan.Emission
import dev.kmapx.core.plan.MappingPlan

/**
 * `when` exhaustivo sin `else` entre jerarquías SEALED paralelas. Única responsabilidad
 * (simétrico a [EnumDispatcher], que hace lo propio para enums): emparejamiento por `simpleName`
 * idéntico con override `@MapSubtype`; cada par genera su sub-plan con TODAS las reglas normales
 * vía [resolveSubtype] (el motor se pasa a sí mismo — la recursión es del orquestador, no de
 * este colaborador), emitido como función nombrada propia; los pares
 * `object` ↔ `object` son referencia directa. Source sin par → KMX010; target sin par →
 * warning KMX023; anidamiento sealed → KMX024 (un nivel en v1).
 */
internal class SealedDispatcher {

    fun resolve(
        source: MClass,
        target: MClass,
        emission: Emission,
        ctx: Ctx,
        resolveSubtype: (source: MClass, target: MClass, emission: Emission) -> MappingPlan,
    ): MappingPlan {
        val branches = mutableListOf<Branch>()
        val matchedTargets = mutableSetOf<String>()

        for (subtype in source.sealedSubtypes) {
            val subtypeLocation = MLocation(subtype.type.qualifiedName)
            if (isSealed(subtype.type)) {
                ctx.diagnostics += Diagnostics.deepSealedNesting(subtypeLocation)
                continue
            }
            val counterpart = subtype.subtypeTargetOverride
                ?.let { override -> target.sealedSubtypes.firstOrNull { it.type.qualifiedName == override } }
                ?: target.sealedSubtypes.firstOrNull { it.type.simpleName == subtype.type.simpleName }
            if (counterpart == null) {
                ctx.diagnostics += Diagnostics.subtypeWithoutCounterpart(
                    subtypeLocation, target.type.qualifiedName,
                )
                continue
            }
            matchedTargets += counterpart.type.qualifiedName

            val plan = if (subtype.type.kind == TypeKind.OBJECT && counterpart.type.kind == TypeKind.OBJECT) {
                // `data object` ↔ `data object`: referencia directa, sin sub-función.
                MappingPlan(
                    source = subtype.type,
                    target = counterpart.type,
                    emission = emission,
                    construction = Construction.ObjectReference(counterpart.type.qualifiedName),
                )
            } else {
                val subPlan = resolveSubtype(
                    subtype, counterpart,
                    Emission.ExtensionFunction("to${counterpart.type.simpleName}"),
                )
                ctx.diagnostics += subPlan.diagnostics
                subPlan
            }
            branches += Branch(subtype.type.qualifiedName, plan)
        }

        // Subtipos del TARGET sin par: legítimo (puede tener más casos), pero nunca silencioso.
        target.sealedSubtypes
            .filter { it.type.qualifiedName !in matchedTargets }
            .forEach {
                ctx.diagnostics += Diagnostics.targetSubtypeUnmatched(
                    MLocation(it.type.qualifiedName), source.type.qualifiedName,
                )
            }

        return MappingPlan(
            source = source.type,
            target = target.type,
            emission = emission,
            construction = Construction.SealedDispatch(branches),
            diagnostics = ctx.diagnostics,
        )
    }

    /** Sealed class o sealed interface — el criterio del despacho. */
    fun isSealed(t: MType): Boolean =
        t.kind == TypeKind.SEALED_CLASS || t.kind == TypeKind.SEALED_INTERFACE
}
