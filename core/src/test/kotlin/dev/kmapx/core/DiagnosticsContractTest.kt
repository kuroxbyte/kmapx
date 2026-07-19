package dev.kmapx.core

import dev.kmapx.core.diagnostics.DiagnosticCode
import dev.kmapx.core.diagnostics.Diagnostics
import dev.kmapx.core.diagnostics.MLocation
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Test de contrato del formato canónico:
 * `[KMXnnn] <ubicación> <problema>. Fix: <acción>.`
 * Cada factory del catálogo produce un render que lo cumple. El catálogo COMPLETO
 * tiene factory: agregar un código sin factory hace fallar este test (por diseño).
 */
class DiagnosticsContractTest {

    private val loc = MLocation("fx.Dto")

    private val samples = listOf(
        Diagnostics.internalError(loc, "boom"),
        Diagnostics.missingSource(loc, "x", listOf("y", "z")),
        Diagnostics.nullabilityViolation(loc, "x", "Src", "x"),
        Diagnostics.incompatibleTypes(loc, "x", "kotlin.String", "kotlin.Int"),
        Diagnostics.noResolvableConstructor(loc),
        Diagnostics.ambiguousConstruction(loc, listOf("a", "b")),
        Diagnostics.noNestedMapping(loc, "x", "fx.A", "fx.B"),
        Diagnostics.mappingCycle(loc, listOf("A", "B", "A")),
        Diagnostics.ambiguousConverters(loc, "x", "java.time.Instant", "kotlin.String", listOf("a", "b")),
        Diagnostics.invalidConverterSignature(loc, "must take exactly one parameter"),
        Diagnostics.renamedSourceMissing(loc, "x", "firstnme", listOf("firstname")),
        Diagnostics.malformedPath(loc, "x", "address..city"),
        Diagnostics.targetDefaultFilled(loc, "tags"),
        Diagnostics.tooManyOmissibleDefaults(loc, listOf("a", "b", "c"), limit = 2),
        Diagnostics.subtypeWithoutCounterpart(MLocation("fx.Event.Refunded"), "fx.EventDto"),
        Diagnostics.targetSubtypeUnmatched(MLocation("fx.EventDto.Cancelled"), "fx.Event"),
        Diagnostics.deepSealedNesting(MLocation("fx.Event.Inner")),
        Diagnostics.afterApplyBadSignature(loc, "fx.Customer", "fx.CustomerPatch"),
        Diagnostics.expectDeclarationUnsupported(MLocation("fx.PlatformThing")),
        Diagnostics.enumEntryWithoutCounterpart(MLocation("fx.Color.BLUE"), "fx.ColorDto", listOf("BLUEISH")),
        Diagnostics.notInvertible(loc, "createdAt", "missing converter kotlin.String -> java.time.Instant for the reverse direction", "register the inverse @Converter"),
        Diagnostics.converterTypeMismatch(loc, "startDate", "fx.ShortDate", "LocalDate -> String", "Int -> String"),
        Diagnostics.notAConverter(loc, "startDate", "fx.NotAConverter"),
        Diagnostics.unnecessaryConverter(loc, "startDate", "fx.Trim"),
        Diagnostics.duplicateFieldConfig(loc, "name"),
        Diagnostics.orEmptyNotCollection(loc, "tags", "kotlin.String"),
        Diagnostics.injectedConverterInModeA(loc, "customerName", "fx.CustomerName"),
        Diagnostics.injectedConverterNotComponent(loc, "fx.CustomerName"),
        Diagnostics.frameworkMissing(loc, "SPRING", "org.springframework:spring-context"),
        Diagnostics.patchTargetNotDataClass(loc),
        Diagnostics.ambiguousMapperName(loc, "toX", listOf("a.X", "b.X")),
        Diagnostics.afterFunctionBadSignature(loc, "afterToDto", "fx.Src", "fx.Dto"),
        Diagnostics.unsupportedMapperShape(loc, "generic interfaces are not supported"),
        Diagnostics.multipleStrategies(loc, "x"),
        Diagnostics.invalidDefaultLiteral(loc, "x", "abc", "kotlin.Int"),
        Diagnostics.unsupportedDefaultType(loc, "x", "fx.Address"),
        Diagnostics.deadStrategy(loc, "x"),
        Diagnostics.mapFieldBadAddressing(loc.copy(member = "x"), methodSite = false),
        Diagnostics.mapFieldDuplicate(loc, "x"),
        Diagnostics.literalRequiresDefault(loc, "x"),
        Diagnostics.defaultIgnored(loc, "x"),
        Diagnostics.targetDefaultUnavailable(loc, "x"),
        Diagnostics.fieldOnlyPolicy(loc, "LITERAL"),
        Diagnostics.cannotIgnore(loc, "x"),
        Diagnostics.ignoreConflictsWithAspects(loc, "x"),
        Diagnostics.invalidMapperConfig(loc, "fx.NotAProfile", "is not annotated with @MapperConfig"),
        Diagnostics.invalidInverse(loc.copy(member = "fromDto"), "no method with the inverse signature found (auto-detection)"),
        Diagnostics.collectionMethodUnresolved(loc.copy(member = "toDtos"), "fx.Order", "fx.OrderDto"),
        Diagnostics.enumFallbackMissing(MLocation("fx.Legacy"), "UNKNWN", "fx.Status", listOf("UNKNOWN")),
    )

    @Test
    fun `cada factory cumple el formato canonico`() {
        val canonical = Regex("""^\[KMX\d{3}] \S+ .+ Fix: .+\.$""")
        samples.forEach { diagnostic ->
            assertTrue(
                canonical.matches(diagnostic.render()),
                "no cumple el formato canónico: ${diagnostic.render()}",
            )
        }
    }

    @Test
    fun `el catalogo completo esta cubierto - todos los codigos tienen factory`() {
        val covered = samples.map { it.code }.toSet()
        assertEquals(DiagnosticCode.entries.toSet(), covered)
    }
}
