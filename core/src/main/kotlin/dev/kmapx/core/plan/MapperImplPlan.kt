package dev.kmapx.core.plan

import dev.kmapx.core.diagnostics.Diagnostic
import dev.kmapx.core.diagnostics.Severity
import dev.kmapx.core.model.MType

/**
 * Plan del modo interfaz: `@Mapper interface X` → `XImpl` (object o class).
 * Regla central: cada método DELEGA en la extension del mismo par cuando existe
 * (`la extension es la fuente de verdad`); si no existe, el plan del método se
 * resuelve con el mismo motor y se materializa inline — la lógica nunca se duplica.
 */
public data class MapperImplPlan(
    /** Qualified name of the annotated interface; the impl is `<SimpleName>Impl` in its package. */
    val interfaceQualifiedName: String,
    val componentModel: Emission.Component,
    val methods: List<MapperMethod>,
    /** Converter-classes (beans) inyectados en el constructor del impl (FQN, deduplicados). */
    val injectedConverters: List<String> = emptyList(),
    val diagnostics: List<Diagnostic> = emptyList(),
) {
    public val interfaceSimpleName: String get() = interfaceQualifiedName.substringAfterLast('.')
    public val packageName: String get() = interfaceQualifiedName.substringBeforeLast('.', missingDelimiterValue = "")
    public val implSimpleName: String get() = "${interfaceSimpleName}Impl"

    public val valid: Boolean
        get() = diagnostics.none { it.severity == Severity.ERROR } &&
            methods.none { (it.body as? MethodBody.InlineConstruction)?.plan?.valid == false }
}

public data class MParam(val name: String, val type: MType)

public data class MapperMethod(
    val name: String,
    val parameters: List<MParam>,
    val returns: MType,
    val body: MethodBody,
    /** Post-función: nombre del método default `after<Método>` a invocar sobre el resultado. */
    val afterFunction: String? = null,
)

public sealed interface MethodBody {
    /** La extension `@MapTo` del mismo par existe: `return p.toPersonDto()`. */
    public data class DelegateToExtension(
        val receiverParam: String,
        /** Qualified extension function (e.g. `sample.toPersonDto`) — el backend genera el import. */
        val qualifiedFunction: String,
    ) : MethodBody

    /** No hay extension declarada: el plan (mismo motor) se materializa en el cuerpo del método. */
    public data class InlineConstruction(
        val receiverParam: String,
        val plan: MappingPlan,
        /** Parámetros extra del método usados como fuentes suplementarias (se referencian sin receiver). */
        val supplementaryParams: Set<String> = emptySet(),
    ) : MethodBody

    /**
     * Método de COLECCIÓN: el elemento se resuelve por DELEGACIÓN, nunca
     * inline (los anidados siempre por referencia a una función nombrada).
     */
    public data class CollectionDelegate(
        val receiverParam: String,
        /** El contenedor DESTINO decide la materialización (LIST → `map {}`, SET → `mapTo(mutableSetOf())`). */
        val into: MType,
        val element: ElementCall,
    ) : MethodBody

    /**
     * PATCH detectado por FORMA (`fun x(target: T, patch: P): T`): actualización
     * parcial inmutable vía `target.copy(...)`. Semántica por defecto: null en el patch = NO tocar
     * (`copy(x = patch.x ?: target.x)`); para SETEAR null, el campo opta por `Patch<T>`
     * (tri-estado). T debe ser data class (KMX012).
     */
    public data class PatchApplication(
        val targetParam: MParam,
        val patchParam: MParam,
        /** Campos del target presentes en el patch; los ausentes se conservan (no aparecen en el copy). */
        val fields: List<PatchField>,
    ) : MethodBody
}

/** La llamada por ELEMENTO de un método de colección. */
public sealed interface ElementCall {
    /** Método hermano del MISMO mapper con el par exacto: `items.map { toDto(it) }`. */
    public data class SelfMethod(val name: String) : ElementCall

    /** Extension declarada del par (embedded u otro origen): `items.map { it.toOrderDto() }`. */
    public data class Extension(val qualifiedFunction: String) : ElementCall
}

public data class PatchField(
    /** Nombre del campo del TARGET en el `copy(...)`. */
    val name: String,
    /** Valor desde el patch — las refs son propiedades del PATCH; wrap/unwrap y converters aplican. */
    val value: ValueSource,
    /** Patch nullable → `?: target.<name>` (null = no tocar). No-nullable → asignación incondicional. */
    val fallbackToTarget: Boolean,
    /**
     * Campo `Patch<T>` (tri-estado). El emisor genera un `when` exhaustivo
     * (`Keep -> target.x`, `is Set -> <value con p.value>`); [value] referencia el valor como
     * `value` (el `Set.value`). Excluye [fallbackToTarget].
     */
    val tristate: Boolean = false,
)
