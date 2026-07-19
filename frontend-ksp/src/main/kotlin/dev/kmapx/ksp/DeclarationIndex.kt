package dev.kmapx.ksp

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.FunctionKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.Modifier
import dev.kmapx.core.diagnostics.Diagnostics
import dev.kmapx.core.diagnostics.MLocation

/**
 * Índice de la ronda: barre el `resolver` UNA vez y expone los mapas que los handlers consultan.
 * Única responsabilidad: descubrir e indexar (`@MapTo`/`@BiMapTo` → extensions, `@Converter` →
 * registro por par, factories top-level por tipo de retorno, y la inversa función→par para el
 * grafo de ciclos). La frontera KSP se cruza aquí, no dentro de cada handler.
 */
internal class DeclarationIndex(
    /** (sourceQn, targetQn) → función `@MapTo`. */
    val mapToPairs: Map<Pair<String, String>, String>,
    /** Extensiones declaradas (mapTo + ambas direcciones de biMapTo). */
    val declaredExtensions: Map<Pair<String, String>, String>,
    /** (fromQn, toQn) → FQNs de `@Converter`. */
    val converters: Map<Pair<String, String>, List<String>>,
    /** Factories top-level agrupadas por el tipo que retornan. */
    val topLevelFactories: Map<String?, List<KSFunctionDeclaration>>,
    /** FQN de función generada → qualified name del source — para armar las aristas del grafo. */
    val functionToSource: Map<String, String>,
) {
    companion object {
        fun build(
            resolver: Resolver,
            sources: List<KSClassDeclaration>,
            reader: AnnotationReader,
            reporter: DiagnosticReporter,
        ): DeclarationIndex {
            val topLevelFactories = resolver.getSymbolsWithAnnotation(Ann.MAP_FACTORY)
                .filterIsInstance<KSFunctionDeclaration>()
                .filter { it.functionKind == FunctionKind.TOP_LEVEL }
                .groupBy { it.returnType?.resolve()?.declaration?.qualifiedName?.asString() }

            val converters = discoverConverters(resolver, reporter)

            val mapToPairs = sources
                .filter { decl -> decl.annotations.any { it.qualifiedName() == Ann.MAP_TO } }
                .flatMap { decl ->
                    reader.parseMapTo(decl).mapNotNull { mapTo ->
                        val sourceQn = decl.qualifiedName?.asString() ?: return@mapNotNull null
                        val targetQn = mapTo.target.qualifiedName?.asString() ?: return@mapNotNull null
                        (sourceQn to targetQn) to "${decl.packageName.asString()}.${mapTo.functionName}"
                    }
                }
                .toMap()

            // @BiMapTo registra AMBAS direcciones — anidados @BiMapTo son invertibles solos.
            val biPairs = sources
                .filter { decl -> decl.annotations.any { it.qualifiedName() == Ann.BIMAP_TO } }
                .flatMap { decl ->
                    reader.parseBiMapTo(decl)?.let { bi ->
                        val aQn = decl.qualifiedName?.asString() ?: return@flatMap emptyList()
                        val bQn = bi.target.qualifiedName?.asString() ?: return@flatMap emptyList()
                        val pkg = decl.packageName.asString()
                        listOf(
                            (aQn to bQn) to "$pkg.${bi.functionName}",
                            (bQn to aQn) to "$pkg.${bi.reverseFunctionName}",
                        )
                    } ?: emptyList()
                }
                .toMap()

            val declaredExtensions = mapToPairs + biPairs
            return DeclarationIndex(
                mapToPairs = mapToPairs,
                declaredExtensions = declaredExtensions,
                converters = converters,
                topLevelFactories = topLevelFactories,
                functionToSource = declaredExtensions.entries.associate { (pair, fn) -> fn to pair.first },
            )
        }

        /**
         * `@Converter fun (A) -> B` top-level, pura. Alcance v1: el módulo de compilación.
         * La clave del mapa ignora nulabilidad y genéricos (exactitud de par por qualified name).
         */
        private fun discoverConverters(
            resolver: Resolver,
            reporter: DiagnosticReporter,
        ): Map<Pair<String, String>, List<String>> {
            val registry = mutableMapOf<Pair<String, String>, MutableList<String>>()
            resolver.getSymbolsWithAnnotation(Ann.CONVERTER)
                .filterIsInstance<KSFunctionDeclaration>()
                .forEach { fn ->
                    val fqn = fn.qualifiedName?.asString() ?: fn.simpleName.asString()
                    val problem = when {
                        fn.functionKind != FunctionKind.TOP_LEVEL -> "must be a top-level function in v1"
                        fn.extensionReceiver != null -> "must not have a receiver"
                        Modifier.SUSPEND in fn.modifiers -> "must not be suspend (mapping is synchronous)"
                        fn.parameters.size != 1 -> "must take exactly one parameter, got ${fn.parameters.size}"
                        fn.returnType?.resolve()?.declaration?.qualifiedName?.asString()
                            .let { it == null || it == "kotlin.Unit" } -> "must return a value (not Unit)"
                        else -> null
                    }
                    if (problem != null) {
                        reporter.report(Diagnostics.invalidConverterSignature(MLocation(fqn), problem), fn)
                        return@forEach
                    }
                    val from = fn.parameters.single().type.resolve().declaration.qualifiedName?.asString()
                    val to = fn.returnType?.resolve()?.declaration?.qualifiedName?.asString()
                    if (from != null && to != null) {
                        registry.getOrPut(from to to) { mutableListOf() } += fqn
                    }
                }
            return registry
        }
    }
}
