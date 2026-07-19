package dev.kmapx.ksp

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSTypeReference
import dev.kmapx.core.model.MClass
import dev.kmapx.core.model.MPath
import dev.kmapx.core.model.MPathSegment

/**
 * El frontend NAVEGA las rutas `a.b.c`; el motor JUZGA. Única responsabilidad: recorrer
 * los `@MapFrom(from = "a.b.c")` del target contra un `root` KSP y producir el [MPath] pre-resuelto
 * (segmentos con nulabilidad + tipo final, o el segmento que falla). Simétrico a `SourceMatcher`
 * del core, que sobre ese `MPath` decide `?.`, KMX003/KMX011/KMX020.
 */
internal class PathNavigator(private val translator: KspTranslator) {

    /** Rutas del [target] (params y properties) navegadas contra [root]. */
    fun resolve(root: KSClassDeclaration, target: MClass): Map<String, MPath> =
        resolvePathsAgainst(root, dottedFroms(target))

    private fun dottedFroms(target: MClass): Set<String> =
        (target.constructors.flatMap { it.params }.mapNotNull { it.mappedFrom } +
            target.properties.mapNotNull { it.mappedFrom })
            .filter { '.' in it && it.split('.').none(String::isBlank) }
            .toSet()

    private fun resolvePathsAgainst(root: KSClassDeclaration, froms: Set<String>): Map<String, MPath> =
        froms.associateWith { from ->
            val parts = from.split('.')
            val segments = mutableListOf<MPathSegment>()
            var current: KSClassDeclaration = root
            var finalTypeRef: KSTypeReference? = null
            for ((index, part) in parts.withIndex()) {
                val prop = current.getAllProperties().firstOrNull { it.simpleName.asString() == part }
                    ?: return@associateWith MPath.Missing(
                        failedSegment = part,
                        ownerSimpleName = current.simpleName.asString(),
                        candidates = current.getAllProperties().map { it.simpleName.asString() }.toList(),
                    )
                val type = prop.type.resolve()
                segments += MPathSegment(part, type.isMarkedNullable)
                finalTypeRef = prop.type
                if (index < parts.lastIndex) {
                    current = type.declaration as? KSClassDeclaration
                        ?: return@associateWith MPath.Missing(
                            failedSegment = parts[index + 1],
                            ownerSimpleName = type.declaration.simpleName.asString(),
                            candidates = emptyList(),
                        )
                }
            }
            MPath.Resolved(segments, translator.translateType(finalTypeRef!!))
        }
}
