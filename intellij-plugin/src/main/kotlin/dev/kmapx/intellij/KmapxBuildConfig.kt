package dev.kmapx.intellij

import com.intellij.openapi.project.Project
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import dev.kmapx.core.engine.NullPolicy

/**
 * La config GLOBAL del build, visible desde el editor: cierra la
 * salvedad documentada en v0.4. Se leen los `gradle.properties` (`kmapx.onNull=…`,
 * `kmapx.stdConverters=true`) y los `build.gradle.kts` (bloque `kmapx { onNull = "…" }` /
 * `.set("…")`) del proyecto, POR TEXTO — heurística consciente: opciones KSP pasadas por otras
 * vías (args programáticos) no se detectan; en ese caso la inspección vuelve al comportamiento
 * de v0.4 (asumir STRICT), que como mucho marca de más y es desactivable.
 *
 * La interpretación de valores es la MISMA de `GlobalConfig.from` (frontend-ksp): valor
 * desconocido = STRICT (aquí null: no aporta nivel).
 */
internal object KmapxBuildConfig {

    data class Global(val onNull: NullPolicy?, val stdConverters: Boolean)

    fun of(project: Project): Global {
        val texts = filesNamed(project, "gradle.properties") + filesNamed(project, "build.gradle.kts")
        return Global(
            onNull = texts.firstNotNullOfOrNull { onNullIn(it) },
            stdConverters = texts.any { stdConvertersIn(it) },
        )
    }

    private fun filesNamed(project: Project, name: String): List<String> =
        FilenameIndex.getVirtualFilesByName(name, GlobalSearchScope.projectScope(project))
            .mapNotNull { file -> runCatching { String(file.contentsToByteArray()) }.getOrNull() }

    private val PROPERTY_ON_NULL = Regex("""kmapx\.onNull\s*=\s*([A-Za-z_]+)""")
    private val DSL_ON_NULL =
        Regex("""kmapx\s*\{[^}]*?onNull(?:\.set\(|\s*=\s*)"([A-Za-z_]+)"""", RegexOption.DOT_MATCHES_ALL)
    private val PROPERTY_STD = Regex("""kmapx\.stdConverters\s*=\s*true""")
    private val DSL_STD =
        Regex("""kmapx\s*\{[^}]*?stdConverters(?:\.set\(|\s*=\s*)true""", RegexOption.DOT_MATCHES_ALL)

    private fun onNullIn(text: String): NullPolicy? {
        val raw = PROPERTY_ON_NULL.find(text)?.groupValues?.get(1)
            ?: DSL_ON_NULL.find(text)?.groupValues?.get(1)
            ?: return null
        return when (raw.lowercase().replace("_", "")) {
            "throw", "orthrow" -> NullPolicy.OR_THROW
            "typedefault" -> NullPolicy.TYPE_DEFAULT
            "targetdefault" -> NullPolicy.TARGET_DEFAULT
            else -> null // STRICT o desconocido: no aporta nivel
        }
    }

    private fun stdConvertersIn(text: String): Boolean =
        PROPERTY_STD.containsMatchIn(text) || DSL_STD.containsMatchIn(text)
}
