package dev.kmapx.ksp

import dev.kmapx.spi.ConverterPair
import dev.kmapx.spi.KmapxExperimentalSpi
import dev.kmapx.spi.KmapxExtension

/**
 * Extensión de PRUEBA del SPI con tipos FICTICIOS (`spisample.LegacyId`): prueba el mecanismo
 * de descubrimiento (ServiceLoader) sin colisionar con los pares reales de otros tests. El pack
 * real `ext-jvm` se verifica aparte, en su propio módulo (su classpath no toca a este).
 */
@OptIn(KmapxExperimentalSpi::class)
class SpiTestExtension : KmapxExtension {
    override fun contributeConverters(): Map<ConverterPair, String> = mapOf(
        ConverterPair("spisample.LegacyId", "kotlin.String") to "spisample.legacyToText",
    )
}
