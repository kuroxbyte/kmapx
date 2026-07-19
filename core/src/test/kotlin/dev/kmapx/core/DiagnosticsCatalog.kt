package dev.kmapx.core

import dev.kmapx.core.diagnostics.Diagnostic
import dev.kmapx.core.diagnostics.DiagnosticCode
import dev.kmapx.core.diagnostics.Diagnostics
import dev.kmapx.core.diagnostics.MLocation
import dev.kmapx.core.diagnostics.Severity
import java.io.File

/**
 * Generador del catálogo de diagnósticos (`docs/referencia/diagnosticos.md`) — la filosofía de
 * la casa aplicada a los errores: documentación que NO puede mentir. Las descripciones salen del
 * KDoc del enum [DiagnosticCode] (parseado del fuente) y los ejemplos de mensaje/fix se RENDERIZAN
 * con las factories reales — las mismas muestras del contrato ([DiagnosticsContractTest]).
 * [DiagnosticsCatalogTest] falla si el archivo publicado difiere del generado (patrón snapshot:
 * regenerar con `-Dkmapx.updateDocs=true`).
 */
object DiagnosticsCatalog {

    private val loc = MLocation("com.example.PersonDto")

    /** Una muestra REPRESENTATIVA por código, con argumentos que se leen como un caso real. */
    val samples: Map<DiagnosticCode, Diagnostic> = listOf(
        Diagnostics.internalError(loc, "unexpected symbol kind"),
        Diagnostics.missingSource(loc, "age", listOf("ageYears")),
        Diagnostics.nullabilityViolation(loc, "nickname", "Person", "nickname"),
        Diagnostics.incompatibleTypes(loc, "createdAt", "java.time.Instant", "kotlin.String"),
        Diagnostics.noResolvableConstructor(loc),
        Diagnostics.ambiguousConstruction(loc, listOf("@MapConstructor(name)", "@MapFactory of")),
        Diagnostics.noNestedMapping(loc, "address", "Address", "AddressDto"),
        Diagnostics.mappingCycle(loc, listOf("Person", "Address", "Person")),
        Diagnostics.ambiguousConverters(loc, "createdAt", "java.time.Instant", "kotlin.String", listOf("toIso", "toEpoch")),
        Diagnostics.subtypeWithoutCounterpart(MLocation("com.example.Event.Refunded"), "com.example.EventDto"),
        Diagnostics.renamedSourceMissing(loc, "name", "firstnme", listOf("firstname")),
        Diagnostics.patchTargetNotDataClass(loc),
        Diagnostics.ambiguousMapperName(loc, "toDto", listOf("a.Dto", "b.Dto")),
        Diagnostics.afterFunctionBadSignature(loc, "afterToDto", "com.example.Person", "com.example.PersonDto"),
        Diagnostics.unsupportedMapperShape(loc, "generic @Mapper interfaces are not supported in v1"),
        Diagnostics.multipleStrategies(loc, "nickname"),
        Diagnostics.invalidDefaultLiteral(loc, "age", "abc", "kotlin.Int"),
        Diagnostics.deadStrategy(loc, "nickname"),
        Diagnostics.invalidConverterSignature(MLocation("com.example.toIso"), "must take exactly one parameter"),
        Diagnostics.malformedPath(loc, "city", "address..city"),
        Diagnostics.targetDefaultFilled(loc, "nickname"),
        Diagnostics.tooManyOmissibleDefaults(loc, listOf("nickname", "bio", "tag"), limit = 2),
        Diagnostics.targetSubtypeUnmatched(MLocation("com.example.EventDto.Cancelled"), "com.example.Event"),
        Diagnostics.deepSealedNesting(MLocation("com.example.Event.Inner")),
        Diagnostics.expectDeclarationUnsupported(MLocation("com.example.PlatformClock")),
        Diagnostics.enumEntryWithoutCounterpart(MLocation("com.example.Color.CRIMSON"), "com.example.ColorDto", listOf("CARMINE")),
        Diagnostics.converterTypeMismatch(loc, "startDate", "com.example.ShortDate", "LocalDate -> String", "Int -> String"),
        Diagnostics.notInvertible(loc, "createdAt", "missing converter kotlin.String -> java.time.Instant for the reverse direction", "register the inverse @Converter"),
        Diagnostics.notAConverter(loc, "startDate", "com.example.NotAConverter"),
        Diagnostics.frameworkMissing(MLocation("com.example.CustomerMapper"), "SPRING", "org.springframework:spring-context"),
        Diagnostics.unnecessaryConverter(loc, "name", "com.example.Trim"),
        Diagnostics.duplicateFieldConfig(loc, "name"),
        Diagnostics.orEmptyNotCollection(loc, "meta", "com.example.Meta"),
        Diagnostics.injectedConverterInModeA(loc, "customerName", "com.example.CustomerName"),
        Diagnostics.injectedConverterNotComponent(MLocation("com.example.CustomerMapper"), "com.example.RiskLabeler"),
        Diagnostics.mapFieldBadAddressing(loc.copy(member = "nickname"), methodSite = false),
        Diagnostics.mapFieldDuplicate(loc, "nickname"),
        Diagnostics.literalRequiresDefault(loc, "nickname"),
        Diagnostics.defaultIgnored(loc, "nickname"),
        Diagnostics.targetDefaultUnavailable(loc, "nickname"),
        Diagnostics.fieldOnlyPolicy(MLocation("com.example.CustomerMapper"), "LITERAL"),
        Diagnostics.cannotIgnore(loc, "createdAt"),
        Diagnostics.ignoreConflictsWithAspects(loc, "createdAt"),
        Diagnostics.invalidMapperConfig(MLocation("com.example.CustomerMapper"), "com.example.NotAProfile", "is not annotated with @MapperConfig"),
        Diagnostics.invalidInverse(MLocation("com.example.CustomerMapper", "fromDto"), "no method with the inverse signature found (auto-detection)"),
        Diagnostics.collectionMethodUnresolved(MLocation("com.example.OrderMapper", "toDtos"), "com.example.Order", "com.example.OrderDto"),
        Diagnostics.enumFallbackMissing(MLocation("com.example.LegacyStatus"), "UNKNWN", "com.example.Status", listOf("UNKNOWN")),
    ).associateBy { it.code }

    /** KDoc de cada entry del enum, parseado del FUENTE de Diagnostics.kt (queda en sync solo). */
    private fun descriptions(): Map<String, String> {
        val source = File("src/main/kotlin/dev/kmapx/core/diagnostics/Diagnostics.kt").readText()
        // El cuerpo del KDoc no puede contener `*/`: así el match es SIEMPRE el KDoc inmediato
        // del entry (sin esto, un lazy `.*?` se tragaría KDocs anteriores del archivo).
        val regex = Regex("""/\*\*((?:(?!\*/).)*)\*/\s*(KMX\d{3})\("""", RegexOption.DOT_MATCHES_ALL)
        return regex.findAll(source).associate { m ->
            m.groupValues[2] to m.groupValues[1].replace(Regex("""\s*\*\s*"""), " ").trim()
        }
    }

    fun render(): String = buildString {
        appendLine("# Catálogo de diagnósticos (KMX001–KMX${"%03d".format(DiagnosticCode.entries.size)})")
        appendLine()
        appendLine("> **GENERADO** desde `core/.../diagnostics/Diagnostics.kt` por `DiagnosticsCatalog` —")
        appendLine("> no editar a mano (el test `DiagnosticsCatalogTest` falla si difiere; regenerar con")
        appendLine("> `./gradlew :core:test -Dkmapx.updateDocs=true`). Las descripciones salen del KDoc del")
        appendLine("> catálogo y los ejemplos se RENDERIZAN con las factories reales.")
        appendLine()
        appendLine("Formato canónico de todo diagnóstico: `[KMXnnn] <ubicación> <problema>. Fix: <acción>.`")
        appendLine("Los WARNING se silencian por código con `@SuppressKmapx(\"KMXnnn\")`; los errores, nunca.")
        appendLine()
        val docs = descriptions()
        for (code in DiagnosticCode.entries) {
            val sample = samples.getValue(code)
            val badge = if (sample.severity == Severity.WARNING) "WARNING" else "ERROR"
            appendLine("## ${code.id} — $badge")
            appendLine()
            appendLine(docs[code.id] ?: "(sin descripción en el catálogo)")
            appendLine()
            appendLine("```")
            appendLine(sample.render())
            appendLine("```")
            appendLine()
        }
    }
}
