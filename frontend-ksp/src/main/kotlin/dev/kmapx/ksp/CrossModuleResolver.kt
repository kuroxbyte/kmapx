package dev.kmapx.ksp

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSFunctionDeclaration

/**
 * Resolución cross-module de un par anidado (design/cross-module-mapping.md): descubre en el
 * CLASSPATH la extensión que otro módulo generó, marcada con `@GeneratedMapping(source, target)`.
 *
 * El `Resolver` de KSP sí ve las dependencias — `getDeclarationsFromPackage` incluye las
 * declaraciones compiladas del paquete. La extensión generada vive en el paquete del tipo FUENTE,
 * así que escaneamos solo ese paquete (una vez, cacheado). Devuelve el FQN de la función para que
 * el motor emita `ViaMapper(GeneratedExtension(fqn))`, idéntico a un par local.
 */
internal class CrossModuleResolver(private val resolver: Resolver) {

    /** Paquete → ((sourceQn, targetQn) → FQN de la extensión). Escaneo perezoso por paquete. */
    private val byPackage = mutableMapOf<String, Map<Pair<String, String>, String>>()

    fun lookup(sourceQn: String, targetQn: String): String? {
        val pkg = sourceQn.substringBeforeLast('.', missingDelimiterValue = "")
        return byPackage.getOrPut(pkg) { scan(pkg) }[sourceQn to targetQn]
    }

    @OptIn(com.google.devtools.ksp.KspExperimental::class)
    private fun scan(pkg: String): Map<Pair<String, String>, String> =
        resolver.getDeclarationsFromPackage(pkg)
            .filterIsInstance<KSFunctionDeclaration>()
            .mapNotNull { fn ->
                val marker = fn.annotations
                    .firstOrNull { it.qualifiedName() == GENERATED_MAPPING } ?: return@mapNotNull null
                val source = marker.stringArg("source") ?: return@mapNotNull null
                val target = marker.stringArg("target") ?: return@mapNotNull null
                val fqn = fn.qualifiedName?.asString() ?: return@mapNotNull null
                (source to target) to fqn
            }
            .toMap()

    private companion object {
        const val GENERATED_MAPPING = "dev.kmapx.annotations.GeneratedMapping"
    }
}
