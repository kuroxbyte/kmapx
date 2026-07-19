package dev.kmapx.core

import dev.kmapx.core.diagnostics.DiagnosticCode
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * El catálogo PUBLICADO (`docs/referencia/diagnosticos.md`) no puede divergir del código —
 * mismo patrón que los snapshots de codegen: el test compara y `-Dkmapx.updateDocs=true`
 * regenera. Además exige una muestra por código (si nace un KMXnnn sin ejemplo, falla aquí
 * antes que en la doc).
 */
class DiagnosticsCatalogTest {

    private val published = File("../docs/referencia/diagnosticos.md")

    @Test
    fun `todos los codigos tienen muestra en el catalogo`() {
        assertEquals(DiagnosticCode.entries.toSet(), DiagnosticsCatalog.samples.keys)
    }

    @Test
    fun `el catalogo publicado esta en sync con el codigo`() {
        val generated = DiagnosticsCatalog.render()
        if (System.getProperty("kmapx.updateDocs") == "true") {
            published.parentFile.mkdirs()
            published.writeText(generated)
            return
        }
        assertTrue(published.exists(), "falta ${published.path} — generar con -Dkmapx.updateDocs=true")
        assertEquals(
            generated, published.readText(),
            "docs/referencia/diagnosticos.md desactualizado — regenerar con " +
                "./gradlew :core:test -Dkmapx.updateDocs=true",
        )
    }
}
