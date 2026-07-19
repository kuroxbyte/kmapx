package dev.kmapx.runtime

/**
 * The SINGLE interface in the `runtime` module.
 *
 * A qualifiable converter is an `object` implementing this; it is referenced by KClass in
 * the `converter` aspect of `dev.kmapx.annotations.MapField`. The generated code
 * calls `ObjectName.convert(x)` — no
 * reflection. `fun interface` (SAM) allows concise implementations.
 *
 * `runtime` is NOT a junk drawer: any further addition needs a deliberate design decision.
 * The default remains: if it can be generated, it is generated; the runtime is the exception.
 *
 * Example:
 * ```
 * object ShortDate : Converts<LocalDate, String> {
 *     override fun convert(value: LocalDate): String = value.format(SHORT)
 * }
 * data class EventDto(@MapField(converter = ShortDate::class) val startDate: String)
 * ```
 */
public fun interface Converts<A, B> {
    public fun convert(value: A): B
}
