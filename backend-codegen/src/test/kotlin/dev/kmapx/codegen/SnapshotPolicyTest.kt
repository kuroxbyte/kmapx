package dev.kmapx.codegen

import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.test.assertTrue

/**
 * DoD— aserción global del pitch del producto: NINGÚN snapshot del repo contiene `!!`
 * salvo los casos con consentimiento explícito `@AllowUnsafe` (convención: el nombre del
 * snapshot contiene "Unsafe"). Si este test falla, el motor emitió un `!!` no consentido.
 */
class SnapshotPolicyTest {

    @Test
    fun `cero dobles bang no consentidos en TODOS los snapshots`() {
        val dir = Path.of("src/test/snapshots")
        val snapshots = Files.list(dir).use { stream ->
            stream.filter { it.extension == "kt" }.toList()
        }
        assertTrue(snapshots.isNotEmpty(), "no se encontraron snapshots en $dir")

        val violations = snapshots
            .filterNot { it.name.contains("Unsafe") }
            .filter { it.readText().contains("!!") }
        assertTrue(
            violations.isEmpty(),
            "snapshots con `!!` sin consentimiento @AllowUnsafe: ${violations.map { it.name }}",
        )
    }

    /**
     * El emisor usa EXCLUSIVAMENTE stdlib común: ningún snapshot contiene `java.`
     * introducido por kmapx (los tipos java del USUARIO pueden aparecer si él los usa;
     * ninguna fixture de snapshot lo hace, así que la regla aquí es absoluta).
     */
    @Test
    fun `ningun snapshot contiene APIs java introducidas por kmapx`() {
        val dir = Path.of("src/test/snapshots")
        val snapshots = Files.list(dir).use { stream ->
            stream.filter { it.extension == "kt" }.toList()
        }
        assertTrue(snapshots.isNotEmpty())
        val violations = snapshots.filter { it.readText().contains("java.") }
        assertTrue(
            violations.isEmpty(),
            "snapshots con `java.`: ${violations.map { it.name }} — el output debe ser stdlib común",
        )
    }
}
