package dev.kmapx.ksp.contract

import dev.kmapx.ksp.Ann
import dev.kmapx.ksp.AnnotationReader
import dev.kmapx.ksp.FrontendContext
import dev.kmapx.ksp.IgnoreAudit
import dev.kmapx.ksp.KspTranslator
import dev.kmapx.ksp.hasError
import dev.kmapx.ksp.qualifiedName
import dev.kmapx.ksp.stringArg
import dev.kmapx.ksp.withIgnored
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSValueParameter
import dev.kmapx.core.diagnostics.Diagnostics
import dev.kmapx.core.diagnostics.MLocation
import dev.kmapx.core.diagnostics.Severity
import dev.kmapx.core.diagnostics.Suggestions
import dev.kmapx.core.model.MClass
import dev.kmapx.core.model.MProperty
import dev.kmapx.core.model.MType
import dev.kmapx.core.model.TypeKind
import dev.kmapx.core.plan.ElementCall
import dev.kmapx.core.plan.Emission
import dev.kmapx.core.plan.MParam
import dev.kmapx.core.plan.MapperMethod
import dev.kmapx.core.plan.MethodBody

/**
 * La FORMA de un método abstracto de `@Mapper` elige su semántica (Strategy):
 * `@InverseOf` explícito > patch estructural (`(T, P): T`) > mapping. El clasificador es la
 * única pieza que conoce las tres reglas; cada resolver conoce solo la suya.
 */
internal sealed interface MethodShape {
    /** Método anotado `@InverseOf` — [forwardName] vacío = auto-detección. */
    data class Inverse(val forwardName: String) : MethodShape

    /** `fun x(target: T, patch: P): T` — retorno == tipo del PRIMER parámetro, aridad 2. */
    data object Patch : MethodShape

    /** Retorno == primer parámetro con aridad ≠ 2: forma de patch inválida, no se adivina. */
    data object InvalidPatchArity : MethodShape

    /** `fun toDtos(items: List<A>): List<B>` — contenedor a contenedor, 1 parámetro. */
    data object CollectionMapping : MethodShape

    /** Mapping normal — primer parámetro = source, extras = fuentes suplementarias. */
    data object Mapping : MethodShape

    companion object {
        fun of(
            method: KSFunctionDeclaration,
            sourceDecl: KSClassDeclaration,
            returnDecl: KSClassDeclaration,
            sourceType: MType,
            returnType: MType,
        ): MethodShape {
            method.annotations.firstOrNull { it.qualifiedName() == Ann.INVERSE_OF }?.let { ann ->
                return Inverse(ann.stringArg("value").orEmpty())
            }
            // Contenedor→contenedor con 1 parámetro es un método de COLECCIÓN — se clasifica
            // ANTES que patch: comparar `List == List` por nombre calificado daría falsos
            // InvalidPatchArity. La validez del PAR la juzga el resolver (KMX046 con detalle).
            if (method.parameters.size == 1 && sourceType.isContainerKind() && returnType.isContainerKind()) {
                return CollectionMapping
            }
            val returnsFirstParam =
                returnDecl.qualifiedName?.asString() == sourceDecl.qualifiedName?.asString()
            return when {
                returnsFirstParam && method.parameters.size == 2 -> Patch
                returnsFirstParam -> InvalidPatchArity
                else -> Mapping
            }
        }

        private fun MType.isContainerKind(): Boolean = kind in setOf(
            TypeKind.COLLECTION_LIST, TypeKind.COLLECTION_SET, TypeKind.COLLECTION_MAP,
            TypeKind.COLLECTION_ITERABLE, TypeKind.COLLECTION_SEQUENCE, TypeKind.COLLECTION_ARRAY,
        )
    }
}

/**
 * Estado compartido de la resolución de métodos de UNA interfaz `@Mapper`: los tres resolvers
 * ([MappingMethodResolver], [PatchMethodResolver], [InverseMethodResolver]) lo reciben en vez de
 * arrastrar listas de parámetros. También aloja los helpers comunes: la post-función
 * y la fusión de config por método.
 */
internal class MapperMethodContext(
    val ctx: FrontendContext,
    val mapper: KSClassDeclaration,
    val location: MLocation,
    val abstractMethods: List<KSFunctionDeclaration>,
    val defaultFunctions: List<KSFunctionDeclaration>,
    /** Métodos del `@Mapper(inheritFrom)` por nombre — su config es la base heredada. */
    val baseMethods: Map<String, KSFunctionDeclaration>,
    val optIns: AnnotationReader.MapperOptIns,
    /** Acumulador de la lista `ignore` de interfaz — se valida al cerrar el loop. */
    val ignoreAudit: IgnoreAudit,
) {
    /** Cascada de niveles — mapper, profile y global, en ese orden. */
    val nullPolicies: List<dev.kmapx.core.engine.NullPolicy> get() = optIns.onNull + ctx.config.onNull
    val useSerialNames: Boolean get() = ctx.config.useSerialNames || optIns.useSerialNames

    /** Conversiones estándar opt-in — aditivo (mapper OR profile OR global). */
    val stdConverters: Boolean get() = ctx.config.stdConverters || optIns.stdConverters

    /** Cascada mapper > profile > global (el primer no-INHERIT gana). */
    val unmapped: dev.kmapx.core.engine.UnmappedPolicy get() = optIns.unmapped ?: ctx.config.unmapped

    /** Sede de método: base heredada + propia; la propia gana por campo destino. */
    fun mergedMethodConfig(method: KSFunctionDeclaration): Map<String, KspTranslator.MethodFieldConfig> {
        val base = baseMethods[method.simpleName.asString()]
            ?.let { ctx.translator.methodFieldConfig(it) } ?: emptyMap()
        return base + ctx.translator.methodFieldConfig(method)
    }

    /**
     * Busca la post-función `after<Método>` entre los métodos default y valida su
     * firma EXACTA ([paramQns] en orden + retorno [returnQn]). null = no declarada;
     * si existe, (nombre, bienFormada) — el llamador reporta el diagnóstico de su forma.
     */
    fun afterCandidate(
        methodName: String,
        paramQns: List<String?>,
        returnQn: String?,
    ): Pair<String, Boolean>? {
        val afterName = "after" + methodName.replaceFirstChar { it.uppercaseChar() }
        val candidates = defaultFunctions.filter { it.simpleName.asString() == afterName }
        if (candidates.isEmpty()) return null
        val wellFormed = candidates.any { fn ->
            fn.parameters.size == paramQns.size &&
                fn.parameters.map { it.type.resolve().declaration.qualifiedName?.asString() } == paramQns &&
                fn.returnType?.resolve()?.declaration?.qualifiedName?.asString() == returnQn
        }
        return afterName to wellFormed
    }

    /**
     * Funde la config por método (`@MapField(target = "...")`) sobre el
     * modelo del TARGET. KMX011 si un `target` no existe; KMX032 (warning) si un campo trae config
     * por campo Y por método (gana el método). El core no cambia: ambas sedes producen el mismo `MClass`.
     */
    fun applyMethodConfig(
        target: MClass,
        config: Map<String, KspTranslator.MethodFieldConfig>,
        methodLocation: MLocation,
        node: KSAnnotated,
    ): MClass {
        if (config.isEmpty()) return target
        val fieldNames = target.fieldNames()
        config.keys.forEach { field ->
            if (field !in fieldNames) {
                ctx.reporter.report(
                    Diagnostics.methodTargetMissing(
                        methodLocation, field, target.type.qualifiedName, Suggestions.closest(field, fieldNames),
                    ),
                    node,
                )
            }
        }
        fun hadFieldConfig(name: String): Boolean =
            target.constructors.any { c ->
                c.params.any {
                    it.name == name &&
                        (it.mappedFrom != null || it.useConverter != null || it.strategies.isNotEmpty() || it.ignored)
                }
            } || target.properties.any {
                it.name == name &&
                    (it.mappedFrom != null || it.useConverter != null || it.strategies.isNotEmpty() || it.ignored)
            }
        config.keys.filter { it in fieldNames && hadFieldConfig(it) }.forEach { name ->
            ctx.reporter.report(Diagnostics.duplicateFieldConfig(MLocation(target.type.qualifiedName), name), node)
        }
        val newConstructors = target.constructors.map { ctor ->
            ctor.copy(
                params = ctor.params.map { p ->
                    val c = config[p.name] ?: return@map p
                    p.copy(
                        mappedFrom = c.mappedFrom ?: p.mappedFrom,
                        useConverter = c.useConverter ?: p.useConverter,
                        strategies = if (c.strategies.isNotEmpty()) c.strategies else p.strategies,
                        // El ignore se UNE entre niveles (nunca des-ignora).
                        ignored = c.ignored || p.ignored,
                    )
                },
            )
        }
        val newProperties = target.properties.map { prop ->
            val c = config[prop.name] ?: return@map prop
            prop.copy(
                mappedFrom = c.mappedFrom ?: prop.mappedFrom,
                useConverter = c.useConverter ?: prop.useConverter,
                strategies = if (c.strategies.isNotEmpty()) c.strategies else prop.strategies,
                ignored = c.ignored || prop.ignored,
            )
        }
        return target.copy(constructors = newConstructors, properties = newProperties)
    }
}

/**
 * Resuelve un método de MAPPING: delega en la extension del par si existe (la
 * extension es la fuente de verdad) o materializa el plan inline con el MISMO motor. Devuelve el
 * método aun con plan inválido (todos los errores en una pasada); null solo si la
 * post-función está mal formada.
 */
internal class MappingMethodResolver(private val c: MapperMethodContext) {

    fun resolve(
        method: KSFunctionDeclaration,
        sourceParam: KSValueParameter,
        sourceDecl: KSClassDeclaration,
        returnDecl: KSClassDeclaration,
        parameters: List<MParam>,
        returns: MType,
    ): MapperMethod? {
        val ctx = c.ctx
        val methodName = method.simpleName.asString()

        // Post-función `after<Método>(source, result): result` como método default.
        val sourceQn = sourceDecl.qualifiedName?.asString().orEmpty()
        val returnQn = returnDecl.qualifiedName?.asString().orEmpty()
        val after = c.afterCandidate(methodName, listOf(sourceQn, returnQn), returnQn)
        if (after?.second == false) {
            ctx.reporter.report(
                Diagnostics.afterFunctionBadSignature(c.location, after.first, sourceQn, returnQn),
                c.mapper,
            )
            return null
        }

        val pair = sourceQn to returnQn
        val body = ctx.index.declaredExtensions[pair]?.let { qualifiedFunction ->
            // La extension del mismo par existe: es la fuente de verdad — se delega.
            MethodBody.DelegateToExtension(sourceParam.name!!.asString(), qualifiedFunction)
        } ?: run {
            // Sin extension declarada: mismo motor, materializado inline. Los parámetros
            // extra del método son fuentes suplementarias para el matching (por nombre).
            val extras = method.parameters.drop(1)
            val sourceModel = ctx.translator.translate(sourceDecl).let { model ->
                model.copy(
                    properties = model.properties + extras.map {
                        MProperty(it.name?.asString() ?: "<unnamed>", ctx.translator.translateType(it.type))
                    },
                )
            }
            // Modo B: la config por método se funde sobre el modelo del TARGET,
            // de modo que el motor resuelve idéntico a la sede por campo.
            val targetModel = c.applyMethodConfig(
                ctx.translator.translate(returnDecl, ctx.index.topLevelFactories[returnQn].orEmpty())
                    // La lista `ignore` del @Mapper aplica al target de TODOS los métodos.
                    .withIgnored(c.optIns.ignore, c.ignoreAudit),
                c.mergedMethodConfig(method),
                c.location.copy(member = methodName),
                method,
            )
            val plan = ctx.engine.resolve(
                source = sourceModel,
                target = targetModel,
                emission = Emission.ExtensionFunction(methodName),
                declaredMappings = ctx.index.declaredExtensions,
                converters = ctx.index.converters,
                // En modo B las rutas `a.b.c` del `from` por método se navegan
                // contra el SOURCE, igual que en modo A — paridad total de sedes.
                resolvedPaths = ctx.paths.resolve(sourceDecl, targetModel),
                useSerialNames = c.useSerialNames,
                nullPolicies = c.nullPolicies,
                // Solo el modo B inline habilita converters-class inyectados.
                allowInjectedConverters = true,
                stdConverters = c.stdConverters,
                unmapped = c.unmapped,
            )
            plan.diagnostics.forEach { ctx.reporter.report(it, method) }
            MethodBody.InlineConstruction(
                receiverParam = sourceParam.name!!.asString(),
                plan = plan,
                supplementaryParams = extras.mapNotNull { it.name?.asString() }.toSet(),
            )
        }
        return MapperMethod(methodName, parameters, returns, body, after?.first)
    }
}

/**
 * Resuelve un método de COLECCIÓN: el elemento se resuelve por DELEGACIÓN,
 * nunca inline (un plan materializado dentro del lambda duplicaría la fuente de
 * verdad). Prioridad: (1º) método hermano del MISMO mapper con el par exacto de elementos;
 * (2º) extension declarada del par. Sin ninguna → KMX046 (con el detalle exacto: par de
 * contenedores fuera de la lista cerrada, elementos nullables, o falta el mapeo del elemento).
 */
internal class CollectionMethodResolver(private val c: MapperMethodContext) {

    fun resolve(
        method: KSFunctionDeclaration,
        sourceParam: KSValueParameter,
        parameters: List<MParam>,
        returns: MType,
    ): MapperMethod? {
        val ctx = c.ctx
        val methodName = method.simpleName.asString()
        val methodLocation = c.location.copy(member = methodName)
        val sourceType = parameters.single().type

        fun unresolved(detail: String?): MapperMethod? {
            ctx.reporter.report(
                Diagnostics.collectionMethodUnresolved(
                    methodLocation,
                    sourceType.typeArgs.singleOrNull()?.qualifiedName ?: sourceType.qualifiedName,
                    returns.typeArgs.singleOrNull()?.qualifiedName ?: returns.qualifiedName,
                    detail,
                ),
                method,
            )
            return null
        }

        // La lista CERRADA de pares de contenedor (coherente con las conversiones implícitas): List→List, Set→Set,
        // y List/Set → Collection/Iterable. Cruces y Maps: KMX046 con detalle.
        val containerOk = when (returns.kind) {
            TypeKind.COLLECTION_LIST -> sourceType.kind == TypeKind.COLLECTION_LIST
            TypeKind.COLLECTION_SET -> sourceType.kind == TypeKind.COLLECTION_SET
            TypeKind.COLLECTION_ITERABLE ->
                sourceType.kind == TypeKind.COLLECTION_LIST || sourceType.kind == TypeKind.COLLECTION_SET
            else -> false
        }
        if (!containerOk) {
            return unresolved(
                "the container pair ${sourceType.simpleName} -> ${returns.simpleName} is outside " +
                    "the closed list: List -> List, Set -> Set, List/Set -> Collection/Iterable",
            )
        }
        val sourceElement = sourceType.typeArgs.singleOrNull() ?: return unresolved("missing element type")
        val targetElement = returns.typeArgs.singleOrNull() ?: return unresolved("missing element type")
        if (sourceElement.nullable || targetElement.nullable) {
            return unresolved("nullable elements are not supported in v1")
        }

        // (1º) un método hermano del MISMO mapper con el par exacto de elementos.
        val sibling = c.abstractMethods.firstOrNull { fn ->
            fn !== method && fn.parameters.size == 1 &&
                fn.annotations.none { it.qualifiedName() == Ann.INVERSE_OF } &&
                fn.parameters[0].type.resolve().declaration.qualifiedName?.asString() == sourceElement.qualifiedName &&
                fn.returnType?.resolve()?.declaration?.qualifiedName?.asString() == targetElement.qualifiedName
        }
        // (2º) una extension declarada del par (embedded u otro origen).
        val extension = ctx.index.declaredExtensions[sourceElement.qualifiedName to targetElement.qualifiedName]
        val call = when {
            sibling != null -> ElementCall.SelfMethod(sibling.simpleName.asString())
            extension != null -> ElementCall.Extension(extension)
            else -> return unresolved(null)
        }

        // `after<Método>(source, result): result` — mismo contrato (los nombres
        // calificados de los contenedores; los genéricos no se distinguen, como en el resto).
        val sourceQn = sourceType.qualifiedName
        val returnQn = returns.qualifiedName
        val after = c.afterCandidate(methodName, listOf(sourceQn, returnQn), returnQn)
        if (after?.second == false) {
            ctx.reporter.report(
                Diagnostics.afterFunctionBadSignature(c.location, after.first, sourceQn, returnQn),
                c.mapper,
            )
            return null
        }

        return MapperMethod(
            name = methodName,
            parameters = parameters,
            returns = returns,
            body = MethodBody.CollectionDelegate(
                receiverParam = sourceParam.name!!.asString(),
                into = returns,
                element = call,
            ),
            afterFunction = after?.first,
        )
    }
}

/**
 * Resuelve UN método PATCH (`fun x(target: T, patch: P): T`, detectado por
 * forma): valida `after<Método>(target, patch, result)` y delega en
 * `engine.resolvePatch` (KMX012 si T no es data class). Devuelve null si el método es inválido.
 */
internal class PatchMethodResolver(private val c: MapperMethodContext) {

    fun resolve(
        method: KSFunctionDeclaration,
        targetDecl: KSClassDeclaration,
        parameters: List<MParam>,
        returns: MType,
    ): MapperMethod? {
        val ctx = c.ctx
        val methodName = method.simpleName.asString()
        val targetParam = method.parameters[0]
        val patchParam = method.parameters[1]
        val patchDecl = patchParam.type.resolve().declaration as? KSClassDeclaration
        if (patchDecl == null) {
            ctx.reporter.report(
                Diagnostics.internalError(c.location.copy(member = methodName), "patch parameter must be a class"),
                method,
            )
            return null
        }
        val targetQn = targetDecl.qualifiedName?.asString().orEmpty()
        val patchQn = patchDecl.qualifiedName?.asString().orEmpty()

        // La post-función del PATCH sigue la regla general `after<Método>`,
        // con la forma (target, patch, result): result. Para `fun apply` produce `afterApply`.
        val after = c.afterCandidate(methodName, listOf(targetQn, patchQn, targetQn), targetQn)
        if (after?.second == false) {
            ctx.reporter.report(
                Diagnostics.afterApplyBadSignature(c.location, targetQn, patchQn, afterName = after.first),
                c.mapper,
            )
            return null
        }

        val targetModel = ctx.translator.translate(targetDecl)
        val resolution = ctx.engine.resolvePatch(
            target = targetModel,
            patch = ctx.translator.translate(patchDecl),
            declaredMappings = ctx.index.declaredExtensions,
            converters = ctx.index.converters,
            // Las rutas del target leen del PATCH — se navegan contra él.
            resolvedPaths = ctx.paths.resolve(patchDecl, targetModel),
        )
        resolution.diagnostics.forEach { ctx.reporter.report(it, method) }
        if (resolution.diagnostics.any { it.severity == Severity.ERROR }) return null

        return MapperMethod(
            name = methodName,
            parameters = parameters,
            returns = returns,
            body = MethodBody.PatchApplication(
                targetParam = MParam(targetParam.name!!.asString(), ctx.translator.translateType(targetParam.type)),
                patchParam = MParam(patchParam.name!!.asString(), ctx.translator.translateType(patchParam.type)),
                fields = resolution.fields,
            ),
            afterFunction = after?.first,
        )
    }
}

/**
 * Resuelve UN método `@InverseOf`: localiza el método FORWARD (por nombre o
 * auto-detección de la firma inversa exacta), reconstruye su modelo target con la config por
 * método aplicada, y pide al motor la resolución BIDIRECCIONAL — el plan de la vuelta es el
 * cuerpo de este método; las asimetrías llegan como KMX028 (misma maquinaria que la inversión bidireccional).
 * Los diagnósticos crudos de la IDA no se re-reportan (el método forward ya los reporta).
 */
internal class InverseMethodResolver(private val c: MapperMethodContext) {

    fun resolve(
        method: KSFunctionDeclaration,
        forwardName: String,
        parameters: List<MParam>,
        returns: MType,
    ): MapperMethod? {
        val ctx = c.ctx
        val methodName = method.simpleName.asString()
        val methodLocation = c.location.copy(member = methodName)
        fun invalid(detail: String): MapperMethod? {
            ctx.reporter.report(Diagnostics.invalidInverse(methodLocation, detail), method)
            return null
        }

        if (method.parameters.size != 1) {
            return invalid("an inverse method must take exactly one parameter")
        }
        if (method.annotations.any { it.qualifiedName() == Ann.MAP_FIELD }) {
            return invalid("an @InverseOf method cannot declare its own @MapField (inversion is all-or-nothing)")
        }
        val paramQn = method.parameters[0].type.resolve().declaration.qualifiedName?.asString()
        val returnQn = method.returnType?.resolve()?.declaration?.qualifiedName?.asString()

        // Forward: por nombre, o auto-detección de la ÚNICA firma inversa exacta.
        fun signatureInverse(fn: KSFunctionDeclaration): Boolean =
            fn.parameters.size == 1 &&
                fn.parameters[0].type.resolve().declaration.qualifiedName?.asString() == returnQn &&
                fn.returnType?.resolve()?.declaration?.qualifiedName?.asString() == paramQn
        val forward = if (forwardName.isNotEmpty()) {
            c.abstractMethods.firstOrNull { it.simpleName.asString() == forwardName }
                ?: return invalid("no abstract method named '$forwardName' in this mapper")
        } else {
            val candidates = c.abstractMethods.filter { it !== method && signatureInverse(it) }
            when (candidates.size) {
                1 -> candidates.single()
                0 -> return invalid("no method with the inverse signature found (auto-detection)")
                else -> return invalid(
                    "ambiguous inverse: ${candidates.joinToString { it.simpleName.asString() }} " +
                        "all match — name it: @InverseOf(\"...\")",
                )
            }
        }
        if (!signatureInverse(forward)) {
            return invalid("'${forward.simpleName.asString()}' does not have the exact inverse signature")
        }
        // Guarda de ciclo: dos @InverseOf apuntándose mutuamente no tienen una IDA de la cual
        // derivar config — al menos uno de los dos debe ser un mapping normal.
        if (forward.annotations.any { it.qualifiedName() == Ann.INVERSE_OF }) {
            return invalid("'${forward.simpleName.asString()}' is itself @InverseOf — the forward must be a regular mapping method")
        }

        val aDecl = forward.parameters[0].type.resolve().declaration as? KSClassDeclaration
        val bDecl = forward.returnType?.resolve()?.declaration as? KSClassDeclaration
        if (aDecl == null || bDecl == null) {
            return invalid("the forward method must map class to class")
        }

        // El modelo B lleva la config por método del FORWARD (herencia de config incluida) y el
        // ignore de nivel — exactamente lo que su propia resolución usa.
        val bQn = bDecl.qualifiedName?.asString()
        // El ignore de nivel aplica SIN re-validar nombres (la validación de la lista es
        // responsabilidad del loop de métodos normales — evita KMX011 duplicado o falso: un
        // nombre puede existir en B y legítimamente no en A).
        val aModel = ctx.translator.translate(aDecl).withIgnored(c.optIns.ignore)
        val bModel = c.applyMethodConfig(
            ctx.translator.translate(bDecl, ctx.index.topLevelFactories[bQn].orEmpty())
                .withIgnored(c.optIns.ignore),
            c.mergedMethodConfig(forward),
            c.location.copy(member = forward.simpleName.asString()),
            method,
        )

        val resolution = ctx.engine.resolveBidirectional(
            a = aModel,
            b = bModel,
            forwardEmission = Emission.ExtensionFunction(forward.simpleName.asString()),
            reverseEmission = Emission.ExtensionFunction(methodName),
            declaredMappings = ctx.index.declaredExtensions,
            converters = ctx.index.converters,
            useSerialNames = c.useSerialNames,
            nullPolicies = c.nullPolicies,
            stdConverters = c.stdConverters,
            unmapped = c.unmapped,
        )
        // Los crudos de la ida ya los reporta el método forward; aquí van las asimetrías
        // (KMX028 y transformaciones) y los problemas propios de la vuelta.
        val forwardRaw = resolution.forward.diagnostics.toSet()
        val toReport = resolution.diagnostics.filterNot { it in forwardRaw }
        toReport.forEach { ctx.reporter.report(it, method) }
        if (toReport.hasError() || !resolution.reverse.valid) return null

        // After<Método>(source, result) — mismo contrato que un mapping normal.
        val after = c.afterCandidate(methodName, listOf(paramQn, returnQn), returnQn)
        if (after?.second == false) {
            ctx.reporter.report(
                Diagnostics.afterFunctionBadSignature(c.location, after.first, paramQn.orEmpty(), returnQn.orEmpty()),
                c.mapper,
            )
            return null
        }

        return MapperMethod(
            name = methodName,
            parameters = parameters,
            returns = returns,
            body = MethodBody.InlineConstruction(
                receiverParam = method.parameters[0].name!!.asString(),
                plan = resolution.reverse,
            ),
            afterFunction = after?.first,
        )
    }
}
