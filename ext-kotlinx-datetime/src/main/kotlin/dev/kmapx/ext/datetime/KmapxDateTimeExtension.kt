package dev.kmapx.ext.datetime

import dev.kmapx.spi.ConverterPair
import dev.kmapx.spi.KmapxExperimentalSpi
import dev.kmapx.spi.KmapxExtension

/** Registra los pares kotlinx-datetime <-> String/Long. Descubierto por ServiceLoader. */
@OptIn(KmapxExperimentalSpi::class)
public class KmapxDateTimeExtension : KmapxExtension {

    override fun contributeConverters(): Map<ConverterPair, String> {
        val string = "kotlin.String"
        val long = "kotlin.Long"
        val fns = "dev.kmapx.ext.datetime"
        return buildMap {
            fun pair(from: String, to: String, fn: String) = put(ConverterPair(from, to), "$fns.$fn")

            pair("kotlinx.datetime.Instant", string, "instantToIso")
            pair(string, "kotlinx.datetime.Instant", "instantFromIso")
            pair("kotlinx.datetime.Instant", long, "instantToEpochMillis")
            pair(long, "kotlinx.datetime.Instant", "instantFromEpochMillis")

            pair("kotlinx.datetime.LocalDate", string, "localDateToIso")
            pair(string, "kotlinx.datetime.LocalDate", "localDateFromIso")
            pair("kotlinx.datetime.LocalDateTime", string, "localDateTimeToIso")
            pair(string, "kotlinx.datetime.LocalDateTime", "localDateTimeFromIso")
            pair("kotlinx.datetime.LocalTime", string, "localTimeToIso")
            pair(string, "kotlinx.datetime.LocalTime", "localTimeFromIso")
        }
    }
}
