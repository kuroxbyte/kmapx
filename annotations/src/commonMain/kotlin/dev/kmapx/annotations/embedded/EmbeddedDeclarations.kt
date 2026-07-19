package dev.kmapx.annotations.embedded

import dev.kmapx.annotations.OnNull
import dev.kmapx.annotations.Unmapped
import kotlin.reflect.KClass

// Modo EMBEDDED (ex modo A, estilo JPA/Jackson): la DECLARACIÓN del mapeo vive
// embebida en el modelo. La config por campo (`@MapField`) y los hints de construcción/pareo
// (`@MapConstructor`, `@MapFactory`, `@MapSubtype`, `@MapEntry`) son COMPARTIDOS entre modos
// y viven en el paquete raíz.

/**
 * Declares that an extension function `fun Source.toTarget(): Target` must be generated
 * for the annotated class (EMBEDDED mode, ex mode A — config lives on the model).
 *
 * Repeatable: each occurrence generates one function; all of them live in the same
 * `<Source>Mappings.kt` file.
 *
 * @param target the class to map to.
 * @param name overrides the generated function name (default: `to<TargetSimpleName>`).
 * Required when two targets share a simple name — otherwise KMX013.
 * @param onNull mapping-level null policy (cascade): applies to every field of this
 * mapping unless the field declares its own. `TARGET_DEFAULT` here is the old `useTargetDefaults`
 * opt-in. `LITERAL`/`UNSAFE` are field-only (KMX041).
 */
@Repeatable
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
public annotation class MapTo(
    val target: KClass<*>,
    val name: String = "",
    val onNull: OnNull = OnNull.INHERIT,
    /** Acepta `@SerialName` del SOURCE como alias de matching (el nombre real gana). */
    val useSerialNames: Boolean = false,
    /** Habilita las conversiones ESTÁNDAR opt-in (`String↔UUID`…) en este mapeo. */
    val stdConverters: Boolean = false,
    /** Severidad de la omisión (KMX021) en este mapeo. */
    val unmapped: Unmapped = Unmapped.INHERIT,
    /** Campos destino excluidos de este mapeo. Se une con el ignore por campo. */
    val ignore: Array<String> = [],
)

/**
 * Declares a BIDIRECTIONAL mapping: generates `A.toB(): B` AND `B.toA(): A` from one
 * declaration, both in `<A>Mappings.kt`. Invertibility is VALIDATED at compile time (KMX028):
 * renames invert automatically; a converter requires its inverse registered; `T -> T?` widening
 * requires a null-handling strategy declared for the way back; fan-out is not invertible.
 * kmapx validates STRUCTURE — converters being exact inverses is the user's responsibility.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
public annotation class BiMapTo(
    val target: KClass<*>,
    val name: String = "",
    val reverseName: String = "",
    /** Acepta `@SerialName` como alias de matching en ambas direcciones. */
    val useSerialNames: Boolean = false,
)
