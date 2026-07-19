package dev.kmapx.ksp

import com.google.devtools.ksp.getDeclaredFunctions
import com.google.devtools.ksp.isAbstract
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import dev.kmapx.core.diagnostics.Diagnostics
import dev.kmapx.core.diagnostics.MLocation
import dev.kmapx.core.diagnostics.MapFieldRules
import dev.kmapx.core.engine.NullPolicy
import dev.kmapx.core.engine.UnmappedPolicy
import dev.kmapx.core.plan.Emission

internal data class DeclaredMapTo(
    val target: KSClassDeclaration,
    val functionName: String,
    /** Política de nivel MAPEO en la cascada `onNull` (null = INHERIT). */
    val onNull: NullPolicy?,
    /** @SerialName del source como alias de matching. */
    val useSerialNames: Boolean,
    /** Conversiones estándar opt-in para este mapeo. */
    val stdConverters: Boolean,
    /** Política `unmapped` de nivel MAPEO (null = INHERIT). */
    val unmapped: UnmappedPolicy?,
    /** Campos destino excluidos de este mapeo (se une con el ignore por campo). */
    val ignore: Set<String>,
)

internal data class DeclaredBiMapTo(
    val target: KSClassDeclaration,
    val functionName: String,
    val reverseFunctionName: String,
    val useSerialNames: Boolean,
)

/**
 * Lee las anotaciones de declaración (`@MapTo`, `@BiMapTo`, `componentModel`) y las traduce a
 * datos. Única responsabilidad: interpretar argumentos de anotación. Reporta KMX025/interno cuando
 * un `@MapTo` no resuelve (por eso recibe el [reporter]).
 */
internal class AnnotationReader(private val reporter: DiagnosticReporter) {

    fun parseMapTo(source: KSClassDeclaration): List<DeclaredMapTo> =
        source.annotations
            .filter { it.qualifiedName() == Ann.MAP_TO }
            .mapNotNull { annotation ->
                val target = annotation.classDeclArg("target")
                if (target == null) {
                    val location = MLocation(source.qualifiedName?.asString() ?: "<unknown>")
                    reporter.report(Diagnostics.internalError(location, "@MapTo target could not be resolved"), source)
                    return@mapNotNull null
                }
                if (target.isExpect) {
                    reporter.report(
                        Diagnostics.expectDeclarationUnsupported(
                            MLocation(target.qualifiedName?.asString() ?: target.simpleName.asString()),
                        ),
                        source,
                    )
                    return@mapNotNull null
                }
                val explicitName = annotation.stringArg("name").orEmpty()
                val useSerialNames = annotation.boolArg("useSerialNames")
                DeclaredMapTo(
                    target,
                    explicitName.ifEmpty { "to${target.simpleName.asString()}" },
                    levelPolicyOf(annotation, source),
                    useSerialNames,
                    annotation.boolArg("stdConverters"),
                    unmappedPolicyOf(annotation),
                    ignoredNamesOf(annotation),
                )
            }
            .toList()

    fun parseBiMapTo(source: KSClassDeclaration): DeclaredBiMapTo? {
        val annotation = source.annotations.firstOrNull { it.qualifiedName() == Ann.BIMAP_TO } ?: return null
        val target = annotation.classDeclArg("target") ?: return null
        val name = annotation.stringArg("name").orEmpty()
        val reverseName = annotation.stringArg("reverseName").orEmpty()
        return DeclaredBiMapTo(
            target = target,
            functionName = name.ifEmpty { "to${target.simpleName.asString()}" },
            reverseFunctionName = reverseName.ifEmpty { "to${source.simpleName.asString()}" },
            useSerialNames = annotation.boolArg("useSerialNames"),
        )
    }

    /**
     * Settings EFECTIVOS de una interfaz `@Mapper`, ya fundidos con su
     * profile `@MapperConfig` (si lo hay): componentModel resuelto (mapper > profile > NONE),
     * [onNull] como niveles ORDENADOS de la cascada (mapper, profile — el global lo agrega el
     * handler), useSerialNames aditivo e ignore unido.
     */
    data class MapperOptIns(
        val componentModel: Emission.Component,
        val onNull: List<NullPolicy>,
        val useSerialNames: Boolean,
        /** Aditivo (mapper OR profile), como serialNames. */
        val stdConverters: Boolean,
        /** Mapper > profile (null = INHERIT, cae al global). */
        val unmapped: UnmappedPolicy?,
        val ignore: Set<String>,
    )

    fun mapperOptIns(mapper: KSClassDeclaration): MapperOptIns {
        val annotation = mapper.annotations.first { it.qualifiedName() == Ann.MAPPER }

        // El profile referenciado por `config = X::class` (Unit = sin profile).
        val profile = annotation.classDeclArg("config")
            ?.takeIf { it.qualifiedName?.asString() != "kotlin.Unit" }
        val profileAnn = profile?.annotations?.firstOrNull { it.qualifiedName() == Ann.MAPPER_CONFIG }
        if (profile != null) {
            val profileQn = profile.qualifiedName?.asString() ?: profile.simpleName.asString()
            val location = MLocation(mapper.qualifiedName?.asString() ?: mapper.simpleName.asString())
            if (profileAnn == null) {
                reporter.report(
                    Diagnostics.invalidMapperConfig(location, profileQn, "is not annotated with @MapperConfig"),
                    mapper,
                )
            } else if (profile.getDeclaredFunctions().any { it.isAbstract }) {
                reporter.report(
                    Diagnostics.invalidMapperConfig(location, profileQn, "declares abstract methods"),
                    mapper,
                )
            }
        }
        // profileAnn no-null implica profile no-null (deriva de él): es directamente el válido.
        val validProfileAnn = profileAnn

        fun serialNames(ann: KSAnnotation?) = ann?.boolArg("useSerialNames") ?: false

        return MapperOptIns(
            componentModel = componentEntryOf(annotation)
                ?: validProfileAnn?.let { componentEntryOf(it) }
                ?: Emission.Component.NONE,
            onNull = listOfNotNull(
                levelPolicyOf(annotation, mapper),
                validProfileAnn?.let { levelPolicyOf(it, profile!!) },
            ),
            useSerialNames = serialNames(annotation) || serialNames(validProfileAnn),
            stdConverters = annotation.boolArg("stdConverters") ||
                (validProfileAnn?.boolArg("stdConverters") ?: false),
            unmapped = unmappedPolicyOf(annotation) ?: validProfileAnn?.let { unmappedPolicyOf(it) },
            ignore = ignoredNamesOf(annotation) +
                (validProfileAnn?.let { ignoredNamesOf(it) } ?: emptySet()),
        )
    }

    /** `componentModel` de una sede (`@Mapper`/`@MapperConfig`); null = INHERIT. */
    private fun componentEntryOf(annotation: KSAnnotation): Emission.Component? {
        return when (annotation.enumEntryName("componentModel")) {
            "SPRING" -> Emission.Component.SPRING
            "KOIN" -> Emission.Component.KOIN
            "NONE" -> Emission.Component.NONE
            else -> null // INHERIT
        }
    }

    /** El `unmapped` de una sede de nivel — null = INHERIT (cae en cascada). */
    private fun unmappedPolicyOf(annotation: KSAnnotation): UnmappedPolicy? =
        when (annotation.enumEntryName("unmapped")) {
            "IGNORE" -> UnmappedPolicy.IGNORE
            "WARN" -> UnmappedPolicy.WARN
            "ERROR" -> UnmappedPolicy.ERROR
            else -> null // INHERIT
        }

    /** La lista `ignore = [...]` de una sede de nivel (`@Mapper`/`@MapTo`). */
    private fun ignoredNamesOf(annotation: KSAnnotation): Set<String> = annotation.stringListArg("ignore")

    /**
     * El `onNull` de una sede de NIVEL (`@Mapper`/`@MapTo`) → [NullPolicy] de la cascada.
     * null = INHERIT (no aporta nivel). `LITERAL`/`UNSAFE` no son políticas de nivel → KMX041.
     */
    private fun levelPolicyOf(annotation: KSAnnotation, node: KSClassDeclaration): NullPolicy? {
        val entry = annotation.enumEntryName("onNull")
        MapFieldRules.levelPolicyFor(entry)?.let { return it }
        return when {
            MapFieldRules.isFieldOnlyPolicy(entry) -> {
                reporter.report(
                    Diagnostics.fieldOnlyPolicy(
                        MLocation(node.qualifiedName?.asString() ?: node.simpleName.asString()),
                        entry!!,
                    ),
                    node,
                )
                null
            }
            else -> null // INHERIT
        }
    }

    /** El `@Mapper(inheritFrom = X::class)` — null si es el default `Unit::class`. */
    fun inheritFromOf(mapper: KSClassDeclaration): KSClassDeclaration? {
        val annotation = mapper.annotations.first { it.qualifiedName() == Ann.MAPPER }
        val base = annotation.classDeclArg("inheritFrom") ?: return null
        return base.takeIf { it.qualifiedName?.asString() != "kotlin.Unit" }
    }

}
