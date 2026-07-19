package dev.kmapx.core.engine

import dev.kmapx.core.diagnostics.Diagnostics
import dev.kmapx.core.diagnostics.Suggestions
import dev.kmapx.core.model.MClass
import dev.kmapx.core.model.MPath
import dev.kmapx.core.model.MProperty

/**
 * Encuentra la PROPIEDAD FUENTE de un campo target. Única responsabilidad: el
 * matching (homónimo, `@MapFrom(from=...)`, ruta `a.b.c`, alias `@SerialName`) y sus diagnósticos
 * (KMX002/KMX011/KMX020). El motor le pregunta "¿de dónde sale este campo?"; qué hacer con el
 * valor es de [ValueResolver].
 */
internal class SourceMatcher {

    /**
     * Matching del campo: `@MapFrom(from = ...)` redirige (explícito gana sobre homónimo, sin
     * warning); nombres con `.` → ruta; renombre inexistente → KMX011 con "did you mean";
     * sin renombre y sin homónimo → KMX002.
     */
    fun find(source: MClass, paramName: String, mappedFrom: String?, ctx: Ctx): SourceLookup? {
        if (mappedFrom != null) {
            if ('.' in mappedFrom) return resolvePath(source, paramName, mappedFrom, ctx)
            val property = source.property(mappedFrom)
            if (property == null) {
                ctx.diagnostics += Diagnostics.renamedSourceMissing(
                    ctx.targetLocation, paramName, mappedFrom,
                    didYouMean = Suggestions.closest(mappedFrom, source.properties.map { it.name }),
                )
                return null
            }
            return SourceLookup(property)
        }
        val property = source.property(paramName)
            // El alias @SerialName SOLO se consulta si el nombre real no matcheó (determinista):
            ?: if (ctx.useSerialNames) source.properties.firstOrNull { it.serialName == paramName } else null
        if (property == null) {
            ctx.diagnostics += Diagnostics.missingSource(
                target = ctx.targetLocation,
                paramName = paramName,
                didYouMean = Suggestions.closest(paramName, source.properties.map { it.name }),
            )
            return null
        }
        return SourceLookup(property)
    }

    /**
     * Rutas `a.b.c`: sintaxis malformada → KMX020; segmento inexistente → KMX011 con
     * did-you-mean del TIPO de ese segmento; resuelta → propiedad sintética cuya "name" es la
     * EXPRESIÓN de acceso (`address?.city`, con `?.` desde el primer segmento nullable) y cuyo
     * tipo efectivo es nullable si cualquier segmento lo es. Tras esto aplican TODAS las reglas.
     */
    fun resolvePath(source: MClass, paramName: String, from: String, ctx: Ctx): SourceLookup? {
        val parts = from.split('.')
        if (parts.any { it.isBlank() }) {
            ctx.diagnostics += Diagnostics.malformedPath(ctx.targetLocation, paramName, from)
            return null
        }
        when (val path = ctx.resolvedPaths[from]) {
            is MPath.Missing -> {
                ctx.diagnostics += Diagnostics.renamedSourceMissing(
                    ctx.targetLocation, paramName, path.failedSegment,
                    didYouMean = Suggestions.closest(path.failedSegment, path.candidates),
                    on = path.ownerSimpleName,
                )
                return null
            }
            is MPath.Resolved -> {
                val expression = buildString {
                    var nullableSoFar = false
                    path.segments.forEachIndexed { i, seg ->
                        if (i > 0) append(if (nullableSoFar) "?." else ".")
                        append(seg.name)
                        if (seg.nullable) nullableSoFar = true
                    }
                }
                val anyIntermediateNullable = path.segments.dropLast(1).any { it.nullable }
                val effectiveType =
                    if (anyIntermediateNullable) path.finalType.asNullable() else path.finalType
                val nullableSegment = path.segments.dropLast(1).firstOrNull { it.nullable }?.name
                return SourceLookup(MProperty(expression, effectiveType), nullableSegment)
            }
            null -> {
                // Sin pre-resolución del frontend (p. ej. tests del motor a solas): la ruta no
                // puede validarse → se trata como inexistente desde el primer segmento.
                ctx.diagnostics += Diagnostics.renamedSourceMissing(
                    ctx.targetLocation, paramName, parts.first(),
                    didYouMean = Suggestions.closest(parts.first(), source.properties.map { it.name }),
                    on = source.type.simpleName,
                )
                return null
            }
        }
    }
}
