package dev.kmapx.ksp

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.validate
import dev.kmapx.codegen.PlanEmitter
import dev.kmapx.core.diagnostics.Diagnostics
import dev.kmapx.core.diagnostics.MLocation
import dev.kmapx.core.engine.MappingEngine
import dev.kmapx.core.engine.MappingGraph
import dev.kmapx.ksp.contract.KoinModuleWriter
import dev.kmapx.ksp.contract.MapperHandler
import dev.kmapx.ksp.embedded.BiMapToHandler
import dev.kmapx.ksp.embedded.MapToHandler

/**
 * Frontend KSP — el COORDINADOR. Su única responsabilidad es el ciclo de vida de KSP: descubrir
 * símbolos, DESPACHAR cada declaración al [DeclarationHandler] de su anotación (registro → OCP:
 * un modo nuevo es un handler nuevo, sin tocar `process()`), correr el chequeo de ciclos y
 * descargar los agregadores (Koin, reporte). Todo el "cómo" vive en colaboradores.
 */
public class KmapxProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
    private val options: Map<String, String> = emptyMap(),
) : SymbolProcessor {

    private val config = GlobalConfig.from(options)
    private val translator = KspTranslator()
    private val engine = MappingEngine()
    private val emitter = PlanEmitter()
    private val reporter = DiagnosticReporter(logger, translator, config.warningsAsErrors)
    private val output = GeneratedOutput(codeGenerator)
    private val paths = PathNavigator(translator)
    private val reader = AnnotationReader(reporter)

    init {
        // El translator reporta KMX036–KMX039 al leer @MapField; el ciclo se cierra aquí.
        translator.reporter = reporter
    }

    // Reporte de cobertura (opt-in `kmapx.report=json[,html]`).
    private val report = ReportCollector(
        formats = options["kmapx.report"]?.split(',')?.map { it.trim() }?.toSet() ?: emptySet(),
        module = options["kmapx.module"] ?: "",
        config = config.effective(),
    )

    // Registro de modos (OCP): agregar un modo = una entrada aquí + su handler.
    // El PATCH ya no es un modo — es una FORMA de método dentro de @Mapper.
    private val handlers: Map<String, DeclarationHandler> =
        listOf(MapToHandler(), MapperHandler(), BiMapToHandler())
            .associateBy { it.annotation }

    override fun finish() = report.finish(codeGenerator)

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val annotated = handlers.keys.flatMap { resolver.getSymbolsWithAnnotation(it).toList() }
        val (valid, deferred) = annotated.distinct().partition { it.validate() }
        val sources = valid.filterIsInstance<KSClassDeclaration>()

        // Una sola pasada de descubrimiento e indexación (converters, extensions, factories).
        val index = DeclarationIndex.build(resolver, sources, reader, reporter)
        // Koin se agrega y emite POR RONDA (como el mapa local original): el reporte de cobertura sí es
        // de field porque agrega todas las rondas y se escribe en finish().
        val koin = KoinModuleWriter(emitter, output)
        val ctx = FrontendContext(
            resolver, translator, engine, emitter, reader, paths, reporter, output, report, koin, index, config,
            CrossModuleResolver(resolver),
        )

        // Dos fases — resolver TODO (los handlers difieren la escritura de modo A/bi vía
        // ResolvedSource), detectar ciclos sobre el grafo de referencias, y recién entonces escribir.
        val resolved = mutableListOf<ResolvedSource>()
        sources.forEach { decl ->
            decl.annotations.mapNotNull { handlers[it.qualifiedName()] }.distinct()
                .forEach { handler -> handler.handle(decl, ctx)?.let { resolved += it } }
        }

        val edges = resolved.associate { r ->
            val qn = r.decl.qualifiedName?.asString().orEmpty()
            qn to r.referencedFunctions.mapNotNull { index.functionToSource[it] }.distinct()
        }
        val cycle = MappingGraph.findCycle(edges)
        if (cycle != null) {
            // Una vez por ciclo, sobre la declaración que lo CIERRA.
            val closing = cycle[cycle.size - 2]
            val symbol = resolved.firstOrNull { it.decl.qualifiedName?.asString() == closing }?.decl
            reporter.report(
                Diagnostics.mappingCycle(MLocation(closing), cycle.map { it.substringAfterLast('.') }),
                symbol ?: resolved.first().decl,
            )
            val inCycle = cycle.toSet()
            resolved.filter { it.decl.qualifiedName?.asString() !in inCycle }
                .forEach { output.write(it.file, it.decl) }
        } else {
            resolved.forEach { output.write(it.file, it.decl) }
        }

        koin.flush()
        return deferred
    }
}

public class KmapxProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor =
        KmapxProcessor(environment.codeGenerator, environment.logger, environment.options)
}
