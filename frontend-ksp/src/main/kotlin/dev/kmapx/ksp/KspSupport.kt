package dev.kmapx.ksp

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFile
import dev.kmapx.codegen.GeneratedFile
import dev.kmapx.core.diagnostics.Diagnostic
import dev.kmapx.core.diagnostics.Severity

/** FQNs de las anotaciones públicas — un solo lugar (lo consultan handlers y descubrimiento). */
internal object Ann {
    const val MAP_TO = "dev.kmapx.annotations.embedded.MapTo"
    const val MAP_FACTORY = "dev.kmapx.annotations.MapFactory"
    const val MAPPER = "dev.kmapx.annotations.contract.Mapper"
    const val MAPPER_CONFIG = "dev.kmapx.annotations.contract.MapperConfig"
    const val INVERSE_OF = "dev.kmapx.annotations.contract.InverseOf"
    const val MAP_FIELD = "dev.kmapx.annotations.MapField"
    const val CONVERTER = "dev.kmapx.annotations.Converter"
    const val BIMAP_TO = "dev.kmapx.annotations.embedded.BiMapTo"
    const val SUPPRESS = "dev.kmapx.annotations.SuppressKmapx"
    const val MAP_CONSTRUCTOR = "dev.kmapx.annotations.MapConstructor"
    const val MAP_SUBTYPE = "dev.kmapx.annotations.MapSubtype"
    const val MAP_ENTRY = "dev.kmapx.annotations.MapEntry"
    const val SPRING_COMPONENT = "org.springframework.stereotype.Component"
}

/** `@SuppressKmapx("KMXnnn")` sobre el símbolo silencia ese WARNING (los errores nunca). */
internal fun KSAnnotated.suppressesKmapx(code: String): Boolean =
    annotations.any {
        it.qualifiedName() == Ann.SUPPRESS &&
            (it.arguments.firstOrNull { a -> a.name?.asString() == "codes" }?.value as? List<*>)
                ?.any { c -> c == code } == true
    }

/** El nombre calificado de una anotación KSP (usado en todo el frontend). */
internal fun KSAnnotation.qualifiedName(): String? =
    annotationType.resolve().declaration.qualifiedName?.asString()

// ── Lectura de argumentos de anotación: UN solo lugar (antes, 12 copias del mismo patrón). ──

internal fun KSAnnotation.argument(name: String): Any? =
    arguments.firstOrNull { it.name?.asString() == name }?.value

internal fun KSAnnotation.stringArg(name: String): String? = argument(name) as? String

internal fun KSAnnotation.boolArg(name: String): Boolean = argument(name) as? Boolean ?: false

/** Un argumento `KClass<*>` como declaración (null si no resuelve a una clase). */
internal fun KSAnnotation.classDeclArg(name: String): KSClassDeclaration? =
    (argument(name) as? com.google.devtools.ksp.symbol.KSType)?.declaration as? KSClassDeclaration

/** Un argumento `Array<String>` como set (listas `ignore`). */
internal fun KSAnnotation.stringListArg(name: String): Set<String> =
    (argument(name) as? List<*>)?.filterIsInstance<String>()?.toSet() ?: emptySet()

/** Nombre del entry de un argumento enum (KSP2 lo entrega como KSType; fallback a toString). */
internal fun KSAnnotation.enumEntryName(name: String): String? =
    when (val value = argument(name)) {
        is com.google.devtools.ksp.symbol.KSType -> value.declaration.simpleName.asString()
        else -> value?.toString()?.substringAfterLast('.')
    }

/**
 * Adaptador de reporte de diagnósticos: traduce un [Diagnostic] del core a un mensaje del
 * `KSPLogger` sobre el KSNode MÁS específico (parámetro > propiedad > clase), usando el índice del
 * translator; si no está, cae al símbolo de origen. Única responsabilidad: reportar.
 */
internal class DiagnosticReporter(
    private val logger: KSPLogger,
    private val translator: KspTranslator,
    /** Config global: sube los WARNING a error (CI duro). */
    private val warningsAsErrors: Boolean = false,
) {
    fun report(diagnostic: Diagnostic, fallback: KSAnnotated) {
        val location = diagnostic.location
        val symbol = translator.locationIndex[location.qualifiedClassName to location.member]
            ?: translator.locationIndex[location.qualifiedClassName to null]
            ?: fallback
        // @SuppressKmapx silencia el WARNING (en el campo o en la clase). Nunca un error.
        if (diagnostic.severity == Severity.WARNING) {
            val onField = symbol as? KSAnnotated
            val onClass = translator.locationIndex[location.qualifiedClassName to null] as? KSAnnotated
            if (onField?.suppressesKmapx(diagnostic.code.id) == true ||
                onClass?.suppressesKmapx(diagnostic.code.id) == true
            ) {
                return
            }
        }
        val asError = diagnostic.severity == Severity.ERROR || warningsAsErrors
        if (asError) logger.error("e: ${diagnostic.render()}", symbol)
        else logger.warn("w: ${diagnostic.render()}", symbol)
    }
}

/**
 * Adaptador de escritura: materializa un [GeneratedFile] con el `CodeGenerator`.
 * Única responsabilidad: el I/O de archivos generados y su declaración de dependencias KSP.
 */
internal class GeneratedOutput(private val codeGenerator: CodeGenerator) {

    /** Archivo aislado (no-agregante): depende solo del archivo de origen (incrementalidad). */
    fun write(file: GeneratedFile, origin: KSAnnotated?) {
        val containingFile = (origin as? KSClassDeclaration)?.containingFile
        create(file.packageName, file.fileName, null, listOfNotNull(containingFile), aggregating = false)
            .use { it.write(file.content) }
    }

    /** Archivo que agrega VARIAS declaraciones (p. ej. el módulo Koin por paquete). */
    fun writeMerged(file: GeneratedFile, deps: List<KSFile>) {
        create(file.packageName, file.fileName, null, deps, aggregating = false)
            .use { it.write(file.content) }
    }

    /** Reporte agregante del build entero: su `finish()` lo usa vía [ReportCollector]. */
    fun writeAggregating(packageName: String, fileName: String, ext: String, content: String, deps: List<KSFile>) {
        create(packageName, fileName, ext, deps, aggregating = true).use { it.write(content) }
    }

    private fun create(pkg: String, name: String, ext: String?, deps: List<KSFile>, aggregating: Boolean) =
        codeGenerator.createNewFile(
            dependencies = Dependencies(aggregating, *deps.distinct().toTypedArray()),
            packageName = pkg,
            fileName = name,
            extensionName = ext ?: "kt",
        ).bufferedWriter()
}

/** El diagnóstico es un ERROR (helper de legibilidad para los handlers). */
internal fun List<Diagnostic>.hasError(): Boolean = any { it.severity == Severity.ERROR }

/**
 * Auditoría de una aplicación de lista `ignore` de NIVEL — qué nombres matchearon
 * algún campo y qué candidatos existen (para el did-you-mean). La POLÍTICA de validación es del
 * llamador: `@MapTo` valida estricto contra SU target; `@Mapper` valida "existe en al menos un
 * target de la interfaz" (los targets de sus métodos son heterogéneos — el caso auditoría no
 * puede exigir el campo en todos); `@InverseOf` no re-valida (sus métodos normales ya lo hacen).
 */
internal class IgnoreAudit {
    val matched: MutableSet<String> = mutableSetOf()
    val candidates: MutableSet<String> = mutableSetOf()

    /** Reporta KMX011 (did-you-mean sobre la unión de candidatos) por cada nombre sin match. */
    fun reportUnmatched(
        names: Set<String>,
        location: dev.kmapx.core.diagnostics.MLocation,
        targetClass: String,
        node: com.google.devtools.ksp.symbol.KSAnnotated,
        reporter: DiagnosticReporter,
    ) {
        (names - matched).forEach { missing ->
            reporter.report(
                dev.kmapx.core.diagnostics.Diagnostics.methodTargetMissing(
                    location, missing, targetClass,
                    dev.kmapx.core.diagnostics.Suggestions.closest(missing, candidates),
                ),
                node,
            )
        }
    }
}

/**
 * Marca como ignorados los campos de la lista de NIVEL (`@Mapper`/`@MapTo`),
 * uniéndolos con los `@MapField(ignore = true)` por campo que ya trae el modelo. NO valida:
 * registra matches/candidatos en [audit] y el llamador decide cuándo reportar KMX011.
 */
internal fun dev.kmapx.core.model.MClass.withIgnored(
    names: Set<String>,
    audit: IgnoreAudit? = null,
): dev.kmapx.core.model.MClass {
    if (names.isEmpty()) return this
    val fieldNames = fieldNames()
    audit?.let {
        it.candidates += fieldNames
        it.matched += names.intersect(fieldNames)
    }
    return copy(
        constructors = constructors.map { c ->
            c.copy(params = c.params.map { p -> if (p.name in names) p.copy(ignored = true) else p })
        },
        properties = properties.map { p -> if (p.name in names) p.copy(ignored = true) else p },
    )
}
