package dev.kmapx.ksp.embedded

import dev.kmapx.ksp.Ann
import dev.kmapx.ksp.DeclarationHandler
import dev.kmapx.ksp.FrontendContext
import dev.kmapx.ksp.PlanReferences
import dev.kmapx.ksp.ResolvedSource
import dev.kmapx.ksp.hasError
import dev.kmapx.ksp.qualifiedName
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.Modifier
import dev.kmapx.core.diagnostics.Diagnostics
import dev.kmapx.core.diagnostics.MLocation
import dev.kmapx.core.plan.Emission

/** Modo bidireccional `@BiMapTo`: ambas extensions desde una declaración, con validación. */
internal class BiMapToHandler : DeclarationHandler {
    override val annotation = Ann.BIMAP_TO

    override fun handle(decl: KSClassDeclaration, ctx: FrontendContext): ResolvedSource? {
        val source = decl
        val bi = ctx.reader.parseBiMapTo(source) ?: return null
        val aQn = source.qualifiedName?.asString() ?: return null
        val bQn = bi.target.qualifiedName?.asString() ?: return null
        val location = MLocation(aQn)

        if (source.isExpect || bi.target.isExpect) {
            ctx.reporter.report(
                Diagnostics.expectDeclarationUnsupported(MLocation(if (source.isExpect) aQn else bQn)),
                source,
            )
            return null
        }
        // KMX013: @BiMapTo + @MapTo del mismo par (en cualquier dirección) colisionan en nombre.
        if (ctx.index.mapToPairs.containsKey(aQn to bQn) || ctx.index.mapToPairs.containsKey(bQn to aQn)) {
            ctx.reporter.report(
                Diagnostics.ambiguousMapperName(
                    location, bi.functionName,
                    listOf("$aQn (@BiMapTo)", "declaración @MapTo existente del mismo par"),
                ),
                source,
            )
            return null
        }

        val isInternal = Modifier.INTERNAL in source.modifiers
        val resolution = ctx.engine.resolveBidirectional(
            a = ctx.translator.translate(source),
            b = ctx.translator.translate(bi.target, ctx.index.topLevelFactories[bQn].orEmpty()),
            forwardEmission = Emission.ExtensionFunction(bi.functionName, isInternal = isInternal),
            reverseEmission = Emission.ExtensionFunction(bi.reverseFunctionName, isInternal = isInternal),
            declaredMappings = ctx.index.declaredExtensions,
            converters = ctx.index.converters,
            useSerialNames = bi.useSerialNames || ctx.config.useSerialNames,
            // `@BiMapTo` no tiene nivel propio en v1 (la invertibilidad de las políticas
            // condicionales se evalúa en la fase 2 de la inversión); aplica solo el nivel global.
            nullPolicies = listOfNotNull(ctx.config.onNull),
            // La tabla estándar es SIMÉTRICA — apta para bidireccional (solo nivel global).
            stdConverters = ctx.config.stdConverters,
            unmapped = ctx.config.unmapped,
        )
        resolution.diagnostics.forEach { ctx.reporter.report(it, source) }
        if (!resolution.valid) return null

        val aModel = ctx.translator.translate(source)
        val bModel = ctx.translator.translate(bi.target)
        ctx.report.record(ctx.report.entryOf(source, resolution.forward, "bidirectional", aModel), source)
        ctx.report.record(ctx.report.entryOf(source, resolution.reverse, "bidirectional", bModel), source)
        return ResolvedSource(
            source,
            ctx.emitter.emitBidirectional(resolution.forward, resolution.reverse),
            PlanReferences.of(listOf(resolution.forward, resolution.reverse)),
        )
    }
}
