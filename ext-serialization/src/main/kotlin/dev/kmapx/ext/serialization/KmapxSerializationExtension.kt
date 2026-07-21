package dev.kmapx.ext.serialization

import dev.kmapx.spi.ConverterPair
import dev.kmapx.spi.KmapxExperimentalSpi
import dev.kmapx.spi.KmapxExtension

/** Registra el par `JsonElement <-> String`. Descubierto por ServiceLoader desde `ksp(...)`. */
@OptIn(KmapxExperimentalSpi::class)
public class KmapxSerializationExtension : KmapxExtension {

    override fun contributeConverters(): Map<ConverterPair, String> {
        val json = "kotlinx.serialization.json.JsonElement"
        val string = "kotlin.String"
        val fns = "dev.kmapx.ext.serialization"
        return mapOf(
            ConverterPair(json, string) to "$fns.jsonElementToString",
            ConverterPair(string, json) to "$fns.jsonElementFromString",
        )
    }
}
