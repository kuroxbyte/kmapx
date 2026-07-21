package dev.kmapx.ext.serialization

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/**
 * Converters entre `JsonElement` y `String` — el pedazo GENÉRICO de JSON que un pack puede dar
 * (no depende de tus tipos). Serializar un objeto concreto a JSON (`Meta -> String`) sigue
 * siendo un `@Converter` tuyo de una línea: es específico del tipo y responsabilidad de tu
 * capa de serialización (ver docs/guia-mapeo, "Fechas, JSON y tipos de plataforma").
 */

private val json = Json

public fun jsonElementToString(value: JsonElement): String =
    json.encodeToString(JsonElement.serializer(), value)

public fun jsonElementFromString(value: String): JsonElement =
    json.parseToJsonElement(value)
