package dev.kmapx.runtime

/**
 * Tri-estado para PATCH. Resuelve la ambigüedad "ausente vs. null explícito":
 * un campo `T?` con semántica JSON-Merge-Patch no puede distinguir "no tocar" de "poné null". Con
 * este wrapper el campo del patch declara los TRES casos:
 *
 * ```
 * data class ProductPatch(val note: Patch<String?> = Patch.Keep)
 * //   Patch.Keep      -> conservar el valor actual del target
 * //   Patch.Set(null) -> BORRAR (poner null)     ← imposible con `T?`
 * //   Patch.Set("x")  -> reemplazar
 * ```
 *
 * El código generado despacha con un `when` exhaustivo (sin `else`). Es el SEGUNDO tipo del
 * `runtime`: se agrega solo cuando el usuario opta por `Patch<T>`; los patches con `T?`
 * siguen sin arrastrarlo.
 */
public sealed interface Patch<out T> {
    /** No tocar: el `copy()` conserva el valor actual del target. */
    public data object Keep : Patch<Nothing>

    /** Asignar [value] (incluido `null`) al campo del target. */
    public data class Set<out T>(public val value: T) : Patch<T>
}
