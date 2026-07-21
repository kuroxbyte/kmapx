package dev.kmapx.ext.serialization

import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JsonConvertersTest {

    @Test
    fun `ida y vuelta JsonElement - String es inversa`() {
        val element = jsonElementFromString("""{"a":1,"b":["x","y"]}""")
        assertEquals(element, jsonElementFromString(jsonElementToString(element)))
        assertEquals("\"hola\"", jsonElementToString(JsonPrimitive("hola")))
    }

    @Test
    fun `la extension registra ambas direcciones hacia sus funciones`() {
        val pairs = KmapxSerializationExtension().contributeConverters()
        assertEquals(2, pairs.size)
        assertTrue(pairs.values.all { it.startsWith("dev.kmapx.ext.serialization.") }, pairs.toString())
    }
}
