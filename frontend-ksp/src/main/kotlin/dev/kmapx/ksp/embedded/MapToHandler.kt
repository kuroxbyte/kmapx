package dev.kmapx.ksp.embedded

import dev.kmapx.ksp.Ann
import dev.kmapx.ksp.DeclarationHandler
import dev.kmapx.ksp.FrontendContext
import dev.kmapx.ksp.IgnoreAudit
import dev.kmapx.ksp.PlanReferences
import dev.kmapx.ksp.ResolvedSource
import dev.kmapx.ksp.qualifiedName
import dev.kmapx.ksp.withIgnored
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.Modifier
import dev.kmapx.core.diagnostics.Diagnostics
import dev.kmapx.core.diagnostics.MLocation
import dev.kmapx.core.plan.Emission
import dev.kmapx.core.plan.MappingPlan

/** Modo A: `@MapTo` → extension functions `Source.toTarget()`. */
internal class MapToHandler : DeclarationHandler {
    override val annotation = Ann.MAP_TO

    override fun handle(decl: KSClassDeclaration, ctx: FrontendContext): ResolvedSource? {
        val source = decl
        // Expect/actual mapping es no-goal v1 — la declaración anotada o el target expect
        // producen KMX025 (el usuario anota la actual por target, o mapea una clase común).
        if (source.isExpect) {
            ctx.reporter.report(
                Diagnostics.expectDeclarationUnsupported(
                    MLocation(source.qualifiedName?.asString() ?: source.simpleName.asString()),
                ),
                source,
            )
            return null
        }

        val sourceModel = ctx.translator.translate(source)
        val sourceLocation = MLocation(sourceModel.type.qualifiedName)
        val isInternal = Modifier.INTERNAL in source.modifiers

        val declared = ctx.reader.parseMapTo(source)
        if (declared.isEmpty()) return null

        // Colisión de nombres (dos targets con el mismo simpleName sin name explícito) → KMX013:
        val collisions = declared.groupBy { it.functionName }.filterValues { it.size > 1 }
        if (collisions.isNotEmpty()) {
            collisions.forEach { (functionName, group) ->
                ctx.reporter.report(
                    Diagnostics.ambiguousMapperName(
                        source = sourceLocation,
                        functionName = functionName,
                        targets = group.map { it.target.qualifiedName?.asString() ?: it.target.simpleName.asString() },
                    ),
                    source,
                )
            }
            return null
        }

        val plans = mutableListOf<MappingPlan>()
        var anyInvalid = false
        for (decl in declared) {
            val targetQualifiedName = decl.target.qualifiedName?.asString()
            // La lista `ignore` del @MapTo se une con los ignore por campo; un @MapTo
            // apunta a UN target → validación estricta inmediata (KMX011).
            val ignoreAudit = IgnoreAudit()
            val targetModel = ctx.translator.translate(
                decl.target,
                topLevelFactories = ctx.index.topLevelFactories[targetQualifiedName].orEmpty(),
            )
                .withIgnored(decl.ignore, ignoreAudit)
            ignoreAudit.reportUnmatched(
                decl.ignore, sourceLocation, targetQualifiedName ?: "<unknown>", source, ctx.reporter,
            )
            val plan = ctx.engine.resolve(
                source = sourceModel,
                target = targetModel,
                emission = Emission.ExtensionFunction(decl.functionName, isInternal = isInternal),
                declaredMappings = ctx.index.declaredExtensions,
                converters = ctx.index.converters,
                resolvedPaths = ctx.paths.resolve(source, targetModel),
                useSerialNames = decl.useSerialNames || ctx.config.useSerialNames,
                // Cascada de niveles — mapeo (@MapTo.onNull) primero, global después.
                nullPolicies = listOfNotNull(decl.onNull, ctx.config.onNull),
                stdConverters = decl.stdConverters || ctx.config.stdConverters,
                // Cascada mapeo > global (el primer no-INHERIT gana).
                unmapped = decl.unmapped ?: ctx.config.unmapped,
                // Par anidado de OTRO módulo: descubrir su extensión `@GeneratedMapping` en el classpath.
                crossModuleMappings = ctx.crossModule::lookup,
            )
            // Los diagnósticos son datos del core; aquí solo se reportan sobre el símbolo de origen
            // (errores y warnings — un plan con solo warnings sigue siendo emitible, KMX018).
            plan.diagnostics.forEach { ctx.reporter.report(it, source) }
            if (!plan.valid) {
                anyInvalid = true
            } else {
                plans += plan
            }
        }
        if (anyInvalid || plans.isEmpty()) return null

        plans.forEach { ctx.report.record(ctx.report.entryOf(source, it, "extension", sourceModel), source) }
        return ResolvedSource(source, ctx.emitter.emit(plans), PlanReferences.of(plans))
    }
}
