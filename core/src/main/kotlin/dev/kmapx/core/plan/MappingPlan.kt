package dev.kmapx.core.plan

import dev.kmapx.core.diagnostics.Diagnostic
import dev.kmapx.core.model.MType

/**
 * The engine's verdict: how to build the target, as pure data.
 * Backends only materialize this; they apply no mapping rules of their own.
 * Q2 (spec): the plan is returned PARTIAL alongside diagnostics, so multiple errors
 * can be reported in one compilation round. `valid == false` means: do not emit.
 */
public data class MappingPlan(
    val source: MType,
    val target: MType,
    val emission: Emission,
    val construction: Construction?,
    val diagnostics: List<Diagnostic> = emptyList(),
) {
    public val valid: Boolean get() = diagnostics.none { it.severity == dev.kmapx.core.diagnostics.Severity.ERROR }
}

public sealed interface Emission {
    /** [isInternal]: the function replicates the source class visibility. */
    public data class ExtensionFunction(val name: String, val isInternal: Boolean = false) : Emission
    public data class InterfaceImpl(val interfaceName: String, val componentModel: Component) : Emission
    public data object Patch : Emission

    public enum class Component { NONE, SPRING, KOIN }
}

public sealed interface Construction {
    /** Constructor invocation: the primary, or a `@MapConstructor` secondary. */
    public data class ConstructorCall(
        val arguments: List<Argument>,
        /** Mutable `var`s outside the constructor, assigned post-construction via `.also { }`. */
        val postAssignments: List<PostAssignment> = emptyList(),
    ) : Construction

    /** `@MapFactory` invocation: companion (`Target.fn(...)`) or top-level (`fn(...)`). */
    public data class FactoryCall(
        val qualifiedFunction: String,
        /** Qualified name of the class whose companion declares the factory; null for top-level. */
        val companionOf: String?,
        val arguments: List<Argument>,
        val postAssignments: List<PostAssignment> = emptyList(),
    ) : Construction

    /** Exhaustive `when` over parallel sealed hierarchies — no `else` branch, ever. */
    public data class SealedDispatch(val branches: List<Branch>) : Construction

    /** `data object` counterparts. */
    public data class ObjectReference(val qualifiedName: String) : Construction

    /** `when` exhaustivo por IGUALDAD entre entries de enums paralelos — sin `else`, jamás. */
    public data class EnumDispatch(val entries: List<EnumBranch>) : Construction
}

/** Rama entry→entry por nombre SIMPLE (el par de enums vive en el plan: source/target). */
public data class EnumBranch(
    val sourceEntry: String,
    val targetEntry: String,
)

public data class Branch(
    val sourceSubtype: String,
    val plan: MappingPlan,
)

public data class Argument(
    val paramName: String,
    val value: ValueSource,
)

public data class PostAssignment(
    val propertyName: String,
    val value: ValueSource,
)

/** Acceso a la fuente: nombre plano o la EXPRESIÓN de ruta (`address?.city`). */
public data class PropRef(val name: String)

public fun ref(name: String): PropRef = PropRef(name)

public data class ConverterRef(val qualifiedFunction: String)

public sealed interface MapperRef {
    public data class GeneratedExtension(val qualifiedFunction: String) : MapperRef
    public data class UserConverter(val ref: ConverterRef) : MapperRef
}

/** Traceability: WHY the engine chose this resolution. */
public enum class Resolution { USER_CONVERTER, DECLARED_MAPTO, IMPLICIT_SAFE }

/** Desenlace de la estrategia de nulabilidad cuando envuelve una conversión estructural. */
public sealed interface StrategyOutcome {
    public data class Fallback(val default: String) : StrategyOutcome
    public data class Throw(val message: String) : StrategyOutcome
    public data object Unsafe : StrategyOutcome
}

public sealed interface ValueSource {
    public data class Direct(val source: PropRef) : ValueSource

    public data class ViaConverter(
        val source: PropRef,
        val converter: ConverterRef,
        val origin: Resolution = Resolution.USER_CONVERTER,
        /** Fuente `A?` hacia target `B?` — se envuelve con `?.let { conv(it) }`. */
        val safeCall: Boolean = false,
    ) : ValueSource

    /**
     * Converter calificado por `@UseConverter` — `ShortDate.convert(x)`.
     * [safeCall]: fuente `A?` → `x?.let(ShortDate::convert)`. Como elemento de colección se emite
     * `ShortDate::convert` (referencia de método) dentro del `map { }`.
     */
    public data class ViaQualifiedConverter(
        val source: PropRef,
        /** Qualified name of the `object`/`class` implementing `Converts<A, B>`. */
        val converterObject: String,
        val origin: Resolution = Resolution.USER_CONVERTER,
        val safeCall: Boolean = false,
        /** `true` = bean inyectado (`instance.convert(x)`); `false` = object estático. */
        val injected: Boolean = false,
    ) : ValueSource

    /** Nested mappings are ALWAYS a reference to a named function — never inline. */
    public data class ViaMapper(
        val source: PropRef,
        val mapper: MapperRef,
        val origin: Resolution = Resolution.DECLARED_MAPTO,
        /** Fuente `A?` hacia target `B?` — `address?.toAddressDto()`. */
        val safeCall: Boolean = false,
    ) : ValueSource

    /**
     * Widening numérico sin pérdida de la lista cerrada — `x.toLong()`;
     * [safeCall]: fuente nullable → `x?.toLong()`. Siempre [Resolution.IMPLICIT_SAFE].
     */
    public data class NumericWidening(
        val source: PropRef,
        /** La función de conversión (`toLong`, `toDouble`…). */
        val toFunction: String,
        val safeCall: Boolean = false,
    ) : ValueSource

    /**
     * Conversión estándar opt-in (`stdConverters`) — [template] es la llamada
     * CALIFICADA con `%s` como la expresión fuente (`java.util.UUID.fromString(%s)`);
     * [safeCall]: fuente nullable → `x?.let { ... }`.
     */
    public data class BuiltinConversion(
        val source: PropRef,
        val template: String,
        val safeCall: Boolean = false,
    ) : ValueSource

    /** `UserId` -> `String` (read `.value`); [safeCall]: fuente nullable → `id?.value`. */
    public data class UnwrapValueClass(val source: PropRef, val safeCall: Boolean = false) : ValueSource

    /** `String` -> `UserId` (constructor); [safeCall]: fuente nullable → `s?.let { UserId(it) }`. */
    public data class WrapValueClass(
        val source: PropRef,
        val into: MType,
        val safeCall: Boolean = false,
    ) : ValueSource

    /**
     * Estrategia de nulabilidad aplicada SOBRE el resultado de una conversión
     * estructural nullable (`id?.value ?: throw ...`, `s?.let { UserId(it) }!!`).
     */
    public data class NullStrategyOver(val inner: ValueSource, val outcome: StrategyOutcome) : ValueSource

    /** When the nullable source is null, OMIT the argument so the target default applies. */
    public data class NullFallbackToDefault(val source: PropRef) : ValueSource

    /** @WithDefault: when the nullable source is null, use the given constant. */
    public data class NullFallbackToValue(val source: PropRef, val default: String) : ValueSource

    /** @OrThrow. [message] lo compone el motor: nombra el campo y el par de tipos. */
    public data class NullOrThrow(val source: PropRef, val message: String) : ValueSource

    /** @AllowUnsafe: conscious `!!`. */
    public data class NullUnsafe(val source: PropRef) : ValueSource

    /**
     * Mapeo de `Map` por entradas — clave (`k`) y valor (`v`) cada uno por la cadena
     * estándar; null = ese lado queda idéntico. Emisión: `mapValues` / `mapKeys` /
     * `buildMap` según qué lados cambian.
     */
    public data class MapEntries(
        val source: PropRef,
        val key: ValueSource?,
        val value: ValueSource?,
    ) : ValueSource

    /**
     * Element-wise mapping over a collection, single pass, no intermediate copies.
     * [element] referencia el elemento como `it`; [into] decide la materialización
     * (LIST → `map { }`, SET → `mapTo(mutableSetOf()) { }`, ARRAY → `map { }.toTypedArray()`,
     * RESULT → `map { }`).
     */
    public data class MapElements(
        val source: PropRef,
        val element: ValueSource,
        val into: dev.kmapx.core.model.TypeKind = dev.kmapx.core.model.TypeKind.COLLECTION_LIST,
        /** Fuente `Sequence` (lazy) → agrega `.toList()` al materializar a `List` (`map{}` es lazy). */
        val lazySource: Boolean = false,
    ) : ValueSource
}
