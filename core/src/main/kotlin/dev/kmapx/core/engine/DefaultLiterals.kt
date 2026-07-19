package dev.kmapx.core.engine

import dev.kmapx.core.model.MType
import dev.kmapx.core.model.TypeKind

/**
 * Parseo TIPADO de `@WithDefault("...")`: interpreta el literal según el tipo target y
 * devuelve la fuente Kotlin ya renderizada (el backend la emite tal cual).
 *
 * SRP: es una responsabilidad propia (string del usuario → literal Kotlin válido, por tipo),
 * independiente de la cadena de resolución. Vive fuera del motor para que el motor solo DECIDA y
 * este objeto solo TRADUZCA. Enums: por nombre, calificado (un nombre inválido lo atrapa la
 * compilación del generado). Q1 (DoR): tipos con `valueOf` → converter, no aquí.
 */
internal object DefaultLiterals {

    sealed interface Parsed {
        data class Rendered(val code: String) : Parsed
        data object InvalidLiteral : Parsed
        data object UnsupportedType : Parsed
    }

    fun render(literal: String, target: MType): Parsed {
        fun rendered(code: String) = Parsed.Rendered(code)
        fun <V> parsedOrInvalid(value: V?, render: (V) -> String): Parsed =
            value?.let { rendered(render(it)) } ?: Parsed.InvalidLiteral

        return when (target.qualifiedName) {
            "kotlin.String" -> rendered("\"${literal.replace("\\", "\\\\").replace("\"", "\\\"")}\"")
            "kotlin.Int" -> parsedOrInvalid(literal.toIntOrNull()) { "$it" }
            "kotlin.Long" -> parsedOrInvalid(literal.toLongOrNull()) { "${it}L" }
            "kotlin.Short" -> parsedOrInvalid(literal.toShortOrNull()) { "($it).toShort()" }
            "kotlin.Byte" -> parsedOrInvalid(literal.toByteOrNull()) { "($it).toByte()" }
            // No-finitos rechazados: "Infinity"/"NaN" PARSEAN pero renderizarían Kotlin
            // incompilable ("Infinityf") — mejor KMX017 que un error críptico del generado.
            "kotlin.Double" -> parsedOrInvalid(literal.toDoubleOrNull()?.takeIf { it.isFinite() }) { "$it" }
            "kotlin.Float" -> parsedOrInvalid(literal.toFloatOrNull()?.takeIf { it.isFinite() }) { "${it}f" }
            "kotlin.Boolean" -> when (literal) {
                "true", "false" -> rendered(literal)
                else -> Parsed.InvalidLiteral
            }
            "kotlin.Char" ->
                if (literal.length == 1) rendered("'${literal.replace("\\", "\\\\").replace("'", "\\'")}'")
                else Parsed.InvalidLiteral
            else ->
                if (target.kind == TypeKind.ENUM) rendered("${target.qualifiedName}.$literal")
                else Parsed.UnsupportedType
        }
    }
}
