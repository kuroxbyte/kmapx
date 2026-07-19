package dev.kmapx.core.engine

import dev.kmapx.core.model.MType

/**
 * Las conversiones implícitas de la LISTA CERRADA, como datos puros:
 *
 *  - [widening]: numérico SIN PÉRDIDA, siempre activo. Narrowing (`Long → Int`…) queda fuera a
 *    propósito: sigue siendo KMX004 — esa decisión es del usuario, con un converter.
 *  - [standard]: conversiones estándar JVM (`String↔UUID`, `String↔BigDecimal`…), OPT-IN vía
 *    `stdConverters` (anotación o `kmapx.stdConverters`). Los `String → X` parsean y pueden
 *    lanzar en runtime — el mismo contrato que un converter del usuario. La tabla es SIMÉTRICA:
 *    cada par tiene su inverso (así `resolveBidirectional` compone).
 *
 * Un `@Converter` del usuario para el par GANA siempre (regla 1): este paso corre
 * después en la cadena.
 */
internal object ImplicitConversions {

    private const val BYTE = "kotlin.Byte"
    private const val SHORT = "kotlin.Short"
    private const val INT = "kotlin.Int"
    private const val LONG = "kotlin.Long"
    private const val FLOAT = "kotlin.Float"
    private const val DOUBLE = "kotlin.Double"

    /** (desde, hacia) → función de widening (`toLong`…). Solo conversiones sin pérdida. */
    private val widenings: Map<Pair<String, String>, String> = buildMap {
        put(BYTE to SHORT, "toShort")
        put(BYTE to INT, "toInt")
        put(BYTE to LONG, "toLong")
        put(BYTE to FLOAT, "toFloat")
        put(BYTE to DOUBLE, "toDouble")
        put(SHORT to INT, "toInt")
        put(SHORT to LONG, "toLong")
        put(SHORT to FLOAT, "toFloat")
        put(SHORT to DOUBLE, "toDouble")
        put(INT to LONG, "toLong")
        put(INT to DOUBLE, "toDouble")
        put(FLOAT to DOUBLE, "toDouble")
    }

    fun widening(s: MType, t: MType): String? = widenings[s.qualifiedName to t.qualifiedName]

    private const val STRING = "kotlin.String"
    private const val UUID = "java.util.UUID"
    private const val BIG_DECIMAL = "java.math.BigDecimal"
    private const val BIG_INTEGER = "java.math.BigInteger"
    private const val INSTANT = "java.time.Instant"

    /** (desde, hacia) → plantilla de llamada CALIFICADA (`%s` = la expresión fuente). */
    private val standards: Map<Pair<String, String>, String> = buildMap {
        put(STRING to UUID, "java.util.UUID.fromString(%s)")
        put(UUID to STRING, "%s.toString()")
        put(STRING to BIG_DECIMAL, "java.math.BigDecimal(%s)")
        put(BIG_DECIMAL to STRING, "%s.toPlainString()")
        put(STRING to BIG_INTEGER, "java.math.BigInteger(%s)")
        put(BIG_INTEGER to STRING, "%s.toString()")
        put(STRING to INSTANT, "java.time.Instant.parse(%s)")
        put(INSTANT to STRING, "%s.toString()")
        put(LONG to INSTANT, "java.time.Instant.ofEpochMilli(%s)")
        put(INSTANT to LONG, "%s.toEpochMilli()")
    }

    fun standard(s: MType, t: MType): String? = standards[s.qualifiedName to t.qualifiedName]
}
