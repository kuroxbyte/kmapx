package dev.kmapx.spi

/**
 * El SPI de kmapx es EXPERIMENTAL hasta 1.0: las firmas pueden cambiar entre versiones 0.x.
 * Implementarlo exige opt-in explícito para que la decisión quede a la vista en tu código.
 */
@RequiresOptIn(
    message = "El SPI de kmapx es experimental hasta 1.0 — las firmas pueden cambiar entre 0.x.",
    level = RequiresOptIn.Level.WARNING,
)
public annotation class KmapxExperimentalSpi

/** Un par de tipos (source → target) por qualified name, sin nulabilidad ni genéricos. */
public data class ConverterPair(
    val sourceQualifiedName: String,
    val targetQualifiedName: String,
)

/**
 * Punto de extensión del processor, descubierto por `java.util.ServiceLoader`: registra tu
 * implementación en `META-INF/services/dev.kmapx.spi.KmapxExtension` y añade el jar a la
 * configuración `ksp(...)` junto al processor de kmapx.
 *
 * Contrato de seguridad: las extensiones AÑADEN caminos válidos (más pares convertibles),
 * jamás suprimen errores — un `@Converter` declarado por el usuario para el mismo par
 * SIEMPRE gana sobre el contribuido.
 */
@KmapxExperimentalSpi
public interface KmapxExtension {

    /**
     * Converters adicionales: par de tipos → FQN de una función top-level pura `(A) -> B` que
     * DEBE existir en el classpath de compilación del consumidor (tu artefacto runtime).
     * El código generado la llamará por nombre — sin reflection, como todo en kmapx.
     */
    public fun contributeConverters(): Map<ConverterPair, String> = emptyMap()
}
