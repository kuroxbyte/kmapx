package dev.kmapx.ext.jvm

import dev.kmapx.spi.ConverterPair
import dev.kmapx.spi.KmapxExperimentalSpi
import dev.kmapx.spi.KmapxExtension

/**
 * El pack JVM como extensión del SPI: registra cada par de tipos → la función de [JvmConverters]
 * que lo resuelve. Descubierto por ServiceLoader cuando el artefacto está en la config `ksp(...)`.
 * Los tipos Kotlin usan su nombre canónico (`kotlin.String`, `kotlin.Long`); los JVM su FQN.
 */
@OptIn(KmapxExperimentalSpi::class)
public class KmapxJvmExtension : KmapxExtension {

    override fun contributeConverters(): Map<ConverterPair, String> {
        val fns = "dev.kmapx.ext.jvm"
        return buildMap {
            fun pair(from: String, to: String, fn: String) = put(ConverterPair(from, to), "$fns.$fn")

            val string = "kotlin.String"
            val long = "kotlin.Long"

            pair("java.time.Instant", string, "instantToIso")
            pair(string, "java.time.Instant", "instantFromIso")
            pair("java.time.Instant", long, "instantToEpochMillis")
            pair(long, "java.time.Instant", "instantFromEpochMillis")

            pair("java.time.LocalDate", string, "localDateToIso")
            pair(string, "java.time.LocalDate", "localDateFromIso")
            pair("java.time.LocalDateTime", string, "localDateTimeToIso")
            pair(string, "java.time.LocalDateTime", "localDateTimeFromIso")
            pair("java.time.Duration", string, "durationToIso")
            pair(string, "java.time.Duration", "durationFromIso")

            pair("java.util.UUID", string, "uuidToString")
            pair(string, "java.util.UUID", "uuidFromString")

            pair("java.math.BigDecimal", string, "bigDecimalToString")
            pair(string, "java.math.BigDecimal", "bigDecimalFromString")
            pair("java.math.BigInteger", string, "bigIntegerToString")
            pair(string, "java.math.BigInteger", "bigIntegerFromString")

            pair("java.net.URI", string, "uriToString")
            pair(string, "java.net.URI", "uriFromString")
        }
    }
}
