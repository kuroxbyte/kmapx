package dev.kmapx.gradle

import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property

/**
 * Nivel GLOBAL de la cascada `onNull` (campo > mapper/mapeo > global) ante `T? -> T`
 * sin estrategia. Las condicionales aplican donde pueden: `TYPE_DEFAULT` (colecciones →
 * `?: emptyXxx()`), `TARGET_DEFAULT` (default del constructor → omite el argumento).
 * `LITERAL`/`UNSAFE` no existen a este nivel: son per-field (`@MapField`).
 */
public enum class OnNull { STRICT, THROW, TYPE_DEFAULT, TARGET_DEFAULT }

/**
 * Configuración del bloque `kmapx { }`. Traducida a opciones del processor KSP por [KmapxPlugin].
 * Los defaults valen para TODO el módulo; una anotación explícita ENCIENDE (opción A: aditivo).
 *
 * ```kotlin
 * kmapx {
 *     report("json", "html")            // Reporte de cobertura
 *     moduleName.set("orders")
 *
 *     useSerialNames.set(true)          // @SerialName como alias en todos los @MapTo
 *     onNull.set(OnNull.THROW)          // cascada: nivel global
 *     warningsAsErrors.set(true)        // CI duro: los KMXnnn WARNING fallan el build
 * }
 * ```
 */
public abstract class KmapxExtension {
    /** Formatos del reporte de cobertura (`json`, `html`). Vacío = desactivado. */
    public abstract val report: ListProperty<String>

    /** Nombre del módulo mostrado en el reporte. */
    public abstract val moduleName: Property<String>

    /** `@SerialName` del source como alias de matching, por defecto en todo el módulo. */
    public abstract val useSerialNames: Property<Boolean>

    /** Política global de `T? -> T` sin estrategia (el último nivel de la cascada). */
    public abstract val onNull: Property<OnNull>

    /** Sube los diagnósticos WARNING (KMXnnn) a error. */
    public abstract val warningsAsErrors: Property<Boolean>

    /** Azúcar: `report("json", "html")`. */
    public fun report(vararg formats: String) {
        report.set(formats.toList())
    }
}
