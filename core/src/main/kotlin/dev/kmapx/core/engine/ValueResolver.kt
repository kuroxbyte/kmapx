package dev.kmapx.core.engine

import dev.kmapx.core.diagnostics.Diagnostic
import dev.kmapx.core.diagnostics.Diagnostics
import dev.kmapx.core.model.MConstructorParam
import dev.kmapx.core.model.MNullStrategy
import dev.kmapx.core.model.MProperty
import dev.kmapx.core.model.MType
import dev.kmapx.core.model.TypeKind
import dev.kmapx.core.plan.ConverterRef
import dev.kmapx.core.plan.MapperRef
import dev.kmapx.core.plan.StrategyOutcome
import dev.kmapx.core.plan.ValueSource
import dev.kmapx.core.plan.ref

/**
 * La CADENA de resolución de un valor — única responsabilidad del `MType` fuente al
 * `MType` target. Orden: nulabilidad primero → converter calificado (paso 0) →
 * `@Converter` global → value class → idéntico → colecciones elemento a elemento
 * → mapper declarado. Nada más: KMX007/KMX004 en el fall-through.
 *
 * No conoce constructores, sedes ni el frontend: recibe (propiedad fuente, parámetro target, Ctx)
 * y devuelve un [ValueSource] o `null` (con los diagnósticos ya en `ctx.diagnostics`).
 */
internal class ValueResolver {

    fun resolve(
        property: MProperty,
        param: MConstructorParam,
        ctx: Ctx,
        nullableSegment: String? = null,
    ): ValueSource? {
        val s = property.type
        val t = param.type

        // Nullability matrix: decide the null handling first, structure second.
        if (param.strategies.size > 1) {
            ctx.diagnostics += Diagnostics.multipleStrategies(ctx.targetLocation, param.name)
            return null
        }
        val explicitStrategy = param.strategies.singleOrNull()
        val nullViolation = s.nullable && !t.nullable
        // La estrategia puede aplicar al ELEMENTO de la colección — no es muerta en ese caso.
        val elementMayViolate = isCollection(s) && isCollectionLike(t) &&
            s.typeArgs.any { it.nullable }

        // La estrategia MUERTA se juzga solo sobre la anotación EXPLÍCITA (las políticas de nivel no cuentan).
        if (explicitStrategy != null && !nullViolation && !elementMayViolate) {
            ctx.diagnostics += Diagnostics.deadStrategy(ctx.targetLocation, param.name)
        }

        // Precedencia: estrategia explícita del campo; `STRICT` explícito CORTA la
        // cascada (vuelve a KMX003); sin estrategia, la violación camina la cadena de políticas.
        val strategy = when {
            explicitStrategy is MNullStrategy.Strict -> null
            explicitStrategy != null -> explicitStrategy
            nullViolation -> cascadeStrategy(s, t, param, ctx)
            else -> null
        }

        if (nullViolation && strategy != null) {
            // La estrategia aplica SOBRE EL RESULTADO de la conversión estructural
            // (unwrap/wrap nullable) cuando el par es de value class; si no hay conversión
            // conocida, KMX004.
            val declaredForPair = ctx.declaredMappings[s.qualifiedName to t.qualifiedName]
            val inner: ValueSource? = when {
                s.sameTypeAs(t) -> null
                s.kind == TypeKind.VALUE_CLASS && s.underlying?.sameTypeAs(t) == true ->
                    ValueSource.UnwrapValueClass(ref(property.name), safeCall = true)
                t.kind == TypeKind.VALUE_CLASS && t.underlying?.sameTypeAs(s) == true ->
                    ValueSource.WrapValueClass(ref(property.name), t, safeCall = true)
                // `address?.toAddressDto() ?: throw ...` — estrategia sobre el resultado.
                declaredForPair != null ->
                    ValueSource.ViaMapper(
                        ref(property.name), MapperRef.GeneratedExtension(declaredForPair), safeCall = true,
                    )
                else -> {
                    // Los implícitos también componen bajo estrategia (`x?.toLong() ?: throw`).
                    val implicit = implicitConversion(s, t, property.name, ctx, safeCall = true)
                    if (implicit == null) {
                        ctx.diagnostics += incompatibleOrMissingNested(s, t, param.name, ctx)
                        return null
                    }
                    implicit
                }
            }
            return when (strategy) {
                // Inalcanzable: `Strict` explícito ya se filtró arriba (cae a KMX003); rama por exhaustividad.
                MNullStrategy.Strict -> null
                is MNullStrategy.WithDefault -> when (val parsed = DefaultLiterals.render(strategy.literal, t)) {
                    is DefaultLiterals.Parsed.Rendered ->
                        if (inner == null) ValueSource.NullFallbackToValue(ref(property.name), parsed.code)
                        else ValueSource.NullStrategyOver(inner, StrategyOutcome.Fallback(parsed.code))
                    DefaultLiterals.Parsed.InvalidLiteral -> {
                        ctx.diagnostics += Diagnostics.invalidDefaultLiteral(
                            ctx.targetLocation, param.name, strategy.literal, t.qualifiedName,
                        )
                        null
                    }
                    DefaultLiterals.Parsed.UnsupportedType -> {
                        ctx.diagnostics += Diagnostics.unsupportedDefaultType(
                            ctx.targetLocation, param.name, t.qualifiedName,
                        )
                        null
                    }
                }
                MNullStrategy.OrThrow -> {
                    val message = "${param.name} must not be null mapping ${ctx.mappingPair}"
                    if (inner == null) ValueSource.NullOrThrow(ref(property.name), message)
                    else ValueSource.NullStrategyOver(inner, StrategyOutcome.Throw(message))
                }
                MNullStrategy.AllowUnsafe ->
                    if (inner == null) ValueSource.NullUnsafe(ref(property.name))
                    else ValueSource.NullStrategyOver(inner, StrategyOutcome.Unsafe)
                MNullStrategy.OrEmpty -> {
                    // TYPE_DEFAULT: el cero/vacío del tipo, de la LISTA CERRADA —
                    // colección idéntica → `?: emptyXxx()`; escalar → `?: 0/""/false…`.
                    val literal = typeDefaultLiteralFor(s, t)?.takeIf { inner == null }
                    if (literal == null) {
                        ctx.diagnostics += Diagnostics.orEmptyNotCollection(
                            ctx.targetLocation, param.name, t.qualifiedName,
                        )
                        null
                    } else {
                        ValueSource.NullFallbackToValue(ref(property.name), literal)
                    }
                }
                MNullStrategy.TargetDefault -> {
                    // Omite el argumento — exige default y tipos iguales
                    // (inner == null ⟺ s ≡ t módulo nulabilidad; la omisión no compone conversiones).
                    if (inner == null && param.hasDefault) {
                        ValueSource.NullFallbackToDefault(ref(property.name))
                    } else {
                        ctx.diagnostics += Diagnostics.targetDefaultUnavailable(ctx.targetLocation, param.name)
                        null
                    }
                }
            }
        }

        if (nullViolation) {
            // Sin salida declarada y cascada agotada: el error pedagógico KMX003 (la salida
            // TARGET_DEFAULT se menciona solo si el default existe).
            ctx.diagnostics += Diagnostics.nullabilityViolation(
                ctx.targetLocation, param.name,
                sourceClass = ctx.mappingPair.substringBefore(" ->"),
                sourceProperty = property.name,
                defaultAvailable = param.hasDefault,
                nullableSegment = nullableSegment,
            )
            return null
        }

        // PASO 0: el converter calificado — override explícito por campo, GANA sobre el
        // @Converter global (paso 1) y el mapper declarado (paso 2). Para pares de contenedor
        // aplica al ELEMENTO (resolveElements lo propaga vía resolveSide), no al contenedor.
        param.useConverter?.let { uc ->
            val containerPair = isCollection(s) && isCollectionLike(t)
            if (!containerPair) {
                if (uc.fromType == null || uc.toType == null) {
                    ctx.diagnostics += Diagnostics.notAConverter(
                        ctx.targetLocation, param.name, uc.objectQualifiedName,
                    )
                    return null
                }
                if (!uc.fromType.sameTypeAs(s.asNonNullable()) || !uc.toType.sameTypeAs(t.asNonNullable())) {
                    ctx.diagnostics += Diagnostics.converterTypeMismatch(
                        ctx.targetLocation, param.name, uc.objectQualifiedName,
                        declared = "${uc.fromType.simpleName} -> ${uc.toType.simpleName}",
                        required = "${s.asNonNullable().simpleName} -> ${t.asNonNullable().simpleName}",
                    )
                    return null
                }
                if (s.sameTypeAs(t)) {
                    // A == B: el converter no hace falta — warning, pero respetamos la intención.
                    ctx.diagnostics += Diagnostics.unnecessaryConverter(
                        ctx.targetLocation, param.name, uc.objectQualifiedName,
                    )
                }
                // Un converter-class (bean) solo se puede inyectar en modo B.
                if (!uc.isObject && !ctx.allowInjectedConverters) {
                    ctx.diagnostics += Diagnostics.injectedConverterInModeA(
                        ctx.targetLocation, param.name, uc.objectQualifiedName,
                    )
                    return null
                }
                return ValueSource.ViaQualifiedConverter(
                    source = ref(property.name),
                    converterObject = uc.objectQualifiedName,
                    safeCall = s.nullable,
                    injected = !uc.isObject,
                )
            }
        }

        // Regla 1: un @Converter del usuario GANA sobre toda otra resolución —
        // incluida la implícita y los mappers declarados. `A?→B?` se envuelve con `?.let`;
        // `A?→B` ya cayó arriba en KMX003 (la estrategia de nulabilidad se exige sobre el resultado).
        if (!s.nullable || t.nullable) {
            val candidates = ctx.converters[s.qualifiedName to t.qualifiedName].orEmpty()
            if (candidates.size > 1) {
                ctx.diagnostics += Diagnostics.ambiguousConverters(
                    ctx.targetLocation, param.name, s.qualifiedName, t.qualifiedName,
                    candidates.map { it.substringAfterLast('.') },
                )
                return null
            }
            candidates.singleOrNull()?.let { fqn ->
                return ValueSource.ViaConverter(
                    source = ref(property.name),
                    converter = ConverterRef(fqn),
                    safeCall = s.nullable,
                )
            }
        }

        // Value class transparency: unwrap/wrap, con `?.` si la fuente es nullable
        // (a esta altura la violación de nulabilidad ya se resolvió: s nullable ⇒ t nullable).
        val unwrap = s.kind == TypeKind.VALUE_CLASS && s.underlying?.sameTypeAs(t) == true
        if (unwrap) return ValueSource.UnwrapValueClass(ref(property.name), safeCall = s.nullable)

        val wrap = t.kind == TypeKind.VALUE_CLASS && t.underlying?.sameTypeAs(s) == true
        if (wrap) return ValueSource.WrapValueClass(ref(property.name), t, safeCall = s.nullable)

        // Silent cells: T->T, T->T?, T?->T? (incluye genéricos idénticos).
        // nullViolation ya se resolvió arriba por completo (estrategia, target default o KMX003).
        if (s.sameTypeAs(t)) return ValueSource.Direct(ref(property.name))

        // Conversiones implícitas de la LISTA CERRADA — widening numérico
        // (siempre) y estándar (opt-in). Antes de colecciones: los ELEMENTOS las heredan por
        // la recursión de resolveSide. `s nullable ⇒ t nullable` a esta altura → `?.`.
        if (!s.nullable || t.nullable) {
            implicitConversion(s, t, property.name, ctx, safeCall = s.nullable)?.let { return it }
        }

        // Colecciones: elemento a elemento, UNA pasada.
        if (!s.nullable || t.nullable) {
            when (val mapped = resolveElements(property, param, ctx)) {
                is ElementResult.Resolved -> return mapped.value
                ElementResult.Failed -> return null
                ElementResult.NotApplicable -> Unit
            }
        }

        // Un mapper declarado del par exacto resuelve en CUALQUIER posición de
        // valor, siempre por referencia a la función nombrada; fuente nullable compone con `?.`
        // (la violación de nulabilidad ya se resolvió arriba: s nullable ⇒ t nullable).
        ctx.declaredMappings[s.qualifiedName to t.qualifiedName]?.let { fn ->
            return ValueSource.ViaMapper(
                ref(property.name), MapperRef.GeneratedExtension(fn), safeCall = s.nullable,
            )
        }

        ctx.diagnostics += incompatibleOrMissingNested(s, t, param.name, ctx)
        return null
    }

    /**
     * El par por las tablas de [ImplicitConversions]: widening siempre;
     * estándar solo con `ctx.stdConverters`. null = el par no es implícito (sigue la cadena).
     */
    private fun implicitConversion(
        s: MType,
        t: MType,
        sourceName: String,
        ctx: Ctx,
        safeCall: Boolean,
    ): ValueSource? {
        ImplicitConversions.widening(s, t)?.let { fn ->
            return ValueSource.NumericWidening(ref(sourceName), fn, safeCall = safeCall)
        }
        if (ctx.stdConverters) {
            ImplicitConversions.standard(s, t)?.let { template ->
                return ValueSource.BuiltinConversion(ref(sourceName), template, safeCall = safeCall)
            }
        }
        return null
    }

    /**
     * El fall-through distingue: par de tipos MAPEABLES por declaración (clases, enums,
     * sealed, objects) sin mapper declarado → KMX007 ("declara el mapeo"); todo lo demás →
     * KMX004 ("registra un converter").
     */
    private fun incompatibleOrMissingNested(
        s: MType,
        t: MType,
        paramName: String,
        ctx: Ctx,
    ): Diagnostic {
        val mappableKinds = setOf(
            TypeKind.DATA_CLASS, TypeKind.REGULAR_CLASS, TypeKind.ENUM,
            TypeKind.SEALED_CLASS, TypeKind.SEALED_INTERFACE, TypeKind.OBJECT,
        )
        // La stdlib no es anotable por el usuario: `Int→Long`, `enum→String`… son territorio
        // de @Converter (KMX004), no de "declara el mapeo" (KMX007).
        fun mappable(x: MType) = x.kind in mappableKinds && !x.qualifiedName.startsWith("kotlin.")
        return if (mappable(s) && mappable(t)) {
            Diagnostics.noNestedMapping(ctx.targetLocation, paramName, s.simpleName, t.simpleName)
        } else {
            Diagnostics.incompatibleTypes(ctx.targetLocation, paramName, s.qualifiedName, t.qualifiedName)
        }
    }

    private sealed interface ElementResult {
        data class Resolved(val value: ValueSource) : ElementResult

        /** Par de colecciones válido pero el elemento no resuelve: diagnósticos ya agregados. */
        data object Failed : ElementResult
        data object NotApplicable : ElementResult
    }

    /**
     * Camina la cadena de políticas de nivel ante una violación `T? -> T` sin estrategia
     * de campo. `STRICT` corta (→ KMX003); las condicionales aplican SOLO donde pueden y si no,
     * la violación cae al siguiente nivel (por eso aquí se pre-validan: la política nunca produce
     * KMX033/KMX040 — esos son errores de la declaración EXPLÍCITA por campo).
     */
    private fun cascadeStrategy(s: MType, t: MType, param: MConstructorParam, ctx: Ctx): MNullStrategy? {
        for (policy in ctx.nullPolicies) {
            when (policy) {
                NullPolicy.STRICT -> return null
                NullPolicy.OR_THROW -> return MNullStrategy.OrThrow
                NullPolicy.TYPE_DEFAULT ->
                    if (typeDefaultLiteralFor(s, t) != null && s.sameTypeAs(t)) {
                        return MNullStrategy.OrEmpty
                    }
                NullPolicy.TARGET_DEFAULT ->
                    if (param.hasDefault && s.sameTypeAs(t)) return MNullStrategy.TargetDefault
            }
        }
        return null
    }

    /**
     * El "cero/vacío" de `TYPE_DEFAULT`: colección idéntica → `emptyXxx()`; escalar de la
     * LISTA CERRADA → su cero. null = el tipo no tiene default de tipo (KMX033 si fue explícito;
     * la política de nivel simplemente cae al siguiente).
     */
    private fun typeDefaultLiteralFor(s: MType, t: MType): String? =
        emptyLiteralFor(t)?.takeIf { isCollection(s) && isCollectionLike(t) } ?: scalarZeroFor(t)

    /** Lista CERRADA de ceros escalares (no se infiere nada: o está aquí, o no hay default). */
    private fun scalarZeroFor(t: MType): String? = when (t.qualifiedName) {
        "kotlin.Int" -> "0"
        "kotlin.Long" -> "0L"
        "kotlin.Short" -> "0"
        "kotlin.Byte" -> "0"
        "kotlin.Double" -> "0.0"
        "kotlin.Float" -> "0.0f"
        "kotlin.Boolean" -> "false"
        "kotlin.String" -> "\"\""
        else -> null
    }

    /** Literal de colección vacía por tipo target (política `TYPE_DEFAULT`). */
    private fun emptyLiteralFor(t: MType): String? = when (t.kind) {
        TypeKind.COLLECTION_LIST -> "emptyList()"
        TypeKind.COLLECTION_SET -> "emptySet()"
        TypeKind.COLLECTION_MAP -> "emptyMap()"
        else -> null
    }

    // Contenedores reconocidos como FUENTE iterable (incluye Iterable/Sequence).
    fun isCollection(t: MType): Boolean =
        t.kind == TypeKind.COLLECTION_LIST || t.kind == TypeKind.COLLECTION_SET ||
            t.kind == TypeKind.COLLECTION_ARRAY || t.kind == TypeKind.RESULT ||
            t.kind == TypeKind.COLLECTION_MAP ||
            t.kind == TypeKind.COLLECTION_ITERABLE || t.kind == TypeKind.COLLECTION_SEQUENCE

    // Contenedores válidos como TARGET (Iterable/Sequence NO lo son: solo fuente).
    private fun isCollectionLike(t: MType): Boolean =
        t.kind == TypeKind.COLLECTION_LIST || t.kind == TypeKind.COLLECTION_SET ||
            t.kind == TypeKind.COLLECTION_ARRAY || t.kind == TypeKind.RESULT ||
            t.kind == TypeKind.COLLECTION_MAP ||
            t.qualifiedName == "kotlin.collections.Collection" ||
            t.qualifiedName == "kotlin.collections.Iterable"

    /** Resuelve UN lado (elemento/clave/valor) por la cadena estándar, con refs [refName]. */
    private fun resolveSide(
        refName: String,
        sourceSide: MType,
        targetSide: MType,
        param: MConstructorParam,
        ctx: Ctx,
    ): ValueSource? {
        // Las estrategias de nulabilidad del parámetro se propagan SOLO adonde está la violación:
        val strategies =
            if (sourceSide.nullable && !targetSide.nullable) param.strategies else emptyList()
        // El converter calificado del contenedor aplica al ELEMENTO (`Converts<A,B>` tipa el
        // elemento, no la colección). Se propaga a los lados que NO son clave de un Map.
        val useConverter = if (refName == "k") null else param.useConverter
        return resolve(
            property = MProperty(refName, sourceSide),
            param = MConstructorParam(
                param.name, targetSide, strategies = strategies, useConverter = useConverter,
            ),
            ctx = ctx,
        )
    }

    /**
     * Contenedores compatibles: List→List, Set→Set, Array→Array, Result→Result,
     * Map→Map, y List/Set→Collection/Iterable. Cruces de contenedor (List→Set, Map→List…)
     * NO son implícitos → caen a KMX004 (la lista es cerrada por diseño).
     */
    private fun resolveElements(property: MProperty, param: MConstructorParam, ctx: Ctx): ElementResult {
        val s = property.type
        val t = param.type
        if (!isCollection(s) || !isCollectionLike(t)) return ElementResult.NotApplicable

        // Map→Map: clave y valor cada uno por la cadena (K invariante, V covariante).
        if (s.kind == TypeKind.COLLECTION_MAP || t.kind == TypeKind.COLLECTION_MAP) {
            if (s.kind != t.kind) return ElementResult.NotApplicable
            val (sK, sV) = s.typeArgs.takeIf { it.size == 2 } ?: return ElementResult.NotApplicable
            val (tK, tV) = t.typeArgs.takeIf { it.size == 2 } ?: return ElementResult.NotApplicable

            // K es INVARIANTE (exacto, nulabilidad incluida); V es covariante (T→T? asignable):
            val keyIdentical = sK.sameTypeAs(tK) && sK.nullable == tK.nullable
            val valueAssignable = sV.sameTypeAs(tV) && (!sV.nullable || tV.nullable)
            if (keyIdentical && valueAssignable) {
                return ElementResult.Resolved(ValueSource.Direct(ref(property.name)))
            }
            val key = if (keyIdentical) null
            else resolveSide("k", sK, tK, param, ctx) ?: return ElementResult.Failed
            val value = if (valueAssignable) null
            else resolveSide("v", sV, tV, param, ctx) ?: return ElementResult.Failed
            return ElementResult.Resolved(ValueSource.MapEntries(ref(property.name), key, value))
        }

        // List/Set/Array/Result exigen mismo contenedor; List y los targets Collection/Iterable
        // aceptan ADEMÁS fuentes Iterable/Sequence (materializan con `.map{}` [+ `.toList()`]).
        val iterableSources = setOf(
            TypeKind.COLLECTION_LIST, TypeKind.COLLECTION_ITERABLE, TypeKind.COLLECTION_SEQUENCE,
        )
        val containerOk = when (t.kind) {
            TypeKind.COLLECTION_LIST -> s.kind in iterableSources
            TypeKind.COLLECTION_SET -> s.kind == TypeKind.COLLECTION_SET
            TypeKind.COLLECTION_ARRAY -> s.kind == TypeKind.COLLECTION_ARRAY
            TypeKind.RESULT -> s.kind == TypeKind.RESULT
            // Collection/Iterable target: List/Set (subtipos) e iterables/sequences; map{} produce List.
            else -> s.kind in iterableSources || s.kind == TypeKind.COLLECTION_SET
        }
        if (!containerOk) return ElementResult.NotApplicable

        val sourceElement = s.typeArgs.singleOrNull() ?: return ElementResult.NotApplicable
        val targetElement = t.typeArgs.singleOrNull() ?: return ElementResult.NotApplicable

        // Passthrough directo SOLO si el CONTENEDOR fuente ya es asignable al target (List→List,
        // Set→Set, o List/Set → Collection/Iterable). Iterable/Sequence SIEMPRE materializan.
        val directContainer = when (t.kind) {
            TypeKind.COLLECTION_LIST -> s.kind == TypeKind.COLLECTION_LIST
            TypeKind.COLLECTION_SET -> s.kind == TypeKind.COLLECTION_SET
            TypeKind.COLLECTION_ARRAY -> s.kind == TypeKind.COLLECTION_ARRAY
            TypeKind.RESULT -> s.kind == TypeKind.RESULT
            else -> s.kind == TypeKind.COLLECTION_LIST || s.kind == TypeKind.COLLECTION_SET
        }
        // Elemento asignable tal cual (idéntico o T→T?): referencia directa, cero copias.
        // Array es INVARIANTE: exige identidad exacta (Array<String> NO es Array<String?>).
        val elementAssignable = if (t.kind == TypeKind.COLLECTION_ARRAY) {
            sourceElement.sameTypeAs(targetElement) && sourceElement.nullable == targetElement.nullable
        } else {
            sourceElement.sameTypeAs(targetElement) && (!sourceElement.nullable || targetElement.nullable)
        }
        if (directContainer && elementAssignable) {
            return ElementResult.Resolved(ValueSource.Direct(ref(property.name)))
        }

        val elementValue = resolveSide("it", sourceElement, targetElement, param, ctx)
            ?: return ElementResult.Failed

        val into = when (t.kind) {
            TypeKind.COLLECTION_SET -> TypeKind.COLLECTION_SET
            TypeKind.COLLECTION_ARRAY -> TypeKind.COLLECTION_ARRAY
            TypeKind.RESULT -> TypeKind.RESULT
            else -> TypeKind.COLLECTION_LIST
        }
        return ElementResult.Resolved(
            ValueSource.MapElements(
                ref(property.name), elementValue, into,
                lazySource = s.kind == TypeKind.COLLECTION_SEQUENCE,
            ),
        )
    }
}
