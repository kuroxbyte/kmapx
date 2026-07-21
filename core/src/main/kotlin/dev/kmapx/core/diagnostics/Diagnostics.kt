package dev.kmapx.core.diagnostics

/**
 * Central diagnostic catalog.
 *
 * Every error/warning the engine can produce is declared here with a stable code.
 * The core NEVER throws or logs: it returns [Diagnostic] values; frontends decide how
 * to report them (e.g. KSPLogger.error against the originating symbol).
 * Negative tests assert on [DiagnosticCode], not on full message strings.
 */
public enum class DiagnosticCode(public val id: String) {
    /** Internal invariant broken while resolving. Always a kmapx bug. */
    KMX001("KMX001"),

    /** Target constructor parameter has no source property and no declared mapping. */
    KMX002("KMX002"),

    /** Nullability violation: source `T?` mapped to non-nullable target without a strategy. */
    KMX003("KMX003"),

    /** Incompatible types with no known conversion and no registered converter. */
    KMX004("KMX004"),

    /** No resolvable constructor: primary not visible/usable and no @MapConstructor/@MapFactory. */
    KMX005("KMX005"),

    /** Ambiguous construction: more than one @MapConstructor or @MapFactory applies. */
    KMX006("KMX006"),

    /** Nested mapping required but not declared: annotate the nested type or register a converter. */
    KMX007("KMX007"),

    /** Mapping cycle detected across declared mappings. */
    KMX008("KMX008"),

    /** Ambiguous converters: more than one @Converter applies to the same conversion. */
    KMX009("KMX009"),

    /** Sealed dispatch: source subtype has no counterpart in the target hierarchy. */
    KMX010("KMX010"),

    /** Renamed source property (@MapFrom(from=...)) does not exist. */
    KMX011("KMX011"),

    /** @PatchMapper target is not a data class: copy() is unavailable. */
    KMX012("KMX012"),

    /** Two @MapTo targets produce the same generated function name; an explicit name is required. */
    KMX013("KMX013"),

    /** @Mapper after-function has the wrong signature: must be (Source, Target) -> Target. */
    KMX014("KMX014"),

    /** @Mapper interface shape not supported in v1: generics or inheritance between @Mapper interfaces. */
    KMX015("KMX015"),

    /** More than one null-handling strategy declared on the same target parameter/property. */
    KMX016("KMX016"),

    /** @WithDefault literal not parseable for the target type, or type unsupported. */
    KMX017("KMX017"),

    /** Null-handling strategy declared where there is no `T? -> T` violation: dead strategy (WARNING). */
    KMX018("KMX018"),

    /** @Converter function with an invalid shape: must be a pure top-level `fun (A) -> B`. */
    KMX019("KMX019"),

    /** @MapFrom(from = ...) with malformed path syntax: empty segments, leading/trailing dots. */
    KMX020("KMX020"),

    /** Parameter has no source but declares a default: filled by the target default (WARNING). */
    KMX021("KMX021"),

    /** More omissible defaults than the emission limit K=2. */
    KMX022("KMX022"),

    /** Sealed dispatch: TARGET subtype has no source counterpart — the when stays exhaustive (WARNING). */
    KMX023("KMX023"),

    /** Sealed dispatch: nested sealed hierarchies — one level only in v1. */
    KMX024("KMX024"),

    /** KMP: `expect` declarations cannot be mapped in v1. */
    KMX025("KMX025"),

    /** Enum dispatch: SOURCE entry has no counterpart in the target enum, or @MapEntry points nowhere. */
    KMX026("KMX026"),

    /** `@MapField(converter=)`: the referenced Converts<A,B> does not match the field's source/target types. */
    KMX027("KMX027"),

    /** @BiMapTo: the mapping is not invertible — asymmetric field, missing inverse converter, fan-out. */
    KMX028("KMX028"),

    /** `@MapField(converter=)`: the referenced object does not implement `dev.kmapx.runtime.Converts`. */
    KMX029("KMX029"),

    /** componentModel requires a framework that is not on the compile classpath. */
    KMX030("KMX030"),

    /** `@MapField(converter=)` declared where source and target types are identical: unnecessary (WARNING). */
    KMX031("KMX031"),

    /** Per-field config declared both on the field and on the @Mapper method; the method wins (WARNING). */
    KMX032("KMX032"),

    /** @OrEmpty declared on a field that is not a List/Set/Map: no empty collection applies. */
    KMX033("KMX033"),

    /** `@MapField(converter=)` with an injected (class) converter used in embedded mode: nowhere to inject. */
    KMX034("KMX034"),

    /** Injected converter class is not a @Component but componentModel = SPRING. */
    KMX035("KMX035"),

    /** @MapField addressing: `target` is required on the method site and forbidden on the field site. */
    KMX036("KMX036"),

    /** More than one @MapField for the same destination field. */
    KMX037("KMX037"),

    /** @MapField(onNull = LITERAL) without a `default` literal. */
    KMX038("KMX038"),

    /** @MapField `default` set but onNull != LITERAL: the literal is ignored (WARNING). */
    KMX039("KMX039"),

    /** @MapField(onNull = TARGET_DEFAULT) on a parameter without a usable constructor default. */
    KMX040("KMX040"),

    /** onNull = LITERAL/UNSAFE used as a mapper/mapping-level policy: those are field-only. */
    KMX041("KMX041"),

    /** Ignored field cannot be omitted: no constructor default. */
    KMX042("KMX042"),

    /** @MapField(ignore = true) combined with other aspects: dead configuration. */
    KMX043("KMX043"),

    /** @Mapper(config = X::class) where X is not a valid @MapperConfig profile. */
    KMX044("KMX044"),

    /** @InverseOf: forward method missing/ambiguous, wrong shape, or own @MapField declared. */
    KMX045("KMX045"),

    /** Contract collection method with no element mapping to delegate to. */
    KMX046("KMX046"),

    /** Class-level @MapEntry fallback pointing at a non-existent target entry. */
    KMX047("KMX047"),
}

/** Where the problem lives, in user-source terms. The frontend maps this back to a KSP symbol. */
public data class MLocation(
    val qualifiedClassName: String,
    val member: String? = null,
)

public enum class Severity { ERROR, WARNING }

public data class Diagnostic(
    val code: DiagnosticCode,
    val location: MLocation,
    val message: String,
    val fix: String,
    val severity: Severity = Severity.ERROR,
) {
    /** Render in the canonical `[KMXnnn] location message Fix: ...` shape used across docs and tests. */
    public fun render(): String {
        val loc = location.member?.let { "${location.qualifiedClassName}.$it" } ?: location.qualifiedClassName
        return "[${code.id}] $loc $message Fix: $fix"
    }
}

/**
 * Factory functions: the ONLY sanctioned way to build diagnostics.
 * Keeps codes, wording and fixes consistent; specs list these messages as contract.
 */
public object Diagnostics {

    /** [didYouMean] puede traer hasta 2 candidatos (empate en distancia de edición). */
    public fun missingSource(target: MLocation, paramName: String, didYouMean: List<String> = emptyList()): Diagnostic =
        Diagnostic(
            code = DiagnosticCode.KMX002,
            location = target.copy(member = paramName),
            message = buildString {
                append("no source property found for constructor parameter '$paramName'.")
                if (didYouMean.isNotEmpty()) {
                    append(" Did you mean ${didYouMean.joinToString(" or ") { "'$it'" }}?")
                }
            },
            fix = "add a matching source property, or use @MapField(from = \"...\") to rename.",
        )

    /** [defaultAvailable] — la salida `useTargetDefaults` se menciona SOLO si el parámetro tiene default. */
    public fun nullabilityViolation(
        target: MLocation,
        paramName: String,
        sourceClass: String,
        sourceProperty: String,
        defaultAvailable: Boolean = false,
        nullableSegment: String? = null,
    ): Diagnostic =
        Diagnostic(
            code = DiagnosticCode.KMX003,
            location = target.copy(member = paramName),
            message = buildString {
                append("is non-nullable but source $sourceClass.$sourceProperty is nullable")
                if (nullableSegment != null) append(" (segment '$nullableSegment' is nullable)")
                append(".")
            },
            fix = buildString {
                if (defaultAvailable) append("set onNull = TARGET_DEFAULT (field, mapper or global) to apply the target default, or ")
                else append("add a default parameter (with onNull = TARGET_DEFAULT), or ")
                append("annotate with @MapField(onNull = LITERAL/THROW/UNSAFE).")
            },
        )

    public fun incompatibleTypes(target: MLocation, paramName: String, from: String, to: String): Diagnostic =
        Diagnostic(
            code = DiagnosticCode.KMX004,
            location = target.copy(member = paramName),
            message = "cannot convert $from to $to.",
            fix = "register a @Converter fun ($from) -> $to, or declare a mapping between the types.",
        )

    /**
     * KMX004 para un CRUCE de contenedor (`List → Set`, `Map → List`…): no es implícito por
     * diseño (cambiaría la estructura o descartaría datos en silencio). El fix es específico
     * para que el usuario elija conscientemente.
     */
    public fun containerCross(
        target: MLocation,
        paramName: String,
        from: String,
        to: String,
    ): Diagnostic {
        val fromShort = from.substringAfterLast('.')
        val toShort = to.substringAfterLast('.')
        val why = when (toShort) {
            "Set" -> " ($fromShort -> Set would silently drop duplicates)"
            "Map" -> " (no obvious key to build the Map from)"
            "List" -> if (fromShort == "Map") " (a Map has no list order)" else ""
            else -> ""
        }
        return Diagnostic(
            code = DiagnosticCode.KMX004,
            location = target.copy(member = paramName),
            message = "cannot convert container $fromShort to $toShort$why.",
            fix = "the container list is closed by design; map to the same container, " +
                "or register a @Converter fun ($from) -> $to to do the cross explicitly.",
        )
    }

    public fun noNestedMapping(target: MLocation, paramName: String, from: String, to: String): Diagnostic =
        Diagnostic(
            code = DiagnosticCode.KMX007,
            location = target.copy(member = paramName),
            message = "no mapping found for $from -> $to required by '$paramName'.",
            fix = "annotate $from with @MapTo($to::class) or register a @Converter.",
        )

    public fun ambiguousConverters(
        target: MLocation,
        paramName: String,
        from: String,
        to: String,
        candidates: List<String>,
    ): Diagnostic =
        Diagnostic(
            code = DiagnosticCode.KMX009,
            location = target.copy(member = paramName),
            message = "ambiguous converters for $from -> $to: ${candidates.joinToString()}.",
            fix = "keep exactly one @Converter for the pair, or convert explicitly per field (post-1.0).",
        )

    public fun invalidConverterSignature(converter: MLocation, detail: String): Diagnostic =
        Diagnostic(
            code = DiagnosticCode.KMX019,
            location = converter,
            message = "invalid @Converter signature: $detail.",
            fix = "declare a pure top-level function with exactly one parameter and a non-Unit " +
                "return type: @Converter fun (A) -> B.",
        )

    /** KMX010: subtipo del SOURCE sin par en la jerarquía target. */
    public fun subtypeWithoutCounterpart(sourceSubtype: MLocation, targetRoot: String): Diagnostic =
        Diagnostic(
            code = DiagnosticCode.KMX010,
            location = sourceSubtype,
            message = "has no counterpart in ${targetRoot.substringAfterLast('.')}.",
            fix = "add ${targetRoot.substringAfterLast('.')}.${sourceSubtype.qualifiedClassName.substringAfterLast('.')} " +
                "or declare @MapSubtype on the source subtype.",
        )

    /**
     * KMX027: el `Converts<A,B>` del aspecto `converter` no encaja con el par del campo.
     * [declared] = A -> B del converter; [required] = source -> target del campo.
     */
    public fun converterTypeMismatch(
        target: MLocation,
        paramName: String,
        converter: String,
        declared: String,
        required: String,
    ): Diagnostic =
        Diagnostic(
            code = DiagnosticCode.KMX027,
            location = target.copy(member = paramName),
            message = "converter ${converter.substringAfterLast('.')} expects $declared " +
                "but field requires $required.",
            fix = "use a converter whose Converts<A, B> matches the field, or remove the converter aspect.",
        )

    /** KMX029: el object referenciado por el aspecto `converter` no implementa `Converts`. */
    public fun notAConverter(target: MLocation, paramName: String, converter: String): Diagnostic =
        Diagnostic(
            code = DiagnosticCode.KMX029,
            location = target.copy(member = paramName),
            message = "@MapField converter ${converter.substringAfterLast('.')} does not " +
                "implement dev.kmapx.runtime.Converts.",
            fix = "reference an object that implements Converts<A, B> for the field's types.",
        )

    /** KMX031: aspecto `converter` sobre un campo cuyo par NO necesita conversión (A == B). */
    public fun unnecessaryConverter(target: MLocation, paramName: String, converter: String): Diagnostic =
        Diagnostic(
            code = DiagnosticCode.KMX031,
            location = target.copy(member = paramName),
            message = "@MapField converter ${converter.substringAfterLast('.')} is unnecessary: " +
                "source and target types are identical.",
            fix = "remove the converter aspect; the value maps directly.",
            severity = Severity.WARNING,
        )

    /** KMX034: un converter con dependencias (class) se usó en modo embedded (extension). */
    public fun injectedConverterInModeA(target: MLocation, paramName: String, converter: String): Diagnostic =
        Diagnostic(
            code = DiagnosticCode.KMX034,
            location = target.copy(member = paramName),
            message = "injected converter ${converter.substringAfterLast('.')} (a class with " +
                "dependencies) can only be used from a @Mapper (contract mode).",
            fix = "declare the mapping in a @Mapper interface, or make the converter a stateless object.",
        )

    /** KMX035: converter-class inyectado sin `@Component` con componentModel = SPRING. */
    public fun injectedConverterNotComponent(mapper: MLocation, converter: String): Diagnostic =
        Diagnostic(
            code = DiagnosticCode.KMX035,
            location = mapper,
            message = "injected converter ${converter.substringAfterLast('.')} must be a " +
                "@Component to be injected with componentModel = SPRING.",
            fix = "annotate ${converter.substringAfterLast('.')} with @Component, or use componentModel = NONE.",
        )

    /** KMX033: `onNull = TYPE_DEFAULT` sobre un tipo fuera de la lista cerrada. */
    public fun orEmptyNotCollection(target: MLocation, paramName: String, type: String): Diagnostic =
        Diagnostic(
            code = DiagnosticCode.KMX033,
            location = target.copy(member = paramName),
            message = "onNull = TYPE_DEFAULT applies only to List/Set/Map and " +
                "Int/Long/Short/Byte/Double/Float/Boolean/String, not $type.",
            fix = "use onNull = LITERAL/THROW for this field, or remove the strategy.",
        )

    /** KMX036: `target` requerido en sede de método, prohibido en sede de campo. */
    public fun mapFieldBadAddressing(location: MLocation, methodSite: Boolean): Diagnostic =
        Diagnostic(
            code = DiagnosticCode.KMX036,
            location = location,
            message = if (methodSite) {
                "@MapField on a mapper method requires target = \"...\"."
            } else {
                "@MapField on a field must not set target: the annotated field IS the destination."
            },
            fix = if (methodSite) "name the destination field: @MapField(target = \"...\", ...)."
            else "remove target = \"...\" (or move the annotation to the mapper method).",
        )

    /** KMX037: más de una `@MapField` para el mismo campo destino. */
    public fun mapFieldDuplicate(location: MLocation, fieldName: String): Diagnostic =
        Diagnostic(
            code = DiagnosticCode.KMX037,
            location = location.copy(member = fieldName),
            message = "multiple @MapField declarations for '$fieldName'.",
            fix = "merge them into a single @MapField: one annotation per destination field.",
        )

    /** KMX038: `onNull = LITERAL` sin `default`. */
    public fun literalRequiresDefault(location: MLocation, fieldName: String): Diagnostic =
        Diagnostic(
            code = DiagnosticCode.KMX038,
            location = location.copy(member = fieldName),
            message = "onNull = LITERAL requires a default literal.",
            fix = "set default = \"...\", or choose another OnNull strategy.",
        )

    /** KMX039, WARNING: `default` seteado con `onNull != LITERAL` — se ignora. */
    public fun defaultIgnored(location: MLocation, fieldName: String): Diagnostic =
        Diagnostic(
            code = DiagnosticCode.KMX039,
            location = location.copy(member = fieldName),
            message = "default is ignored when onNull != LITERAL.",
            fix = "remove default, or set onNull = OnNull.LITERAL.",
            severity = Severity.WARNING,
        )

    /** KMX041: `LITERAL`/`UNSAFE` como política de NIVEL — son per-field por diseño. */
    public fun fieldOnlyPolicy(location: MLocation, value: String): Diagnostic =
        Diagnostic(
            code = DiagnosticCode.KMX041,
            location = location,
            message = "onNull = $value is not a mapper/mapping-level policy: a level cannot carry " +
                "a literal or a blanket !!.",
            fix = "move it to @MapField(onNull = $value) on the target field, or use " +
                "STRICT/THROW/TYPE_DEFAULT/TARGET_DEFAULT at this level.",
        )

    /** KMX045: `@InverseOf` mal direccionado o con forma inválida. */
    public fun invalidInverse(method: MLocation, detail: String): Diagnostic =
        Diagnostic(
            code = DiagnosticCode.KMX045,
            location = method,
            message = "invalid @InverseOf: $detail.",
            fix = "point @InverseOf at a single-parameter mapping method with the exact " +
                "inverse signature, and keep the inverse method free of its own @MapField.",
        )

    /** KMX044: el `config` referenciado no es un profile `@MapperConfig` válido. */
    public fun invalidMapperConfig(mapper: MLocation, profile: String, detail: String): Diagnostic =
        Diagnostic(
            code = DiagnosticCode.KMX044,
            location = mapper,
            message = "config ${profile.substringAfterLast('.')} $detail.",
            fix = "annotate ${profile.substringAfterLast('.')} with @MapperConfig and keep it " +
                "free of abstract methods (a profile carries settings, not mappings).",
        )

    /** KMX042: campo ignorado sin default de constructor — no hay forma de omitirlo. */
    public fun cannotIgnore(target: MLocation, paramName: String): Diagnostic =
        Diagnostic(
            code = DiagnosticCode.KMX042,
            location = target.copy(member = paramName),
            message = "'$paramName' is ignored but has no constructor default: the argument " +
                "cannot be omitted.",
            fix = "add a default value to the target parameter (for nullables: `= null`), " +
                "or remove the ignore.",
        )

    /** KMX043: `ignore = true` junto a otros aspectos — config muerta. */
    public fun ignoreConflictsWithAspects(location: MLocation, fieldName: String): Diagnostic =
        Diagnostic(
            code = DiagnosticCode.KMX043,
            location = location.copy(member = fieldName),
            message = "ignore = true makes the other @MapField aspects dead configuration.",
            fix = "remove from/converter/onNull/default, or remove ignore = true.",
        )

    /** KMX040: `TARGET_DEFAULT` sin default de constructor utilizable. */
    public fun targetDefaultUnavailable(target: MLocation, paramName: String): Diagnostic =
        Diagnostic(
            code = DiagnosticCode.KMX040,
            location = target.copy(member = paramName),
            message = "onNull = TARGET_DEFAULT but '$paramName' has no usable constructor default " +
                "(a default value and identical types are required).",
            fix = "add a default value to the target parameter, or choose another OnNull strategy.",
        )

    /** KMX032: config por campo y por método para el mismo destino — gana el método. */
    public fun duplicateFieldConfig(target: MLocation, field: String): Diagnostic =
        Diagnostic(
            code = DiagnosticCode.KMX032,
            location = target.copy(member = field),
            message = "configuration for '$field' is declared both on the field and on the @Mapper method.",
            fix = "keep it in one place; the method-level configuration wins.",
            severity = Severity.WARNING,
        )

    /** KMX030: el framework del componentModel no está en el classpath de compilación. */
    public fun frameworkMissing(mapper: MLocation, model: String, dependency: String): Diagnostic =
        Diagnostic(
            code = DiagnosticCode.KMX030,
            location = mapper,
            message = "componentModel $model requires $dependency on the classpath.",
            fix = "add the dependency, or use componentModel NONE.",
        )

    /** KMX028: asimetría que rompe la invertibilidad. [fixHint] antecede a la salida estándar. */
    public fun notInvertible(
        target: MLocation,
        paramName: String,
        detail: String,
        fixHint: String,
    ): Diagnostic =
        Diagnostic(
            code = DiagnosticCode.KMX028,
            location = target.copy(member = paramName),
            message = "not invertible: $detail.",
            fix = "$fixHint, or use two one-way @MapTo declarations.",
        )

    /** KMX026: entry del SOURCE sin par. [didYouMean]: candidatos cuando un @MapEntry apunta a la nada. */
    public fun enumEntryWithoutCounterpart(
        sourceEntry: MLocation,
        targetEnum: String,
        didYouMean: List<String> = emptyList(),
    ): Diagnostic =
        Diagnostic(
            code = DiagnosticCode.KMX026,
            location = sourceEntry,
            message = buildString {
                append("has no counterpart in ${targetEnum.substringAfterLast('.')}.")
                if (didYouMean.isNotEmpty()) {
                    append(" Did you mean ${didYouMean.joinToString(" or ") { "'$it'" }}?")
                }
            },
            fix = "add ${targetEnum.substringAfterLast('.')}." +
                "${sourceEntry.qualifiedClassName.substringAfterLast('.')} or declare @MapEntry " +
                "on the source entry.",
        )

    /** KMX047: el fallback de `@MapEntry` en sede de CLASE no existe en el target. */
    public fun enumFallbackMissing(
        sourceEnum: MLocation,
        fallback: String,
        targetEnum: String,
        didYouMean: List<String> = emptyList(),
    ): Diagnostic =
        Diagnostic(
            code = DiagnosticCode.KMX047,
            location = sourceEnum,
            message = buildString {
                append("class-level @MapEntry fallback '$fallback' does not exist in ")
                append("${targetEnum.substringAfterLast('.')}.")
                if (didYouMean.isNotEmpty()) {
                    append(" Did you mean ${didYouMean.joinToString(" or ") { "'$it'" }}?")
                }
            },
            fix = "point the fallback at an existing entry of ${targetEnum.substringAfterLast('.')}.",
        )

    /** KMX046: método de colección sin mapeo del ELEMENTO al cual delegar. */
    public fun collectionMethodUnresolved(
        mapper: MLocation,
        sourceElement: String,
        targetElement: String,
        detail: String? = null,
    ): Diagnostic =
        Diagnostic(
            code = DiagnosticCode.KMX046,
            location = mapper,
            message = "no element mapping to delegate to for $sourceElement -> $targetElement" +
                (detail?.let { " ($it)" } ?: "") + ".",
            fix = "declare an abstract method mapping $sourceElement to $targetElement in this " +
                "mapper, or a @MapTo on the source element.",
        )

    /** Subtipo del TARGET sin par — el `when` sigue exhaustivo sobre el source. */
    public fun targetSubtypeUnmatched(targetSubtype: MLocation, sourceRoot: String): Diagnostic =
        Diagnostic(
            code = DiagnosticCode.KMX023,
            location = targetSubtype,
            message = "has no counterpart in ${sourceRoot.substringAfterLast('.')} " +
                "(the generated when remains exhaustive over the source).",
            fix = "remove the target subtype, or add the source subtype if it was expected.",
            severity = Severity.WARNING,
        )

    public fun deepSealedNesting(subtype: MLocation): Diagnostic =
        Diagnostic(
            code = DiagnosticCode.KMX024,
            location = subtype,
            message = "nested sealed hierarchies are not supported in v1 (one level only).",
            fix = "flatten the hierarchy, or map the nested level with its own @MapTo.",
        )

    /** `@MapFrom(from = ...)` apunta a una propiedad inexistente. [didYouMean] hasta 2 candidatos. */
    public fun renamedSourceMissing(
        target: MLocation,
        paramName: String,
        from: String,
        didYouMean: List<String> = emptyList(),
        on: String? = null,
    ): Diagnostic =
        Diagnostic(
            code = DiagnosticCode.KMX011,
            location = target.copy(member = paramName),
            message = buildString {
                append("source property '$from' does not exist")
                if (on != null) append(" on $on")
                append(".")
                if (didYouMean.isNotEmpty()) {
                    append(" Did you mean ${didYouMean.joinToString(" or ") { "'$it'" }}?")
                }
            },
            fix = "point @MapField(from = \"...\") to an existing source property.",
        )

    /**
     * KMX011, modo contract: `@MapField(target = "...")` sobre un método `@Mapper`
     * nombra un campo que no existe en el tipo de retorno.
     */
    public fun methodTargetMissing(
        method: MLocation,
        targetField: String,
        targetClass: String,
        didYouMean: List<String> = emptyList(),
    ): Diagnostic =
        Diagnostic(
            code = DiagnosticCode.KMX011,
            location = method,
            message = buildString {
                append("target property '$targetField' does not exist in ${targetClass.substringAfterLast('.')}")
                append(".")
                if (didYouMean.isNotEmpty()) {
                    append(" Did you mean ${didYouMean.joinToString(" or ") { "'$it'" }}?")
                }
            },
            fix = "point target = \"...\" to an existing property of the mapper's return type.",
        )

    /** KMX020 quedó redefinido a SINTAXIS malformada (las rutas válidas ya resuelven). */
    public fun malformedPath(target: MLocation, paramName: String, from: String): Diagnostic =
        Diagnostic(
            code = DiagnosticCode.KMX020,
            location = target.copy(member = paramName),
            message = "malformed path '$from': empty segments are not allowed.",
            fix = "use dot-separated property names, e.g. @MapField(from = \"address.city\").",
        )

    /** KMX008: [path] es el camino completo del ciclo, p.ej. `[Person, Address, Person]`. */
    public fun mappingCycle(location: MLocation, path: List<String>): Diagnostic =
        Diagnostic(
            code = DiagnosticCode.KMX008,
            location = location,
            message = "mapping cycle detected: ${path.joinToString(" -> ")}.",
            fix = "break the cycle: map the nested type with a @Converter, or remove one of the declarations.",
        )

    public fun patchTargetNotDataClass(target: MLocation): Diagnostic =
        Diagnostic(
            code = DiagnosticCode.KMX012,
            location = target,
            message = "patch mapping requires the target to be a data class (copy() is needed for immutable update).",
            fix = "make ${target.qualifiedClassName} a data class, or declare a regular mapping that constructs a new instance.",
        )

    public fun noResolvableConstructor(target: MLocation): Diagnostic =
        Diagnostic(
            code = DiagnosticCode.KMX005,
            location = target,
            message = "no resolvable constructor: the primary constructor is not visible (or absent) " +
                "and no @MapConstructor/@MapFactory is declared.",
            fix = "annotate a constructor with @MapConstructor, or a companion/top-level " +
                "function returning ${target.qualifiedClassName.substringAfterLast('.')} with @MapFactory.",
        )

    public fun ambiguousConstruction(target: MLocation, candidates: List<String>): Diagnostic =
        Diagnostic(
            code = DiagnosticCode.KMX006,
            location = target,
            message = "ambiguous construction: ${candidates.size} candidates (${candidates.joinToString()}).",
            fix = "keep exactly one @MapConstructor or @MapFactory; remove the annotation " +
                "from the unwanted candidates.",
        )

    public fun ambiguousMapperName(source: MLocation, functionName: String, targets: List<String>): Diagnostic =
        Diagnostic(
            code = DiagnosticCode.KMX013,
            location = source,
            message = "ambiguous generated function name '$functionName': " +
                "${targets.size} @MapTo targets share it (${targets.joinToString()}).",
            fix = "set an explicit name with @MapTo(..., name = \"...\") on the conflicting declarations.",
        )

    public fun multipleStrategies(target: MLocation, paramName: String): Diagnostic =
        Diagnostic(
            code = DiagnosticCode.KMX016,
            location = target.copy(member = paramName),
            message = "multiple null-handling strategies declared.",
            fix = "declare exactly one strategy: @MapField(onNull = ...) is exclusive by construction.",
        )

    public fun invalidDefaultLiteral(target: MLocation, paramName: String, literal: String, type: String): Diagnostic =
        Diagnostic(
            code = DiagnosticCode.KMX017,
            location = target.copy(member = paramName),
            message = "\"$literal\" is not a valid ${type.substringAfterLast('.')} default.",
            fix = "provide a parseable literal, or use onNull = THROW.",
        )

    public fun unsupportedDefaultType(target: MLocation, paramName: String, type: String): Diagnostic =
        Diagnostic(
            code = DiagnosticCode.KMX017,
            location = target.copy(member = paramName),
            message = "onNull = LITERAL is not supported for $type.",
            fix = "use a @Converter or onNull = THROW.",
        )

    public fun deadStrategy(target: MLocation, paramName: String): Diagnostic =
        Diagnostic(
            code = DiagnosticCode.KMX018,
            location = target.copy(member = paramName),
            message = "null-handling strategy declared but the source is not nullable (dead strategy).",
            fix = "remove the annotation.",
            severity = Severity.WARNING,
        )

    public fun afterFunctionBadSignature(
        mapper: MLocation,
        afterName: String,
        sourceType: String,
        targetType: String,
    ): Diagnostic =
        Diagnostic(
            code = DiagnosticCode.KMX014,
            location = mapper.copy(member = afterName),
            message = "after-function '$afterName' must have signature " +
                "(${sourceType.substringAfterLast('.')}, ${targetType.substringAfterLast('.')}) -> " +
                "${targetType.substringAfterLast('.')}.",
            fix = "declare it as a default method: fun $afterName(source: " +
                "${sourceType.substringAfterLast('.')}, result: ${targetType.substringAfterLast('.')}): " +
                "${targetType.substringAfterLast('.')}.",
        )

    /** La post-función de un método PATCH es `after<Método>(target, patch, result) -> result`. */
    public fun afterApplyBadSignature(
        mapper: MLocation,
        targetType: String,
        patchType: String,
        afterName: String = "afterApply",
    ): Diagnostic =
        Diagnostic(
            code = DiagnosticCode.KMX014,
            location = mapper.copy(member = afterName),
            message = "after-function '$afterName' must have signature " +
                "(${targetType.substringAfterLast('.')}, ${patchType.substringAfterLast('.')}, " +
                "${targetType.substringAfterLast('.')}) -> ${targetType.substringAfterLast('.')}.",
            fix = "declare it as a default method: fun $afterName(target: " +
                "${targetType.substringAfterLast('.')}, patch: ${patchType.substringAfterLast('.')}, " +
                "result: ${targetType.substringAfterLast('.')}): ${targetType.substringAfterLast('.')}.",
        )

    public fun unsupportedMapperShape(mapper: MLocation, detail: String): Diagnostic =
        Diagnostic(
            code = DiagnosticCode.KMX015,
            location = mapper,
            message = "unsupported @Mapper shape: $detail.",
            fix = "declare a plain, non-generic interface that does not extend another @Mapper interface.",
        )

    /**
     * La omisión por fuente ausente no es silenciosa — warning informativo.
     * la política `unmapped` puede escalar la [severity] a ERROR (`IGNORE` no llama aquí).
     */
    public fun targetDefaultFilled(
        target: MLocation,
        paramName: String,
        severity: Severity = Severity.WARNING,
    ): Diagnostic =
        Diagnostic(
            code = DiagnosticCode.KMX021,
            location = target.copy(member = paramName),
            message = "filled by the target default (no matching source property).",
            fix = "add a matching source property if the omission is unintended.",
            severity = severity,
        )

    /** El límite de emisión es K=2 campos con omisión condicional por mecanismo. */
    public fun tooManyOmissibleDefaults(target: MLocation, params: List<String>, limit: Int): Diagnostic =
        Diagnostic(
            code = DiagnosticCode.KMX022,
            location = target,
            message = "${params.size} parameters would need conditional omission " +
                "(${params.joinToString()}); the limit is $limit.",
            fix = "annotate the extra ones with @MapField(onNull = LITERAL/THROW/UNSAFE), " +
                "or make them nullable in the target.",
        )

    /** Expect/actual mapping es no-goal v1. */
    public fun expectDeclarationUnsupported(declaration: MLocation): Diagnostic =
        Diagnostic(
            code = DiagnosticCode.KMX025,
            location = declaration,
            message = "expect declarations cannot be mapped in v1.",
            fix = "annotate the actual class per target, or map a common concrete class.",
        )

    public fun internalError(location: MLocation, detail: String): Diagnostic =
        Diagnostic(
            code = DiagnosticCode.KMX001,
            location = location,
            message = "internal error: $detail.",
            fix = "this is a kmapx bug; please report it with a minimal reproducer.",
        )
}
