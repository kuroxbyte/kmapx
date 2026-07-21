package dev.kmapx.annotations

import kotlin.reflect.KClass

/**
 * Registers a top-level function as a type converter. Converters are plain Kotlin
 * functions: refactor-safe, testable, never strings with code. For CHOOSING among several
 * converters for the same type pair per field, see [MapField.converter] + the `Converts` interface.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
public annotation class Converter

/**
 * Marker EMITIDO por kmapx sobre cada extensión de mapeo generada (`@MapTo`/`@BiMapTo`). No lo
 * escribas tú. Retención BINARY para que el processor de OTRO módulo lo descubra en el classpath
 * (`getDeclarationsFromPackage`) y resuelva un par anidado cross-module sin redeclararlo.
 *
 * @param source qualified name del tipo fuente. @param target qualified name del tipo destino.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
public annotation class GeneratedMapping(val source: String, val target: String)

/**
 * Null-handling strategy for `T? -> T` — ONE aspect, ONE value: declaring two
 * strategies for the same field is structurally impossible.
 */
public enum class OnNull {
    /**
     * The default at every site: no strategy HERE — the violation walks the cascade
     * `field > mapper/mapping > global`; an exhausted cascade means STRICT (KMX003).
     */
    INHERIT,

    /** EXPLICIT strict: cuts the cascade — `T? -> T` is KMX003 even if an outer level declares a way out. */
    STRICT,

    /** Use [MapField.default] (the dev's literal) → `?: <default>`. */
    LITERAL,

    /**
     * Use the type's zero/empty from a CLOSED list: `emptyList()/emptySet()/emptyMap()` for
     * identical collections; `0/0L/0.0/0.0f/false/""` for Int/Long/Short/Byte/Double/Float/Boolean/String.
     * Any other type: KMX033 on the field; as a level policy it simply falls through.
     */
    TYPE_DEFAULT,

    /** Omit the argument so the target constructor's default applies (per-field). Requires a declared default (KMX040). */
    TARGET_DEFAULT,

    /** `?: throw` with a clear message. */
    THROW,

    /** Conscious opt-in to `!!`. Prefer the other strategies. */
    UNSAFE,
}

/**
 * THE per-field configuration annotation: groups the three aspects of mapping one
 * target field — source ([from]), converter ([converter]) and null handling
 * ([onNull] + [default]) — in a single declaration. Replaces `@MapFrom`, `@UseConverter`,
 * `@WithDefault`, `@OrThrow`, `@AllowUnsafe` and `@OrEmpty`.
 *
 * Two sites, one vocabulary:
 *  - on the field (embedded mode): `@MapField(from = "address.city", onNull = OnNull.THROW)` —
 *    [target] must stay empty: the annotated field IS the destination (KMX036 otherwise).
 *  - on the mapper method (contract mode, clean domain): `@MapField(target = "city", from = "address.city")` —
 *    [target] is required (KMX036). Repeat the annotation to configure several fields; repeating
 *    a target (or the annotation on one field) is KMX037.
 *
 * @param target destination field name — the ONLY addressing parameter (method site only).
 * @param from source property, or dotted path `a.b.c` flattening nested objects; "" = no rename.
 * @param converter `KClass` implementing `dev.kmapx.runtime.Converts<A, B>` (step 0 of the resolution order:
 * wins over the global `@Converter fun` and declared mappers; KMX027/KMX029 validate it).
 * `object` = static; `class` = injected bean, contract mode only. `Unit::class` = none.
 * @param onNull strategy for `T? -> T`; exclusive by construction.
 * @param default literal for [OnNull.LITERAL] only (KMX038 if missing, KMX039 if set otherwise).
 * @param ignore Deliberately exclude this destination field — the argument is
 * OMITTED so the constructor default applies (a default is required: KMX042; for nullables use
 * `= null`). Silences KMX002/KMX021 for the field. Exclusive with the other aspects (KMX043).
 */
@Repeatable
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
public annotation class MapField(
    val target: String = "",
    val from: String = "",
    val converter: KClass<*> = Unit::class,
    val onNull: OnNull = OnNull.INHERIT,
    val default: String = MapField.UNSET_DEFAULT,
    val ignore: Boolean = false,
) {
    public companion object {
        /** Sentinel for "no default set" — `""` is a legitimate String literal. */
        public const val UNSET_DEFAULT: String = "\u0000"
    }
}

/**
 * Severity of the target-default omission ("field filled by its constructor default with
 * no matching source", KMX021). `INHERIT` walks the cascade `mapping > mapper > profile >
 * global`; the global default is `WARN` (the historical behavior — the omission is never
 * silent). `IGNORE` silences it; `ERROR` blocks the build.
 */
public enum class Unmapped { INHERIT, IGNORE, WARN, ERROR }

/**
 * Overrides subtype pairing in parallel sealed hierarchies. By default subtypes pair
 * by identical simpleName; this points the annotated SOURCE subtype at an explicit target.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
public annotation class MapSubtype(val target: KClass<*>)

/**
 * Overrides entry pairing between parallel enums. By default entries pair by identical
 * name; on a SOURCE entry this points it at an explicit target entry name. On the source enum
 * CLASS it declares the FALLBACK: every entry without a counterpart maps to
 * [target] — per-entry overrides still win, and the generated `when` keeps a branch per entry
 * (never an `else`).
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.PROPERTY, AnnotationTarget.FIELD)
@Retention(AnnotationRetention.SOURCE)
public annotation class MapEntry(val target: String)

/**
 * Silences specific kmapx WARNING diagnostics (`KMXnnn`) for the annotated field/property/class
 *. Errors are never suppressed. On a class, applies to all its fields.
 *
 * ```kotlin
 * data class Dto(@SuppressKmapx("KMX021") val nickname: String = "N/A")  // sé que lo llena el default
 * ```
 */
@Target(AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.PROPERTY, AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
public annotation class SuppressKmapx(vararg val codes: String)

/** Marks a secondary constructor as the construction mechanism (resolution step 2). */
@Target(AnnotationTarget.CONSTRUCTOR)
@Retention(AnnotationRetention.SOURCE)
public annotation class MapConstructor

/** Marks a companion/top-level function as the construction factory (resolution step 3). */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
public annotation class MapFactory
