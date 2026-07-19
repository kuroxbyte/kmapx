package dev.kmapx.annotations.contract

import dev.kmapx.annotations.OnNull
import dev.kmapx.annotations.Unmapped
import kotlin.reflect.KClass

// Modo CONTRACT (ex modo B, estilo MapStruct/DDD): el mapeo se declara como
// CONTRATO — una interfaz en la capa de infraestructura; el dominio queda limpio.

/**
 * Component model for generated [Mapper] implementations (interpreted by the emitter only).
 * `INHERIT` (the default): look at the [MapperConfig] profile, then `NONE` — the same
 * lesson as `OnNull.STRICT`/`INHERIT`: the default must not collide with a meaningful value.
 */
public enum class ComponentModel { INHERIT, NONE, SPRING, KOIN }

/**
 * Reusable settings profile: an interface (with NO abstract methods) carrying the
 * interface-level settings of [Mapper]; it generates NOTHING. A mapper opts in with
 * `@Mapper(config = X::class)` (KMX044 if X is not a `@MapperConfig`). Resolution:
 *  - [componentModel]: mapper wins if not `INHERIT`, then profile, then `NONE`.
 *  - [onNull]: one more level of the cascade — `field > mapper > profile > global`.
 *  - [useSerialNames]: additive (mapper OR profile OR global), option A.
 *  - [ignore]: UNITED with the mapper's list and the per-field `@MapField(ignore = true)`.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
public annotation class MapperConfig(
    val componentModel: ComponentModel = ComponentModel.INHERIT,
    val onNull: OnNull = OnNull.INHERIT,
    val useSerialNames: Boolean = false,
    /** Conversiones estándar opt-in — aditivo (mapper OR profile OR global). */
    val stdConverters: Boolean = false,
    /** Severidad de la omisión (KMX021) — un nivel más de la cascada. */
    val unmapped: Unmapped = Unmapped.INHERIT,
    val ignore: Array<String> = [],
)

/**
 * Declares a mapper interface (CONTRACT mode, ex mode B — the mapping is a contract in the
 * infrastructure layer; clean domain). Generates `class XImpl : X` (or `object`
 * when it has no dependencies and componentModel is NONE), delegating to embedded extensions.
 *
 * The method SHAPE selects the semantics:
 *  - `fun toDto(s: Src, extra: X...): Dto` → mapping (first param = source, extras supplementary).
 *  - `fun apply(target: T, patch: P): T` (return type == FIRST parameter type, exactly 2 params)
 *    → PATCH: immutable partial update via `copy()`. T must be a data class (
 *    KMX012). Null in the patch = keep; to SET null, type the field as `dev.kmapx.runtime.Patch<T>`
 *    (tri-state). Post-function: `after<Method>(target, patch, result): result`.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
public annotation class Mapper(
    /** `INHERIT` (default): hereda del profile de [config]; sin profile = `NONE`. */
    val componentModel: ComponentModel = ComponentModel.INHERIT,
    /** Profile `@MapperConfig` con los settings reutilizables. `Unit::class` = sin profile. */
    val config: KClass<*> = Unit::class,
    /**
     * Hereda la config por método (`@MapField`) de otro `@Mapper`, emparejando por NOMBRE
     * de método; la `@MapField` propia del método derivado gana por campo destino.
     * `Unit::class` (default) = sin herencia.
     */
    val inheritFrom: KClass<*> = Unit::class,
    /**
     * Política de nulabilidad para TODOS los métodos de la interfaz (cascada:
     * campo > mapper > global). `TARGET_DEFAULT` = el viejo `useTargetDefaults`;
     * `LITERAL`/`UNSAFE` son per-field (KMX041).
     */
    val onNull: OnNull = OnNull.INHERIT,
    /** `@SerialName` del source como alias de matching, para toda la interfaz (aditivo). */
    val useSerialNames: Boolean = false,
    /** Conversiones estándar opt-in (`String↔UUID`…) — aditivo, como serialNames. */
    val stdConverters: Boolean = false,
    /** Severidad de la omisión (KMX021) para toda la interfaz. */
    val unmapped: Unmapped = Unmapped.INHERIT,
    /**
     * Campos destino excluidos en TODOS los métodos de la interfaz (auditoría:
     * `createdAt`…). Se UNE con los `@MapField(ignore = true)` por campo. Nombre inexistente = KMX011.
     */
    val ignore: Array<String> = [],
)

/**
 * Contract-mode counterpart of [BiMapTo]: the annotated `@Mapper` method is the
 * INVERSE of a sibling method. It inherits the forward method's config INVERTED (renames flip,
 * converters require their registered inverse) and invertibility is validated (KMX028) — the
 * same engine machinery as the bidirectional inversion.
 *
 * ```kotlin
 * @Mapper interface CustomerMapper {
 *     @MapField(target = "displayName", from = "name")
 *     fun toDto(c: Customer): CustomerDto
 *
 *     @InverseOf("toDto")          // "" = auto-detect the single method with the inverse signature
 *     fun fromDto(dto: CustomerDto): Customer
 * }
 * ```
 *
 * Explicit by design (unlike the structural PATCH): a method with an inverse
 * signature may legitimately want independent config — inverting silently would change
 * semantics. Constraints (KMX045): both methods take exactly ONE parameter; the forward is a
 * mapping (not a patch); an `@InverseOf` method declares no `@MapField` of its own — inversion
 * is all-or-nothing (write a regular method for custom reverse config).
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
public annotation class InverseOf(val value: String = "")
