package dev.kmapx.ksp

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSClassDeclaration
import dev.kmapx.codegen.GeneratedFile
import dev.kmapx.codegen.PlanEmitter
import dev.kmapx.core.engine.MappingEngine
import dev.kmapx.core.engine.NullPolicy
import dev.kmapx.core.engine.UnmappedPolicy
import dev.kmapx.ksp.contract.KoinModuleWriter

/**
 * Config global del módulo (bloque `kmapx { }` → opciones KSP). Defaults que aplican a TODO el
 * módulo; una anotación explícita ENCIENDE (opción A: los opt-in son aditivos, no se apagan).
 */
internal data class GlobalConfig(
    val useSerialNames: Boolean = false,
    /** El nivel GLOBAL de la cascada `onNull` (reemplaza nullPolicy/emptyCollections/useTargetDefaults). */
    val onNull: NullPolicy = NullPolicy.STRICT,
    val warningsAsErrors: Boolean = false,
    /** Conversiones estándar opt-in para TODO el módulo. */
    val stdConverters: Boolean = false,
    /** El nivel GLOBAL de la política `unmapped` (default WARN, el histórico). */
    val unmapped: UnmappedPolicy = UnmappedPolicy.WARN,
) {
    /** Solo lo NO-default, para mostrarlo en el header del reporte de cobertura (transparencia). */
    fun effective(): Map<String, String> = buildMap {
        if (useSerialNames) put("useSerialNames", "true")
        if (warningsAsErrors) put("warningsAsErrors", "true")
        if (onNull != NullPolicy.STRICT) put("onNull", onNull.name.lowercase())
        if (stdConverters) put("stdConverters", "true")
        if (unmapped != UnmappedPolicy.WARN) put("unmapped", unmapped.name.lowercase())
    }

    companion object {
        fun from(options: Map<String, String>): GlobalConfig = GlobalConfig(
            useSerialNames = options["kmapx.useSerialNames"].toBoolean(),
            // LITERAL/UNSAFE no existen como política global: valor desconocido = STRICT.
            onNull = when (options["kmapx.onNull"]?.lowercase()?.replace("_", "")) {
                "throw", "orthrow" -> NullPolicy.OR_THROW
                "typedefault" -> NullPolicy.TYPE_DEFAULT
                "targetdefault" -> NullPolicy.TARGET_DEFAULT
                else -> NullPolicy.STRICT
            },
            warningsAsErrors = options["kmapx.warningsAsErrors"].toBoolean(),
            stdConverters = options["kmapx.stdConverters"].toBoolean(),
            unmapped = when (options["kmapx.unmapped"]?.lowercase()) {
                "ignore" -> UnmappedPolicy.IGNORE
                "error" -> UnmappedPolicy.ERROR
                else -> UnmappedPolicy.WARN
            },
        )
    }
}

/**
 * Dependencias compartidas de UNA ronda de procesamiento. El processor la arma y la pasa a cada
 * [DeclarationHandler]; así un handler declara qué necesita sin conocer al processor (DIP). Reúne
 * los servicios (traducción, motor, emisión, salida, diagnóstico, rutas, reporte, Koin) y el
 * índice de la ronda.
 */
internal class FrontendContext(
    val resolver: Resolver,
    val translator: KspTranslator,
    val engine: MappingEngine,
    val emitter: PlanEmitter,
    val reader: AnnotationReader,
    val paths: PathNavigator,
    val reporter: DiagnosticReporter,
    val output: GeneratedOutput,
    val report: ReportCollector,
    val koin: KoinModuleWriter,
    val index: DeclarationIndex,
    val config: GlobalConfig,
    val crossModule: CrossModuleResolver,
)

/**
 * Un modo de mapeo (una anotación). Registrar un modo nuevo = una implementación nueva en el
 * registro del processor, SIN tocar `process()` (OCP). Cada handler es una responsabilidad única.
 *
 * Devuelve un [ResolvedSource] cuando la escritura se DIFIERE al chequeo de ciclos (modo A y
 * bidireccional); `null` cuando ya escribió (modo interfaz/patch) o cuando el mapeo fue inválido.
 */
internal interface DeclarationHandler {
    /** FQN de la anotación que maneja (clave del registro). */
    val annotation: String

    fun handle(decl: KSClassDeclaration, ctx: FrontendContext): ResolvedSource?
}

/** Resultado de resolver una clase cuya escritura se difiere hasta el chequeo de ciclos. */
internal data class ResolvedSource(
    val decl: KSClassDeclaration,
    val file: GeneratedFile,
    /** FQNs de extensions generadas que este mapeo REFERENCIA (aristas del grafo). */
    val referencedFunctions: Set<String>,
)
