package dev.kmapx.core.engine

import dev.kmapx.core.diagnostics.Diagnostic
import dev.kmapx.core.diagnostics.DiagnosticCode
import dev.kmapx.core.diagnostics.Diagnostics
import dev.kmapx.core.diagnostics.MLocation
import dev.kmapx.core.diagnostics.Suggestions
import dev.kmapx.core.model.MClass
import dev.kmapx.core.model.MConstructorParam
import dev.kmapx.core.model.MProperty
import dev.kmapx.core.model.MNullStrategy
import dev.kmapx.core.model.MQualifiedConverter
import dev.kmapx.core.model.MType
import dev.kmapx.core.model.TypeKind
import dev.kmapx.core.plan.Argument
import dev.kmapx.core.plan.Branch
import dev.kmapx.core.plan.Construction
import dev.kmapx.core.plan.Emission
import dev.kmapx.core.plan.MappingPlan
import dev.kmapx.core.plan.PostAssignment
import dev.kmapx.core.plan.ValueSource

/**
 * Scope actual: matching por nombre, matriz de nulabilidad completa (celdas
 * silenciosas + estrategias `@WithDefault`/`@OrThrow`/`@AllowUnsafe` con KMX016/017/018),
 * conversiones implícitas de la LISTA CERRADA (idéntico, `T→T?`, wrap/unwrap de value
 * class, colecciones elemento a elemento en una pasada), resolución determinista de
 * construcción y `var`s de cuerpo post-construcción vía `.also`.
 * Fuera de alcance (ver specs): useTargetDefaults, converters,
 * anidados no-colección, Map/arrays.
 *
 * Contract: this engine NEVER throws on invalid input — it returns diagnostics.
 */
public class MappingEngine {

    // Colaboradores, cada uno con UNA responsabilidad (SRP). El motor los COMPONE; el estado
    // compartido de cada resolución viaja en [Ctx]. La orquestación recursiva (sealed dispatch,
    // bidireccional) se queda aquí porque vuelve a llamar a `resolve`.
    private val construction = ConstructionResolver()
    private val sources = SourceMatcher()
    private val values = ValueResolver()
    private val enums = EnumDispatcher()
    private val sealed = SealedDispatcher()

    public fun resolve(
        source: MClass,
        target: MClass,
        emission: Emission,
        declaredMappings: Map<Pair<String, String>, String> = emptyMap(),
        converters: Map<Pair<String, String>, List<String>> = emptyMap(),
        resolvedPaths: Map<String, dev.kmapx.core.model.MPath> = emptyMap(),
        useSerialNames: Boolean = false,
        /** Cadena de políticas de nivel (mapper/mapeo → global), sin INHERIT. */
        nullPolicies: List<NullPolicy> = emptyList(),
        allowInjectedConverters: Boolean = false,
        /** Habilita las conversiones estándar opt-in (el widening es incondicional). */
        stdConverters: Boolean = false,
        /** Severidad de la omisión (KMX021). */
        unmapped: UnmappedPolicy = UnmappedPolicy.WARN,
        /** Lookup cross-module de un par anidado no declarado localmente (design doc). */
        crossModuleMappings: (String, String) -> String? = { _, _ -> null },
    ): MappingPlan {
        val diagnostics = mutableListOf<Diagnostic>()
        val targetLocation = MLocation(target.type.qualifiedName)
        val ctx = Ctx(
            targetLocation = targetLocation,
            mappingPair = "${source.type.simpleName} -> ${target.type.simpleName}",
            declaredMappings = declaredMappings,
            crossModuleMappings = crossModuleMappings,
            converters = converters,
            resolvedPaths = resolvedPaths,
            useSerialNames = useSerialNames,
            nullPolicies = nullPolicies,
            allowInjectedConverters = allowInjectedConverters,
            stdConverters = stdConverters,
            unmapped = unmapped,
            diagnostics = diagnostics,
        )

        // Jerarquías sealed paralelas → dispatch exhaustivo, nunca construcción directa.
        // La recursión es del ORQUESTADOR: el colaborador recibe resolve() como lambda.
        if (sealed.isSealed(source.type) && sealed.isSealed(target.type)) {
            return sealed.resolve(source, target, emission, ctx) { sub, counterpart, subEmission ->
                resolve(
                    source = sub,
                    target = counterpart,
                    emission = subEmission,
                    declaredMappings = ctx.declaredMappings,
                    crossModuleMappings = ctx.crossModuleMappings,
                    converters = ctx.converters,
                    // La cascada de políticas aplica también dentro del dispatch sealed.
                    nullPolicies = ctx.nullPolicies,
                    stdConverters = ctx.stdConverters,
                    unmapped = ctx.unmapped,
                )
            }
        }

        // Enums paralelos → when por igualdad, mismo principio de exhaustividad.
        if (source.type.kind == TypeKind.ENUM && target.type.kind == TypeKind.ENUM) {
            return enums.resolve(source, target, emission, ctx)
        }

        val mechanism = construction.resolve(target, targetLocation, diagnostics)
            ?: return MappingPlan(
                source = source.type,
                target = target.type,
                emission = emission,
                construction = null,
                diagnostics = diagnostics,
            )

        val arguments = mutableListOf<Argument>()
        for (param in mechanism.params) {
            // Campo ignorado — omisión SILENCIOSA (el ignore es el consentimiento
            // explícito: acalla KMX002/KMX021). Sin default no hay forma de omitir → KMX042.
            if (param.ignored) {
                if (!param.hasDefault) {
                    diagnostics += Diagnostics.cannotIgnore(targetLocation, param.name)
                }
                continue
            }
            // Fuente ausente + default declarado → omisión con la severidad de la
            // política `unmapped` (WARN histórico; IGNORE acalla; ERROR bloquea).
            // Solo aplica al matching implícito (un renombre exige fuente).
            if (param.mappedFrom == null && param.hasDefault && source.property(param.name) == null) {
                if (ctx.unmapped != UnmappedPolicy.IGNORE) {
                    diagnostics += Diagnostics.targetDefaultFilled(
                        targetLocation, param.name,
                        severity = when (ctx.unmapped) {
                            UnmappedPolicy.ERROR -> dev.kmapx.core.diagnostics.Severity.ERROR
                            else -> dev.kmapx.core.diagnostics.Severity.WARNING
                        },
                    )
                }
                continue
            }
            val lookup = sources.find(source, param.name, param.mappedFrom, ctx) ?: continue
            val resolved = values.resolve(lookup.property, param, ctx, lookup.nullableSegment)
            if (resolved != null) arguments += Argument(param.name, resolved)
        }

        // La omisión CONDICIONAL bifurca la llamada; el límite de emisión es K=2.
        val omissible = arguments.filter { it.value is ValueSource.NullFallbackToDefault }
        if (omissible.size > OMISSIBLE_LIMIT) {
            diagnostics += Diagnostics.tooManyOmissibleDefaults(
                targetLocation, omissible.map { it.paramName }, OMISSIBLE_LIMIT,
            )
        }

        val postAssignments = resolvePostAssignments(source, target, mechanism, ctx)

        val construction = when (mechanism) {
            is Mechanism.Ctor -> Construction.ConstructorCall(arguments, postAssignments)
            is Mechanism.Factory -> Construction.FactoryCall(
                qualifiedFunction = mechanism.factory.qualifiedName,
                companionOf = mechanism.factory.companionOf,
                arguments = arguments,
                postAssignments = postAssignments,
            )
        }

        return MappingPlan(
            source = source.type,
            target = target.type,
            emission = emission,
            construction = construction,
            diagnostics = diagnostics,
        )
    }

    /** Ida y vuelta desde una declaración. [diagnostics] YA trae las asimetrías como KMX028. */
    public data class BiResolution(
        val forward: MappingPlan,
        val reverse: MappingPlan,
        val diagnostics: List<Diagnostic>,
    ) {
        public val valid: Boolean
            get() = diagnostics.none { it.severity == dev.kmapx.core.diagnostics.Severity.ERROR }
    }

    /**
     * `@BiMapTo`: resuelve ambas direcciones y VALIDA la invertibilidad. Los renombres
     * `@MapFrom(from=...)` de la ida se invierten solos; las fallas de la VUELTA se traducen a
     * KMX028 con el detalle de la asimetría (converter sin inverso, ensanchamiento sin
     * estrategia, campo de un solo lado, anidado unidireccional); fan-out y omisión por
     * default también son KMX028 (el round-trip no reconstruye).
     */
    public fun resolveBidirectional(
        a: MClass,
        b: MClass,
        forwardEmission: Emission,
        reverseEmission: Emission,
        declaredMappings: Map<Pair<String, String>, String> = emptyMap(),
        converters: Map<Pair<String, String>, List<String>> = emptyMap(),
        useSerialNames: Boolean = false,
        nullPolicies: List<NullPolicy> = emptyList(),
        stdConverters: Boolean = false,
        unmapped: UnmappedPolicy = UnmappedPolicy.WARN,
    ): BiResolution {
        val forward = resolve(
            a, b, forwardEmission, declaredMappings, converters,
            useSerialNames = useSerialNames, nullPolicies = nullPolicies, stdConverters = stdConverters,
            unmapped = unmapped,
        )
        val reverse = resolve(
            // Los overrides @MapEntry de la ida se INVIERTEN para la vuelta — el
            // espejo de injectReverseRenames, en clave de enums (gap cerrado, 2026-07-16).
            injectReverseEntryOverrides(a, b), injectReverseRenames(a, b), reverseEmission,
            declaredMappings, converters,
            useSerialNames = useSerialNames, nullPolicies = nullPolicies, stdConverters = stdConverters,
            unmapped = unmapped,
        )

        val diagnostics = mutableListOf<Diagnostic>()
        val aName = a.type.simpleName

        // El fallback de CLASE es fan-in (varios entries → uno): la vuelta no sabe a
        // cuál regresar — no invertible, la misma regla que el fan-out de campos.
        listOf(a, b).forEach { side ->
            val fallback = side.enumFallback
            if (side.type.kind == TypeKind.ENUM && fallback != null) {
                diagnostics += Diagnostics.notInvertible(
                    MLocation(side.type.qualifiedName), fallback,
                    "the class-level @MapEntry fallback is fan-in and the way back cannot split '$fallback'",
                    "remove the class-level fallback",
                )
            }
        }
        // Dos entries de la ida apuntando al MISMO destino también son fan-in.
        if (a.type.kind == TypeKind.ENUM) {
            a.enumEntries.groupBy { it.targetOverride ?: it.name }
                .filterValues { it.size > 1 }
                .forEach { (wanted, group) ->
                    diagnostics += Diagnostics.notInvertible(
                        MLocation(a.type.qualifiedName), wanted,
                        "fan-in: ${group.size} entries (${group.joinToString { it.name }}) map to " +
                            "'$wanted' and the way back cannot split it",
                        "keep a single entry per target entry",
                    )
                }
        }

        // Un campo IGNORADO en cualquiera de los dos lados rompe el round-trip
        // (la omisión no se reconstruye) — misma regla que la omisión por default (KMX021).
        listOf(a, b).forEach { side ->
            val ignored = side.constructors.flatMap { c -> c.params.filter { it.ignored } }.map { it.name } +
                side.properties.filter { it.ignored }.map { it.name }
            ignored.distinct().forEach { name ->
                diagnostics += Diagnostics.notInvertible(
                    MLocation(side.type.qualifiedName), name,
                    "the field is ignored and the round-trip cannot reconstruct it",
                    "remove the ignore",
                )
            }
        }

        // Fan-out en la ida: dos params de B leyendo la misma propiedad de A → la vuelta no divide.
        b.primaryConstructor?.params
            ?.groupBy { it.mappedFrom ?: it.name }
            ?.filterValues { it.size > 1 }
            ?.forEach { (from, group) ->
                diagnostics += Diagnostics.notInvertible(
                    MLocation(b.type.qualifiedName), group.first().name,
                    "fan-out: ${group.size} parameters (${group.joinToString { it.name }}) " +
                        "read from '$from' and the way back cannot split one value",
                    "keep a single parameter per source property",
                )
            }

        forward.diagnostics.forEach { diagnostics += targetDefaultAsNotInvertible(it) }

        reverse.diagnostics.forEach { d ->
            val location = MLocation(d.location.qualifiedClassName)
            val member = d.location.member ?: "?"
            diagnostics += when (d.code) {
                DiagnosticCode.KMX004 -> Diagnostics.notInvertible(
                    location, member,
                    d.message.removeSuffix(".")
                        .replaceFirst("cannot convert", "missing converter") +
                        " for the reverse direction",
                    "register the inverse @Converter",
                )
                DiagnosticCode.KMX003 -> Diagnostics.notInvertible(
                    location, member,
                    "the forward direction widens to nullable and the way back has no null-handling strategy",
                    "annotate the parameter on $aName with @MapField(onNull = LITERAL/THROW/UNSAFE)",
                )
                DiagnosticCode.KMX002 -> Diagnostics.notInvertible(
                    location, member,
                    "the field exists only on $aName and the round-trip cannot reconstruct it",
                    "add the missing counterpart property",
                )
                DiagnosticCode.KMX007 -> Diagnostics.notInvertible(
                    location, member,
                    "the nested pair is declared one-way only",
                    "declare @BiMapTo on the nested type",
                )
                else -> targetDefaultAsNotInvertible(d)
            }
        }
        return BiResolution(forward, reverse, diagnostics)
    }

    /** KMX021 (relleno por default) en CUALQUIER dirección = el round-trip no reconstruye. */
    private fun targetDefaultAsNotInvertible(d: Diagnostic): Diagnostic =
        if (d.code == DiagnosticCode.KMX021) {
            Diagnostics.notInvertible(
                MLocation(d.location.qualifiedClassName), d.location.member ?: "?",
                "the field is filled by a target default and the round-trip cannot reconstruct it",
                "add the missing counterpart property",
            )
        } else {
            d
        }

    /**
     * Los overrides `@MapEntry` declarados en los entries de A definen el
     * emparejamiento de VUELTA: el entry destino de B recibe el override INVERTIDO
     * (`CRIMSON → RED` si A declaró `RED → CRIMSON`). Un override EXPLÍCITO de B gana
     * (B puede declarar los suyos para otros mapeos).
     */
    private fun injectReverseEntryOverrides(a: MClass, b: MClass): MClass {
        if (a.type.kind != TypeKind.ENUM || b.type.kind != TypeKind.ENUM) return b
        val reverseOverrides = a.enumEntries
            .mapNotNull { entry -> entry.targetOverride?.let { it to entry.name } }
            .toMap()
        if (reverseOverrides.isEmpty()) return b
        return b.copy(
            enumEntries = b.enumEntries.map { entry ->
                val inverted = reverseOverrides[entry.name]
                if (entry.targetOverride == null && inverted != null) {
                    entry.copy(targetOverride = inverted)
                } else {
                    entry
                }
            },
        )
    }

    /** El `@MapFrom(from = x)` declarado en B define el matching de VUELTA sobre los params de A. */
    private fun injectReverseRenames(a: MClass, b: MClass): MClass {
        val reverseFrom = buildMap {
            b.constructors.forEach { ctor ->
                ctor.params.forEach { p -> p.mappedFrom?.let { put(it, p.name) } }
            }
            b.properties.forEach { pr -> pr.mappedFrom?.let { put(it, pr.name) } }
        }
        if (reverseFrom.isEmpty()) return a
        return a.copy(
            constructors = a.constructors.map { c ->
                c.copy(
                    params = c.params.map { p ->
                        val inverted = reverseFrom[p.name]
                        if (p.mappedFrom == null && inverted != null) p.copy(mappedFrom = inverted) else p
                    },
                )
            },
            properties = a.properties.map { pr ->
                val inverted = reverseFrom[pr.name]
                if (pr.mappedFrom == null && inverted != null) pr.copy(mappedFrom = inverted) else pr
            },
        )
    }

    /** Resultado de resolver un método PATCH. Diagnósticos con error ⇒ no se emite. */
    public data class PatchResolution(
        val fields: List<dev.kmapx.core.plan.PatchField>,
        val diagnostics: List<Diagnostic>,
    )

    /**
     * Matching PATCH-driven: cada propiedad del patch busca su campo en el target
     * (por nombre, honrando `@MapFrom(from=...)` del target). Patch nullable → fallback
     * al valor actual (null = no tocar); no-nullable → asignación incondicional. Las
     * conversiones (wrap/unwrap, converters) aplican DENTRO del fallback vía safeCall.
     */
    public fun resolvePatch(
        target: MClass,
        patch: MClass,
        declaredMappings: Map<Pair<String, String>, String> = emptyMap(),
        converters: Map<Pair<String, String>, List<String>> = emptyMap(),
        resolvedPaths: Map<String, dev.kmapx.core.model.MPath> = emptyMap(),
    ): PatchResolution {
        val diagnostics = mutableListOf<Diagnostic>()
        val targetLocation = MLocation(target.type.qualifiedName)

        if (target.type.kind != TypeKind.DATA_CLASS) {
            diagnostics += Diagnostics.patchTargetNotDataClass(targetLocation)
            return PatchResolution(emptyList(), diagnostics)
        }

        val ctx = Ctx(
            targetLocation = targetLocation,
            mappingPair = "${patch.type.simpleName} -> ${target.type.simpleName}",
            declaredMappings = declaredMappings,
            converters = converters,
            resolvedPaths = resolvedPaths,
            useSerialNames = false,
            diagnostics = diagnostics,
        )

        val fields = mutableListOf<dev.kmapx.core.plan.PatchField>()

        // Campos del target con RUTA leen del patch (`patch.meta?.note ?: target.x`);
        // el root de cada ruta cuenta como propiedad del patch consumida (no es KMX002).
        // La ruta puede vivir en la PROPERTY o en el PARAM del constructor (el @Map de un
        // `val` de constructor va al parámetro) — se consideran ambos, param primero.
        data class PathField(
            val name: String,
            val from: String,
            val type: MType,
            val strategies: List<MNullStrategy>,
            val useConverter: MQualifiedConverter?,
        )
        val pathFields = buildList {
            target.primaryConstructor?.params
                ?.filter { it.mappedFrom?.contains('.') == true }
                ?.forEach { add(PathField(it.name, it.mappedFrom!!, it.type, it.strategies, it.useConverter)) }
            target.properties
                .filter { it.mappedFrom?.contains('.') == true }
                .filter { field -> none { it.name == field.name } }
                .forEach { add(PathField(it.name, it.mappedFrom!!, it.type, it.strategies, it.useConverter)) }
        }
        val pathRoots = mutableSetOf<String>()
        pathFields.forEach { field ->
            pathRoots += field.from.substringBefore('.')
            val lookup = sources.resolvePath(patch, field.name, field.from, ctx) ?: return@forEach
            val fallback = lookup.property.type.nullable
            val paramType = if (fallback) field.type.asNullable() else field.type
            val value = values.resolve(
                lookup.property,
                MConstructorParam(field.name, paramType, strategies = field.strategies, useConverter = field.useConverter),
                ctx, lookup.nullableSegment,
            ) ?: return@forEach
            fields += dev.kmapx.core.plan.PatchField(field.name, value, fallback)
        }

        // Renombre en PATCH: el `@MapField(from=)` de un `val` de constructor vive en el PARAM (igual
        // que las rutas de arriba) — el matching consulta AMBAS sedes, param primero.
        val renamedFrom = buildMap {
            target.properties.forEach { pr ->
                pr.mappedFrom?.takeIf { '.' !in it }?.let { put(it, pr.name) }
            }
            target.primaryConstructor?.params?.forEach { p ->
                p.mappedFrom?.takeIf { '.' !in it }?.let { put(it, p.name) }
            }
        }

        // Los aspectos por campo (`converter`, `onNull`) de un `val` de constructor viven en el
        // PARÁMETRO; los de un `var` de cuerpo, en la property. El patch consulta ambas sedes
        // (param primero) para que `@MapField(converter=)` NO se pierda al parchear.
        val ctorParamsByName = target.primaryConstructor?.params?.associateBy { it.name } ?: emptyMap()
        fun converterFor(name: String): MQualifiedConverter? =
            ctorParamsByName[name]?.useConverter ?: target.properties.firstOrNull { it.name == name }?.useConverter
        fun strategiesFor(name: String): List<MNullStrategy> =
            ctorParamsByName[name]?.strategies?.takeIf { it.isNotEmpty() }
                ?: target.properties.firstOrNull { it.name == name }?.strategies ?: emptyList()

        for (property in patch.properties) {
            if (property.name in pathRoots) continue
            val wantedName = renamedFrom[property.name] ?: property.name
            val field = target.properties.firstOrNull { it.name == wantedName }
            if (field == null) {
                // Campo del patch sin par en el target: PATCH es sobre el target, esto es error.
                diagnostics += Diagnostics.missingSource(
                    targetLocation, property.name,
                    didYouMean = Suggestions.closest(property.name, target.properties.map { it.name }),
                )
                continue
            }
            // Campo `Patch<T>` → tri-estado. El valor interno (`Set.value`, tipo T)
            // se resuelve hacia el campo del target por la cadena estándar (convierte si hace
            // falta), referenciado como `value`; el emisor lo envuelve en un `when` exhaustivo.
            if (property.type.qualifiedName == PATCH_TYPE) {
                val innerType = property.type.typeArgs.singleOrNull() ?: continue
                val value = values.resolve(
                    property = MProperty("value", innerType),
                    param = MConstructorParam(
                        field.name, field.type,
                        strategies = strategiesFor(field.name), useConverter = converterFor(field.name),
                    ),
                    ctx = ctx,
                ) ?: continue
                fields += dev.kmapx.core.plan.PatchField(field.name, value, fallbackToTarget = false, tristate = true)
                continue
            }

            val fallback = property.type.nullable
            // Con fallback, el par se resuelve hacia el tipo NULLABLE del campo: la conversión
            // compone con safeCall y el `?: target.x` cierra la nulabilidad.
            val paramType = if (fallback) field.type.asNullable() else field.type
            val value = values.resolve(
                property = property,
                param = MConstructorParam(
                    field.name, paramType,
                    strategies = strategiesFor(field.name), useConverter = converterFor(field.name),
                ),
                ctx = ctx,
            ) ?: continue
            fields += dev.kmapx.core.plan.PatchField(field.name, value, fallback)
        }
        return PatchResolution(fields, diagnostics)
    }

    private fun isSealed(t: MType): Boolean =
        t.kind == TypeKind.SEALED_CLASS || t.kind == TypeKind.SEALED_INTERFACE


    /**
     * `when` exhaustivo sin `else` entre jerarquías paralelas. Emparejamiento por
     * `simpleName` idéntico; override con `@MapSubtype`. Cada par genera su sub-plan con
     * TODAS las reglas normales, emitido como función nombrada propia; los pares
     * `object` ↔ `object` son referencia directa. Source sin par → KMX010; target sin par →
     * warning KMX023; anidamiento sealed → KMX024 (un nivel en v1).
     */
    private fun resolveSealedDispatch(
        source: MClass,
        target: MClass,
        emission: Emission,
        ctx: Ctx,
    ): MappingPlan {
        val branches = mutableListOf<Branch>()
        val matchedTargets = mutableSetOf<String>()

        for (subtype in source.sealedSubtypes) {
            val subtypeLocation = MLocation(subtype.type.qualifiedName)
            if (isSealed(subtype.type)) {
                ctx.diagnostics += Diagnostics.deepSealedNesting(subtypeLocation)
                continue
            }
            val counterpart = subtype.subtypeTargetOverride
                ?.let { override -> target.sealedSubtypes.firstOrNull { it.type.qualifiedName == override } }
                ?: target.sealedSubtypes.firstOrNull { it.type.simpleName == subtype.type.simpleName }
            if (counterpart == null) {
                ctx.diagnostics += Diagnostics.subtypeWithoutCounterpart(
                    subtypeLocation, target.type.qualifiedName,
                )
                continue
            }
            matchedTargets += counterpart.type.qualifiedName

            val plan = if (subtype.type.kind == TypeKind.OBJECT && counterpart.type.kind == TypeKind.OBJECT) {
                // `data object` ↔ `data object`: referencia directa, sin sub-función.
                MappingPlan(
                    source = subtype.type,
                    target = counterpart.type,
                    emission = emission,
                    construction = Construction.ObjectReference(counterpart.type.qualifiedName),
                )
            } else {
                val subPlan = resolve(
                    source = subtype,
                    target = counterpart,
                    emission = Emission.ExtensionFunction("to${counterpart.type.simpleName}"),
                    declaredMappings = ctx.declaredMappings,
                    converters = ctx.converters,
                    // La cascada de políticas aplica también dentro del dispatch sealed
                    // (antes solo se propagaba useTargetDefaults; la cadena unifica).
                    nullPolicies = ctx.nullPolicies,
                )
                ctx.diagnostics += subPlan.diagnostics
                subPlan
            }
            branches += Branch(subtype.type.qualifiedName, plan)
        }

        // Subtipos del TARGET sin par: legítimo (puede tener más casos), pero nunca silencioso.
        target.sealedSubtypes
            .filter { it.type.qualifiedName !in matchedTargets }
            .forEach {
                ctx.diagnostics += Diagnostics.targetSubtypeUnmatched(
                    MLocation(it.type.qualifiedName), source.type.qualifiedName,
                )
            }

        return MappingPlan(
            source = source.type,
            target = target.type,
            emission = emission,
            construction = Construction.SealedDispatch(branches),
            diagnostics = ctx.diagnostics,
        )
    }

    /**
     * `var`s públicas de cuerpo fuera del mecanismo elegido, SOLO si el matching las
     * cubre (sin fuente → se ignoran en silencio: mapear es sobre el target). Participan de las
     * mismas reglas de nulabilidad/conversión que los argumentos.
     */
    private fun resolvePostAssignments(
        source: MClass,
        target: MClass,
        mechanism: Mechanism,
        ctx: Ctx,
    ): List<PostAssignment> {
        val mechanismParams = mechanism.params.map { it.name }.toSet()
        return target.properties
            // Una var ignorada no se asigna (sin exigencia de default: no es argumento).
            .filter { it.mutable && !it.computed && it.name !in mechanismParams && !it.ignored }
            .mapNotNull { targetProp ->
                // Un renombre explícito en la var exige que la fuente exista (KMX011);
                // sin renombre, la var sin fuente se ignora en silencio.
                val lookup = if (targetProp.mappedFrom != null) {
                    sources.find(source, targetProp.name, targetProp.mappedFrom, ctx) ?: return@mapNotNull null
                } else {
                    SourceLookup(source.property(targetProp.name) ?: return@mapNotNull null)
                }
                // Las post-asignaciones participan de las mismas reglas (estrategias incluidas).
                val asParam = MConstructorParam(
                    targetProp.name, targetProp.type,
                    hasDefault = false, strategies = targetProp.strategies,
                )
                values.resolve(lookup.property, asParam, ctx, lookup.nullableSegment)
                    ?.let { PostAssignment(targetProp.name, it) }
            }
    }


    private companion object {
        /** Máximo de campos con omisión condicional por mecanismo (más → KMX022). */
        const val OMISSIBLE_LIMIT = 2

        /** Tipo tri-estado del runtime para PATCH set-null. */
        const val PATCH_TYPE = "dev.kmapx.runtime.Patch"
    }
}
