package dev.kmapx.core.model

/**
 * Domain type model (modelo de dominio).
 *
 * Principles:
 * - Immutable plain data. No laziness, no compiler references. Adapters translate eagerly by copy.
 * - Models ONLY what mapping needs — this is not a universal type model.
 * - `hasDefault` is a boolean: the default VALUE is not observable through KSP .
 */
public enum class TypeKind {
    DATA_CLASS,
    REGULAR_CLASS,
    VALUE_CLASS,
    SEALED_CLASS,
    SEALED_INTERFACE,
    ENUM,
    OBJECT,
    COLLECTION_LIST,
    COLLECTION_SET,
    COLLECTION_MAP,

    /** `Iterable<T>`/`Collection<T>` — SOLO como fuente; `.map{}` produce `List`. */
    COLLECTION_ITERABLE,

    /** `Sequence<T>` — SOLO como fuente (lazy); exige `.toList()` al materializar a `List`. */
    COLLECTION_SEQUENCE,

    /** `Array<T>` (los arrays primitivos quedan como REGULAR_CLASS: solo passthrough). */
    COLLECTION_ARRAY,

    /** `kotlin.Result<T>` (¡es value class! — los adapters lo clasifican ANTES que VALUE_CLASS). */
    RESULT,
    JAVA_CLASS,
    OTHER,
}

public data class MType(
    val qualifiedName: String,
    val nullable: Boolean,
    val kind: TypeKind = TypeKind.OTHER,
    val typeArgs: List<MType> = emptyList(),
    /** Only for [TypeKind.VALUE_CLASS]: the single underlying property's type. */
    val underlying: MType? = null,
    /**
     * Paquete real, para distinguir anidamiento (`sample.Event.Approved` con paquete
     * `sample` es la clase anidada `Event.Approved`). null = derivar de [qualifiedName]
     * (correcto solo para clases top-level).
     */
    val packageName: String? = null,
) {
    public val simpleName: String get() = qualifiedName.substringAfterLast('.')

    /** Same type ignoring nullability. */
    public fun sameTypeAs(other: MType): Boolean =
        qualifiedName == other.qualifiedName && typeArgs == other.typeArgs

    public fun asNullable(): MType = if (nullable) this else copy(nullable = true)
    public fun asNonNullable(): MType = if (!nullable) this else copy(nullable = false)
}

/**
 * Estrategia declarada por el usuario para la celda `T? -> T`.
 * Se modela como LISTA en params/properties: más de una declarada es KMX016 (regla del motor,
 * no del translator — el translator solo copia lo que ve).
 */
public sealed interface MNullStrategy {
    /**
     * `onNull = STRICT` EXPLÍCITO en el campo — corta la cascada de políticas de nivel
     * (la violación vuelve a ser KMX003 aunque el mapper/global declaren otra salida).
     */
    public data object Strict : MNullStrategy

    public data class WithDefault(val literal: String) : MNullStrategy
    public data object OrThrow : MNullStrategy
    public data object AllowUnsafe : MNullStrategy

    /** Colección nullable → colección vacía (`?: emptyList()`). Solo List/Set/Map (KMX033). */
    public data object OrEmpty : MNullStrategy

    /**
     * `onNull = TARGET_DEFAULT`: omite el argumento para que aplique el default del
     * constructor — el target default per-field. Exige default declarado y tipos iguales (KMX040).
     */
    public data object TargetDefault : MNullStrategy
}

/**
 * Converter calificado referenciado por `@UseConverter(Object::class)`.
 * [objectQualifiedName] es el `object` que implementa `dev.kmapx.runtime.Converts<A,B>`.
 * [fromType]/[toType] son A/B, leídos por el frontend de la supertype `Converts`; AMBOS null
 * cuando el object no implementa `Converts` (→ KMX029). El motor valida el encaje (KMX027).
 */
public data class MQualifiedConverter(
    val objectQualifiedName: String,
    val fromType: MType?,
    val toType: MType?,
    /**
     * `true` si es un `object` (estático, `X.convert(x)`); `false` si es una `class` (bean
     * con dependencias, inyectada — solo modo B).
     */
    val isObject: Boolean = true,
)

public data class MProperty(
    val name: String,
    val type: MType,
    val mutable: Boolean = false,
    /** Declared in the primary constructor (vs. class body). */
    val inConstructor: Boolean = true,
    /** Computed getter (`val x get() = ...`): readable as source, never assignable as target. */
    val computed: Boolean = false,
    /** Strategies declared on this TARGET property (post-assignments participate). */
    val strategies: List<MNullStrategy> = emptyList(),
    /** `@MapFrom(from = "...")` — redirige el matching a esa propiedad del source. */
    val mappedFrom: String? = null,
    /** `@SerialName("x")` del SOURCE — alias de matching, SOLO con `useSerialNames = true`. */
    val serialName: String? = null,
    /** `@UseConverter(Object::class)` sobre esta propiedad target. */
    val useConverter: MQualifiedConverter? = null,
    /** Excluida deliberadamente — la post-asignación se omite. */
    val ignored: Boolean = false,
)

public data class MConstructorParam(
    val name: String,
    val type: MType,
    val hasDefault: Boolean = false,
    /** Strategies declared on this TARGET parameter. */
    val strategies: List<MNullStrategy> = emptyList(),
    /** `@MapFrom(from = "...")` — redirige el matching a esa propiedad del source. */
    val mappedFrom: String? = null,
    /** `@UseConverter(Object::class)` sobre este parámetro target. */
    val useConverter: MQualifiedConverter? = null,
    /** Excluido deliberadamente — el argumento se OMITE (exige default, KMX042). */
    val ignored: Boolean = false,
)

public data class MConstructor(
    val params: List<MConstructorParam>,
    val isPrimary: Boolean = true,
    val visible: Boolean = true,
    /** User opt-in via @MapConstructor (resolution step 2). */
    val annotatedMapConstructor: Boolean = false,
)

public data class MFactory(
    /** Fully-qualified function reference (e.g. `pkg.Temperature.fromKelvin` or `pkg.fromKelvin`). */
    val qualifiedName: String,
    val params: List<MConstructorParam>,
    /** Qualified name of the class whose companion declares it; null for a top-level factory. */
    val companionOf: String? = null,
) {
    public val simpleName: String get() = qualifiedName.substringAfterLast('.')
}

/** Un segmento de ruta ya NAVEGADO por el frontend (el motor juzga; no navega). */
public data class MPathSegment(val name: String, val nullable: Boolean)

/** Resultado de pre-resolver una ruta `a.b.c` contra el SOURCE (lo hace el frontend). */
public sealed interface MPath {
    public data class Resolved(
        val segments: List<MPathSegment>,
        val finalType: MType,
    ) : MPath

    /** El segmento [failedSegment] no existe en [ownerSimpleName]; [candidates] para did-you-mean. */
    public data class Missing(
        val failedSegment: String,
        val ownerSimpleName: String,
        val candidates: List<String>,
    ) : MPath
}

/** Entry de un enum SOURCE; [targetOverride] viene de `@MapEntry(target = "...")`. */
public data class MEnumEntry(
    val name: String,
    val targetOverride: String? = null,
)

public data class MClass(
    val type: MType,
    val properties: List<MProperty> = emptyList(),
    val constructors: List<MConstructor> = emptyList(),
    val factories: List<MFactory> = emptyList(),
    /** Only for sealed kinds: direct subtypes, translated eagerly. */
    val sealedSubtypes: List<MClass> = emptyList(),
    /** `@MapSubtype(target = X::class)` sobre este subtipo SOURCE — qualified name del target. */
    val subtypeTargetOverride: String? = null,
    /** Solo para [TypeKind.ENUM] — entries en orden de declaración. */
    val enumEntries: List<MEnumEntry> = emptyList(),
    /**
     * `@MapEntry` en sede de CLASE sobre el enum SOURCE — el entry destino para
     * todo entry sin par (los overrides por entry ganan). null = sin fallback (KMX026 aplica).
     */
    val enumFallback: String? = null,
) {
    public val primaryConstructor: MConstructor? get() = constructors.firstOrNull { it.isPrimary }
    public fun property(name: String): MProperty? = properties.firstOrNull { it.name == name }

    /** Todos los nombres DIRECCIONABLES como destino (params de constructores + properties). */
    public fun fieldNames(): Set<String> =
        (constructors.flatMap { c -> c.params.map { it.name } } + properties.map { it.name }).toSet()
}
