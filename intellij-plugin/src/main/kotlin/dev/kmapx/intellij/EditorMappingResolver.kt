package dev.kmapx.intellij

import dev.kmapx.core.diagnostics.Diagnostics
import dev.kmapx.core.diagnostics.MLocation
import dev.kmapx.core.diagnostics.MapFieldRules
import dev.kmapx.core.engine.MappingEngine
import dev.kmapx.core.engine.NullPolicy
import dev.kmapx.core.model.MClass
import dev.kmapx.core.plan.Emission
import dev.kmapx.core.plan.MappingPlan
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtNamedFunction

/**
 * La resolución de mapeos EN EL EDITOR, compartida por la inspección
 * ([MappingInspection]) y el preview ([GeneratedCodePreview]): el MISMO `MappingEngine` del
 * compilador sobre el modelo del [PsiAdapter], alimentado con el estado del editor —
 * el índice colaborativo del proyecto ([ProjectMappingIndex]: mapeos declarados y converters)
 * y la config global del build ([KmapxBuildConfig]: `kmapx.onNull`, `kmapx.stdConverters`).
 *
 * Abstenciones (documentadas en cada modo): `useSerialNames` (el adapter no lee `@SerialName`),
 * patch/multi-fuente e `@InverseOf` en contract.
 */
internal object EditorMappingResolver {

    data class ResolvedMapTo(
        val annotation: KtAnnotationEntry,
        val target: KtClassOrObject,
        val functionName: String,
        val plan: MappingPlan,
    )

    data class ResolvedMethod(
        val method: KtNamedFunction,
        val sourceClass: KtClassOrObject,
        val targetClass: KtClassOrObject,
        val plan: MappingPlan,
    )

    /** Los planes de los `@MapTo` de una clase (modo embedded). */
    fun mapTos(source: KtClassOrObject): List<ResolvedMapTo> {
        val project = source.project
        val annotations = source.annotationEntries.filter { it.shortName?.asString() == "MapTo" }
        if (annotations.isEmpty()) return emptyList()

        val adapter = PsiAdapter(project)
        val sourceModel = adapter.translate(source)
        val global = KmapxBuildConfig.of(project)
        val index = ProjectMappingIndex.of(project)

        return annotations.mapNotNull { annotation ->
            // Sin @SerialName en el adapter, inspeccionar daría falsos KMX002 — abstención.
            if (MapFieldPsi.booleanValue(annotation, "useSerialNames")) return@mapNotNull null
            val classLiteral = annotation.valueArguments.firstOrNull()
                ?.getArgumentExpression()?.text?.removeSuffix("::class") ?: return@mapNotNull null
            val targetShort = classLiteral.substringAfterLast('.')
            val target = MapFieldPsi.ownerClass(project, targetShort) ?: return@mapNotNull null
            val functionName = MapFieldPsi.stringValue(annotation, "name")?.takeIf { it.isNotEmpty() }
                ?: "to$targetShort"

            val plan = MappingEngine().resolve(
                source = sourceModel,
                target = adapter.translate(target)
                    // La lista `ignore` del @MapTo (sin validar nombres aquí: eso es del build).
                    .withFieldConfig(emptyMap(), MapFieldPsi.stringListValue(annotation, "ignore")),
                emission = Emission.ExtensionFunction(functionName),
                declaredMappings = index.declaredMappings,
                converters = index.converters,
                // Mapeo primero, GLOBAL del build después (v0.6: la salvedad cerrada).
                nullPolicies = listOfNotNull(
                    MapFieldRules.levelPolicyFor(MapFieldPsi.enumEntryText(annotation, "onNull")),
                    global.onNull,
                ),
                stdConverters = MapFieldPsi.booleanValue(annotation, "stdConverters") || global.stdConverters,
            )
            ResolvedMapTo(annotation, target, functionName, plan)
        }
    }

    /** Los planes de los métodos de MAPEO simple de una interfaz `@Mapper` (modo contract). */
    fun mapperMethods(mapper: KtClassOrObject): List<ResolvedMethod> {
        if (mapper !is KtClass || !mapper.isInterface()) return emptyList()
        val mapperAnn = mapper.annotationEntries.firstOrNull { it.shortName?.asString() == "Mapper" }
            ?: return emptyList()
        val project = mapper.project
        val profileAnn = profileAnnotationOf(mapper, mapperAnn)

        // Misma abstención que en embedded, en cualquiera de las dos sedes de config.
        if (MapFieldPsi.booleanValue(mapperAnn, "useSerialNames") ||
            profileAnn?.let { MapFieldPsi.booleanValue(it, "useSerialNames") } == true
        ) {
            return emptyList()
        }

        val global = KmapxBuildConfig.of(project)
        val index = ProjectMappingIndex.of(project)

        // Los niveles de la cascada, EN ORDEN — mapper > profile > global.
        val nullPolicies: List<NullPolicy> = listOfNotNull(
            MapFieldRules.levelPolicyFor(MapFieldPsi.enumEntryText(mapperAnn, "onNull")),
            profileAnn?.let { MapFieldRules.levelPolicyFor(MapFieldPsi.enumEntryText(it, "onNull")) },
            global.onNull,
        )
        val levelIgnore = MapFieldPsi.stringListValue(mapperAnn, "ignore") +
            (profileAnn?.let { MapFieldPsi.stringListValue(it, "ignore") } ?: emptySet())
        val stdConverters = global.stdConverters ||
            MapFieldPsi.booleanValue(mapperAnn, "stdConverters") ||
            profileAnn?.let { MapFieldPsi.booleanValue(it, "stdConverters") } == true

        val adapter = PsiAdapter(project)
        return mapper.declarations.filterIsInstance<KtNamedFunction>().mapNotNull { method ->
            // Solo la forma de MAPEO simple: abstracta, 1 parámetro, sin @InverseOf. Patch,
            // multi-fuente y colecciones quedan para el build.
            if (method.hasBody()) return@mapNotNull null
            if (method.annotationEntries.any { it.shortName?.asString() == "InverseOf" }) return@mapNotNull null
            val param = method.valueParameters.singleOrNull() ?: return@mapNotNull null
            val sourceName = param.typeReference?.text?.substringBefore('<')?.removeSuffix("?")
                ?: return@mapNotNull null
            val targetName = method.typeReference?.text?.substringBefore('<')?.removeSuffix("?")
                ?: return@mapNotNull null
            val sourceClass = MapFieldPsi.ownerClass(project, sourceName) ?: return@mapNotNull null
            val targetClass = MapFieldPsi.ownerClass(project, targetName) ?: return@mapNotNull null

            // La config POR CAMPO declarada en el método: `@MapField(target = "x", ...)`.
            val methodConfig = method.annotationEntries
                .filter { it.shortName?.asString() == "MapField" }
                .mapNotNull { entry ->
                    MapFieldPsi.stringValue(entry, "target")?.takeIf { it.isNotEmpty() }
                        ?.let { it to MapFieldPsi.aspectsOf(entry) }
                }
                .toMap()

            val plan = MappingEngine().resolve(
                source = adapter.translate(sourceClass),
                target = adapter.translate(targetClass).withFieldConfig(methodConfig, levelIgnore),
                emission = Emission.ExtensionFunction(method.name ?: "preview"),
                declaredMappings = index.declaredMappings,
                converters = index.converters,
                nullPolicies = nullPolicies,
                stdConverters = stdConverters,
            )
            ResolvedMethod(method, sourceClass, targetClass, plan)
        }
    }

    // ── v0.8: las FORMAS restantes de contract — colecciones, patch e @InverseOf ──

    /**
     * Un problema de FORMA de un método de `@Mapper` (v0.8). [missingElementPair] viene seteado
     * solo en el KMX046 de "falta el mapeo del elemento" — alimenta el quick-fix que declara el
     * método hermano.
     */
    data class ShapeIssue(
        val method: KtNamedFunction,
        val diagnostic: dev.kmapx.core.diagnostics.Diagnostic,
        val missingElementPair: Pair<String, String>? = null,
    )

    /**
     * Los diagnósticos de FORMA de los métodos no-simples: colecciones (KMX046, espejando la
     * lista cerrada de `CollectionMethodResolver`), patch (`resolvePatch` real, KMX012 + campos)
     * e `@InverseOf` (las validaciones LOCALES de KMX045 — los textos de detalle espejan a
     * `InverseMethodResolver`; el código y la factory son los compartidos del core).
     */
    fun mapperShapeIssues(mapper: KtClassOrObject): List<ShapeIssue> {
        if (mapper !is KtClass || !mapper.isInterface()) return emptyList()
        val mapperAnn = mapper.annotationEntries.firstOrNull { it.shortName?.asString() == "Mapper" }
            ?: return emptyList()
        // El patch matchea campos por nombre — con @SerialName daría falsos; misma abstención.
        val profileAnn = profileAnnotationOf(mapper, mapperAnn)
        if (MapFieldPsi.booleanValue(mapperAnn, "useSerialNames") ||
            profileAnn?.let { MapFieldPsi.booleanValue(it, "useSerialNames") } == true
        ) {
            return emptyList()
        }
        val project = mapper.project
        val index = ProjectMappingIndex.of(project)
        val adapter = PsiAdapter(project)
        val pkg = mapper.containingKtFile.packageFqName.asString()
        val mapperQn = mapper.name?.let { if (pkg.isEmpty()) it else "$pkg.$it" } ?: return emptyList()
        val abstractMethods = mapper.declarations.filterIsInstance<KtNamedFunction>().filter { !it.hasBody() }

        val issues = mutableListOf<ShapeIssue>()
        for (method in abstractMethods) {
            val location = MLocation(mapperQn, method.name)
            val inverseAnn = method.annotationEntries.firstOrNull { it.shortName?.asString() == "InverseOf" }
            when {
                inverseAnn != null ->
                    inverseIssue(method, inverseAnn, abstractMethods)?.let {
                        issues += ShapeIssue(method, Diagnostics.invalidInverse(location, it))
                    }
                isCollectionShape(method) ->
                    issues += collectionIssues(method, location, abstractMethods, index, project)
                isPatchShape(method) ->
                    issues += patchIssues(method, index, adapter, project)
                else -> Unit
            }
        }
        return issues
    }

    private val CONTAINERS = setOf("List", "Set", "Collection", "Iterable", "Map", "Array", "Sequence")

    private fun shortOf(text: String?): String? =
        text?.substringBefore('<')?.removeSuffix("?")?.substringAfterLast('.')?.takeIf { it.isNotEmpty() }

    private fun elementOf(text: String?): String? =
        text?.substringAfter('<', "")?.substringBeforeLast('>', "")?.trim()?.takeIf { it.isNotEmpty() }

    private fun isCollectionShape(method: KtNamedFunction): Boolean =
        method.valueParameters.size == 1 &&
            shortOf(method.valueParameters[0].typeReference?.text) in CONTAINERS &&
            shortOf(method.typeReference?.text) in CONTAINERS

    private fun isPatchShape(method: KtNamedFunction): Boolean =
        method.valueParameters.size == 2 &&
            shortOf(method.typeReference?.text) != null &&
            shortOf(method.typeReference?.text) == shortOf(method.valueParameters[0].typeReference?.text)

    /** Métodos de colección en el editor — la MISMA lista cerrada del `CollectionMethodResolver` del build. */
    private fun collectionIssues(
        method: KtNamedFunction,
        location: MLocation,
        abstractMethods: List<KtNamedFunction>,
        index: ProjectMappingIndex.Snapshot,
        project: com.intellij.openapi.project.Project,
    ): List<ShapeIssue> {
        val sourceText = method.valueParameters[0].typeReference?.text
        val targetText = method.typeReference?.text
        val sourceShort = shortOf(sourceText) ?: return emptyList()
        val targetShort = shortOf(targetText) ?: return emptyList()
        val sourceElem = elementOf(sourceText)
        val targetElem = elementOf(targetText)

        fun issue(detail: String?, pair: Pair<String, String>? = null) = listOf(
            ShapeIssue(
                method,
                Diagnostics.collectionMethodUnresolved(
                    location,
                    sourceElem?.removeSuffix("?") ?: sourceShort,
                    targetElem?.removeSuffix("?") ?: targetShort,
                    detail,
                ),
                pair,
            ),
        )

        val containerOk = when (targetShort) {
            "List" -> sourceShort == "List"
            "Set" -> sourceShort == "Set"
            "Collection", "Iterable" -> sourceShort == "List" || sourceShort == "Set"
            else -> false
        }
        if (!containerOk) {
            return issue(
                "the container pair $sourceShort -> $targetShort is outside the closed list: " +
                    "List -> List, Set -> Set, List/Set -> Collection/Iterable",
            )
        }
        if (sourceElem == null || targetElem == null) return issue("missing element type")
        if (sourceElem.endsWith("?") || targetElem.endsWith("?")) {
            return issue("nullable elements are not supported in v1")
        }

        val sourceElemShort = sourceElem.substringAfterLast('.')
        val targetElemShort = targetElem.substringAfterLast('.')
        // (1º) método hermano del par exacto (por nombre corto — la heurística del editor).
        val sibling = abstractMethods.any { fn ->
            fn !== method && fn.valueParameters.size == 1 &&
                fn.annotationEntries.none { it.shortName?.asString() == "InverseOf" } &&
                shortOf(fn.valueParameters[0].typeReference?.text) == sourceElemShort &&
                shortOf(fn.typeReference?.text) == targetElemShort
        }
        if (sibling) return emptyList()
        // (2º) extension declarada del par (el índice del proyecto la conoce por qualified name).
        fun qualifiedOf(short: String): String? = MapFieldPsi.ownerClass(project, short)?.let {
            val ownerPkg = it.containingKtFile.packageFqName.asString()
            if (ownerPkg.isEmpty()) short else "$ownerPkg.$short"
        }
        val sourceQn = qualifiedOf(sourceElemShort)
        val targetQn = qualifiedOf(targetElemShort)
        // Elementos aún no escritos/localizables: abstención (mejor callar que un falso KMX046).
        if (sourceQn == null || targetQn == null) return emptyList()
        if (index.declaredMappings.containsKey(sourceQn to targetQn)) return emptyList()
        return issue(null, sourceElemShort to targetElemShort)
    }

    /** PATCH en el editor — el `resolvePatch` REAL, filtrado a los códigos seguros con el índice. */
    private fun patchIssues(
        method: KtNamedFunction,
        index: ProjectMappingIndex.Snapshot,
        adapter: PsiAdapter,
        project: com.intellij.openapi.project.Project,
    ): List<ShapeIssue> {
        val targetShort = shortOf(method.typeReference?.text) ?: return emptyList()
        val patchShort = shortOf(method.valueParameters[1].typeReference?.text) ?: return emptyList()
        val target = MapFieldPsi.ownerClass(project, targetShort) ?: return emptyList()
        val patch = MapFieldPsi.ownerClass(project, patchShort) ?: return emptyList()

        val resolution = MappingEngine().resolvePatch(
            target = adapter.translate(target),
            patch = adapter.translate(patch),
            declaredMappings = index.declaredMappings,
            converters = index.converters,
        )
        return resolution.diagnostics
            .filter { it.code in PATCH_SAFE_CODES }
            .map { ShapeIssue(method, it) }
    }

    /** Las validaciones LOCALES de `@InverseOf` (espejo de `InverseMethodResolver`). */
    private fun inverseIssue(
        method: KtNamedFunction,
        annotation: KtAnnotationEntry,
        abstractMethods: List<KtNamedFunction>,
    ): String? {
        if (method.valueParameters.size != 1) return "an inverse method must take exactly one parameter"
        if (method.annotationEntries.any { it.shortName?.asString() == "MapField" }) {
            return "an @InverseOf method cannot declare its own @MapField (inversion is all-or-nothing)"
        }
        val paramShort = shortOf(method.valueParameters[0].typeReference?.text)
        val returnShort = shortOf(method.typeReference?.text)

        fun signatureInverse(fn: KtNamedFunction): Boolean =
            fn.valueParameters.size == 1 &&
                shortOf(fn.valueParameters[0].typeReference?.text) == returnShort &&
                shortOf(fn.typeReference?.text) == paramShort

        val forwardName = (annotation.valueArguments.firstOrNull() as? org.jetbrains.kotlin.psi.KtValueArgument)
            ?.getArgumentExpression()?.let { it as? org.jetbrains.kotlin.psi.KtStringTemplateExpression }
            ?.entries?.singleOrNull()?.text.orEmpty()

        val forward = if (forwardName.isNotEmpty()) {
            abstractMethods.firstOrNull { it.name == forwardName }
                ?: return "no abstract method named '$forwardName' in this mapper"
        } else {
            val candidates = abstractMethods.filter { it !== method && signatureInverse(it) }
            when (candidates.size) {
                1 -> candidates.single()
                0 -> return "no method with the inverse signature found (auto-detection)"
                else -> return "ambiguous inverse: ${candidates.joinToString { it.name.orEmpty() }} " +
                    "all match — name it: @InverseOf(\"...\")"
            }
        }
        if (!signatureInverse(forward)) {
            return "'${forward.name}' does not have the exact inverse signature"
        }
        if (forward.annotationEntries.any { it.shortName?.asString() == "InverseOf" }) {
            return "'${forward.name}' is itself @InverseOf — the forward must be a regular mapping method"
        }
        return null
    }

    private val PATCH_SAFE_CODES = setOf(
        dev.kmapx.core.diagnostics.DiagnosticCode.KMX012,
        dev.kmapx.core.diagnostics.DiagnosticCode.KMX004,
        dev.kmapx.core.diagnostics.DiagnosticCode.KMX007,
    )

    /** La `@MapperConfig` del profile referenciado por `config = X::class` (si lo hay). */
    private fun profileAnnotationOf(mapper: KtClass, mapperAnn: KtAnnotationEntry): KtAnnotationEntry? =
        MapFieldPsi.argument(mapperAnn, "config")?.getArgumentExpression()?.text
            ?.removeSuffix("::class")?.substringAfterLast('.')
            ?.let { MapFieldPsi.ownerClass(mapper.project, it) }
            ?.annotationEntries?.firstOrNull { it.shortName?.asString() == "MapperConfig" }

    /**
     * El target con la config de NIVEL aplicada al modelo: aspectos por campo (config de método,
     * que PREVALECE sobre la del propio campo: su estrategia se antepone) e ignore de nivel.
     */
    private fun MClass.withFieldConfig(
        configs: Map<String, MapFieldPsi.FieldAspects>,
        ignore: Set<String>,
    ): MClass {
        if (configs.isEmpty() && ignore.isEmpty()) return this
        return copy(
            constructors = constructors.map { c ->
                c.copy(
                    params = c.params.map { p ->
                        val cfg = configs[p.name]
                        p.copy(
                            strategies = listOfNotNull(cfg?.strategy) + p.strategies,
                            mappedFrom = cfg?.from ?: p.mappedFrom,
                            ignored = p.ignored || cfg?.ignored == true || p.name in ignore,
                        )
                    },
                )
            },
            properties = properties.map { p ->
                val cfg = configs[p.name]
                p.copy(
                    strategies = listOfNotNull(cfg?.strategy) + p.strategies,
                    mappedFrom = cfg?.from ?: p.mappedFrom,
                    ignored = p.ignored || cfg?.ignored == true || p.name in ignore,
                )
            },
        )
    }
}
