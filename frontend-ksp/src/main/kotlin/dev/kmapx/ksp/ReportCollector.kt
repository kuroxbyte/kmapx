package dev.kmapx.ksp

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.symbol.FileLocation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFile
import dev.kmapx.codegen.ReportEmitter
import dev.kmapx.codegen.ReportEmitter.ReportField
import dev.kmapx.codegen.ReportEmitter.ReportMapping
import dev.kmapx.core.diagnostics.Severity
import dev.kmapx.core.model.MClass
import dev.kmapx.core.plan.Construction
import dev.kmapx.core.plan.Emission
import dev.kmapx.core.plan.MappingPlan
import dev.kmapx.core.plan.MapperRef
import dev.kmapx.core.plan.ValueSource

/**
 * Recolección y emisión del reporte de cobertura. Única responsabilidad: acumular la
 * trazabilidad (`Resolution` del plan) por campo y, en `finish()`, materializar JSON/HTML.
 *
 * Es una preocupación TRANSVERSAL y OPT-IN: extraerla del processor lo libera de mezclar la
 * generación de mappers con la del reporte. Cuando `enabled == false` no cuesta nada.
 */
internal class ReportCollector(
    private val formats: Set<String>,
    private val module: String,
    /** Config global efectiva (solo lo NO-default) para el header del reporte. */
    private val config: Map<String, String> = emptyMap(),
) {
    val enabled: Boolean get() = formats.isNotEmpty()

    private val entries = mutableListOf<ReportMapping>()
    private val sourceFiles = mutableListOf<KSFile>()

    fun record(entry: ReportMapping, decl: KSClassDeclaration) {
        if (!enabled) return
        entries += entry
        decl.containingFile?.let { sourceFiles += it }
    }

    fun locationOf(decl: KSClassDeclaration): Pair<String?, Int?> =
        (decl.location as? FileLocation)
            ?.let { it.filePath.substringAfterLast('/') to it.lineNumber } ?: (null to null)

    /** origin/shape/ref por campo, derivados del ValueSource (la trazabilidad del plan). */
    fun reportField(target: String, value: ValueSource, fallback: Boolean? = null): ReportField {
        fun of(from: String, origin: String, shape: String, ref: String? = null) =
            ReportField(target, from, origin, shape, ref, fallback)
        return when (value) {
            is ValueSource.Direct -> of(value.source.name, "IMPLICIT_SAFE", "direct")
            is ValueSource.ViaConverter ->
                of(value.source.name, value.origin.name, "converter", value.converter.qualifiedFunction)
            is ValueSource.ViaQualifiedConverter ->
                of(value.source.name, value.origin.name, "qualified-converter", value.converterObject)
            is ValueSource.ViaMapper -> of(
                value.source.name, value.origin.name, "mapper",
                (value.mapper as? MapperRef.GeneratedExtension)?.qualifiedFunction,
            )
            is ValueSource.UnwrapValueClass -> of(value.source.name, "IMPLICIT_SAFE", "unwrap")
            // Los implícitos de la lista cerrada, con su forma exacta.
            is ValueSource.NumericWidening ->
                of(value.source.name, "IMPLICIT_SAFE", "widening(${value.toFunction})")
            is ValueSource.BuiltinConversion ->
                of(value.source.name, "IMPLICIT_SAFE", "builtin", value.template)
            is ValueSource.WrapValueClass -> of(value.source.name, "IMPLICIT_SAFE", "wrap")
            is ValueSource.MapElements -> {
                val inner = reportField(target, value.element)
                of(value.source.name, inner.origin, "map-elements(${inner.shape})", inner.ref)
            }
            is ValueSource.MapEntries -> {
                val parts = listOfNotNull(
                    value.key?.let { "key=" + reportField(target, it).shape },
                    value.value?.let { "value=" + reportField(target, it).shape },
                )
                of(value.source.name, "IMPLICIT_SAFE", "map-entries(${parts.joinToString()})")
            }
            is ValueSource.NullStrategyOver -> {
                val inner = reportField(target, value.inner)
                of(inner.from, inner.origin, "strategy-over(${inner.shape})", inner.ref)
            }
            is ValueSource.NullFallbackToDefault -> of(value.source.name, "IMPLICIT_SAFE", "target-default")
            is ValueSource.NullFallbackToValue -> of(value.source.name, "IMPLICIT_SAFE", "fallback-value")
            is ValueSource.NullOrThrow -> of(value.source.name, "IMPLICIT_SAFE", "or-throw")
            is ValueSource.NullUnsafe -> of(value.source.name, "IMPLICIT_SAFE", "unsafe")
        }
    }

    /** Construye la entrada del reporte para un plan de extension/bidireccional (modo A). */
    fun entryOf(
        decl: KSClassDeclaration,
        plan: MappingPlan,
        mode: String,
        sourceModel: MClass,
    ): ReportMapping {
        val fields = when (val c = plan.construction) {
            is Construction.ConstructorCall ->
                c.arguments.map { reportField(it.paramName, it.value) } +
                    c.postAssignments.map { reportField(it.propertyName, it.value) }
            is Construction.FactoryCall ->
                c.arguments.map { reportField(it.paramName, it.value) } +
                    c.postAssignments.map { reportField(it.propertyName, it.value) }
            else -> emptyList()
        }
        val usedRoots = fields.map { it.from.substringBefore('.').substringBefore('?') }.toSet()
        val (file, line) = locationOf(decl)
        return ReportMapping(
            source = plan.source.qualifiedName,
            target = plan.target.qualifiedName,
            mode = mode,
            function = (plan.emission as? Emission.ExtensionFunction)?.name ?: "",
            file = file,
            line = line,
            fields = fields,
            unusedSourceProperties = sourceModel.properties.map { it.name }.filter { it !in usedRoots },
            warnings = plan.diagnostics.filter { it.severity == Severity.WARNING }.map { it.render() },
        )
    }

    /** Escribe JSON/HTML en `finish()`. `aggregating = true` SOLO con el reporte activado. */
    fun finish(codeGenerator: CodeGenerator) {
        if (!enabled || entries.isEmpty()) return
        val sorted = entries.sortedWith(compareBy({ it.source }, { it.function }))
        fun write(content: String, ext: String) {
            codeGenerator.createNewFile(
                dependencies = Dependencies(aggregating = true, *sourceFiles.distinct().toTypedArray()),
                packageName = "",
                fileName = "kmapx-report",
                extensionName = ext,
            ).bufferedWriter().use { it.write(content) }
        }
        if ("json" in formats) write(ReportEmitter.toJson(module, sorted, config), "json")
        if ("html" in formats) write(ReportEmitter.toHtml(module, sorted, config), "html")
    }
}
