package dev.kmapx.ksp

import dev.kmapx.core.plan.Construction
import dev.kmapx.core.plan.MapperRef
import dev.kmapx.core.plan.MappingPlan
import dev.kmapx.core.plan.ValueSource

/**
 * Extrae las ARISTAS del grafo de mapeo: qué funciones generadas referencia un plan
 * (`ViaMapper`, incluso anidado en colecciones/estrategias/sealed). Única responsabilidad: leer
 * un plan y devolver los nombres referenciados. El processor las usa para `MappingGraph.findCycle`
 * (KMX008). Funciones puras sobre el plan — sin estado ni KSP.
 */
internal object PlanReferences {

    /** Funciones generadas que el plan referencia — las aristas del grafo de ciclos (KMX008). */
    fun of(plans: List<MappingPlan>): Set<String> =
        walk(plans) { value ->
            when (value) {
                is ValueSource.ViaMapper ->
                    (value.mapper as? MapperRef.GeneratedExtension)
                        ?.let { listOf(it.qualifiedFunction) }.orEmpty()
                else -> emptyList()
            }
        }.toSet()

    /** FQNs de converters-class INYECTADOS por los planes (para el constructor del impl). */
    fun injectedConverters(plans: List<MappingPlan>): List<String> =
        walk(plans) { value ->
            when (value) {
                is ValueSource.ViaQualifiedConverter ->
                    if (value.injected) listOf(value.converterObject) else emptyList()
                else -> emptyList()
            }
        }.distinct()

    /**
     * El ÚNICO recorrido del árbol de un plan (argumentos, post-asignaciones, ValueSources
     * anidados en colecciones/Map/estrategias y sub-planes sealed); [leaf] extrae lo suyo de
     * cada nodo. Que la recursión viva UNA vez no es cosmético: la asimetría entre los dos
     * recorridos gemelos que había antes (uno conocía `MapEntries`, el otro no) fue el origen
     * del bug de ciclos-vía-Map de la revisión 2026-07-15 — esta forma lo hace irrepetible.
     */
    private fun walk(plans: List<MappingPlan>, leaf: (ValueSource) -> List<String>): List<String> {
        fun values(value: ValueSource): List<String> = leaf(value) + when (value) {
            is ValueSource.MapElements -> values(value.element)
            is ValueSource.MapEntries -> listOfNotNull(value.key, value.value).flatMap { values(it) }
            is ValueSource.NullStrategyOver -> values(value.inner)
            else -> emptyList()
        }
        return plans.flatMap { plan ->
            when (val c = plan.construction) {
                is Construction.ConstructorCall ->
                    c.arguments.flatMap { values(it.value) } + c.postAssignments.flatMap { values(it.value) }
                is Construction.FactoryCall ->
                    c.arguments.flatMap { values(it.value) } + c.postAssignments.flatMap { values(it.value) }
                is Construction.SealedDispatch -> walk(c.branches.map { it.plan }, leaf)
                else -> emptyList()
            }
        }
    }
}
