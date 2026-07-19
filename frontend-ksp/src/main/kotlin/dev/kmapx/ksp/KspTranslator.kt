package dev.kmapx.ksp

import com.google.devtools.ksp.getAllSuperTypes
import com.google.devtools.ksp.getConstructors
import com.google.devtools.ksp.getDeclaredProperties
import com.google.devtools.ksp.isPrivate
import com.google.devtools.ksp.isProtected
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSNode
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeReference
import com.google.devtools.ksp.symbol.KSValueParameter
import com.google.devtools.ksp.symbol.Modifier
import com.google.devtools.ksp.symbol.KSAnnotation
import dev.kmapx.core.diagnostics.Diagnostic
import dev.kmapx.core.diagnostics.DiagnosticCode
import dev.kmapx.core.diagnostics.Diagnostics
import dev.kmapx.core.diagnostics.MLocation
import dev.kmapx.core.diagnostics.MapFieldRules
import dev.kmapx.core.model.MClass
import dev.kmapx.core.model.MConstructor
import dev.kmapx.core.model.MConstructorParam
import dev.kmapx.core.model.MEnumEntry
import dev.kmapx.core.model.MFactory
import dev.kmapx.core.model.MNullStrategy
import dev.kmapx.core.model.MProperty
import dev.kmapx.core.model.MQualifiedConverter
import dev.kmapx.core.model.MType
import dev.kmapx.core.model.TypeKind

/**
 * La frontera: traduce símbolos KSP a modelo de dominio de forma EAGER y por copia.
 * Ningún KSType/KSDeclaration sobrevive a este archivo — el core recibe datos planos.
 * La memoización/caching, si algún benchmark la justifica, vive aquí y solo aquí.
 */
internal class KspTranslator {

    /**
     * Las validaciones de `@MapField` (KMX036–KMX039) se reportan desde la traducción.
     * `var` porque el reporter necesita al translator (locationIndex): el processor cierra el ciclo.
     */
    var reporter: DiagnosticReporter? = null

    /** Una clase anidada se re-traduce por cada uso: cada problema de `@MapField` se reporta UNA vez. */
    private val reportedMapFieldIssues = mutableSetOf<Pair<MLocation, DiagnosticCode>>()

    private fun reportOnce(diagnostic: Diagnostic, node: KSAnnotated) {
        if (reportedMapFieldIssues.add(diagnostic.location to diagnostic.code)) {
            reporter?.report(diagnostic, node)
        }
    }

    /**
     * Índice `MLocation → KSNode` más específico, construido durante la traducción.
     * La frontera del core se conserva: el core sigue emitiendo `MLocation` (datos); solo el
     * frontend sabe volver al símbolo. Clave: (qualifiedClassName, member?).
     */
    val locationIndex: MutableMap<Pair<String, String?>, KSNode> = mutableMapOf()

    /**
     * [topLevelFactories]: funciones top-level `@MapFactory` cuyo retorno es [decl]
     * (las descubre el processor vía resolver; el translator no barre el proyecto).
     */
    fun translate(
        decl: KSClassDeclaration,
        topLevelFactories: List<KSFunctionDeclaration> = emptyList(),
    ): MClass {
        val qualifiedName = decl.qualifiedName?.asString()
        if (qualifiedName != null) {
            locationIndex[qualifiedName to null] = decl
            decl.getDeclaredProperties().forEach { prop ->
                locationIndex[qualifiedName to prop.simpleName.asString()] = prop
            }
            // Los parámetros de constructor GANAN sobre la propiedad homónima (ubicación más precisa):
            decl.getConstructors().forEach { ctor ->
                ctor.parameters.forEach { p ->
                    p.name?.asString()?.let { locationIndex[qualifiedName to it] = p }
                }
            }
        }

        val ctorParamNames = decl.primaryConstructor?.parameters
            ?.mapNotNull { it.name?.asString() }?.toSet() ?: emptySet()

        val ownerName = qualifiedName ?: decl.simpleName.asString()
        val properties = decl.getDeclaredProperties().map { prop ->
            // `var` con `private/protected set` NO es asignable post-construcción:
            val setterRestricted = prop.setter?.modifiers
                ?.any { it == Modifier.PRIVATE || it == Modifier.PROTECTED } == true
            val aspects = prop.mapFieldOf(ownerName, prop.simpleName.asString())
            MProperty(
                name = prop.simpleName.asString(),
                type = prop.type.toMType(),
                mutable = prop.isMutable && !setterRestricted,
                inConstructor = prop.simpleName.asString() in ctorParamNames,
                computed = prop.getter != null && !prop.hasBackingField,
                strategies = listOfNotNull(aspects?.strategy),
                mappedFrom = aspects?.from,
                serialName = prop.serialNameOrNull(),
                useConverter = aspects?.converter,
                ignored = aspects?.ignored ?: false,
            )
        }.toList()

        val constructors = decl.getConstructors().map { ctor ->
            MConstructor(
                params = ctor.parameters.map { it.toMConstructorParam(ownerName) },
                isPrimary = ctor == decl.primaryConstructor,
                // internal es utilizable: el código generado vive en el mismo módulo que el source.
                visible = !ctor.isPrivate() && !ctor.isProtected(),
                annotatedMapConstructor = ctor.hasAnnotation(Ann.MAP_CONSTRUCTOR),
            )
        }.toList()

        val factories = companionFactories(decl) + topLevelFactories
            .filter { !it.isSuspend() }
            .map { fn ->
                MFactory(
                    qualifiedName = fn.qualifiedName?.asString() ?: fn.simpleName.asString(),
                    params = fn.parameters.map { it.toMConstructorParam(ownerName) },
                    companionOf = null,
                )
            }

        val subtypes = if (Modifier.SEALED in decl.modifiers) {
            decl.getSealedSubclasses().map { translate(it) }.toList()
        } else emptyList()

        // Entries del enum, con override @MapEntry.
        val enumEntries = if (decl.classKind == ClassKind.ENUM_CLASS) {
            decl.declarations.filterIsInstance<KSClassDeclaration>()
                .filter { it.classKind == ClassKind.ENUM_ENTRY }
                .map { entry ->
                    MEnumEntry(
                        name = entry.simpleName.asString(),
                        targetOverride = entry.annotations
                            .firstOrNull { it.qualifiedName() == Ann.MAP_ENTRY }
                            ?.arguments?.firstOrNull { a -> a.name?.asString() == "target" }
                            ?.value as? String,
                    )
                }
                .toList()
        } else emptyList()

        // @MapEntry en sede de CLASE = fallback para los entries sin par.
        val enumFallback = if (decl.classKind == ClassKind.ENUM_CLASS) {
            decl.annotations.firstOrNull { it.qualifiedName() == Ann.MAP_ENTRY }
                ?.arguments?.firstOrNull { a -> a.name?.asString() == "target" }
                ?.value as? String
        } else null

        // Override de emparejamiento de subtipos.
        val subtypeOverride = decl.annotations
            .firstOrNull { it.qualifiedName() == Ann.MAP_SUBTYPE }
            ?.arguments?.firstOrNull { it.name?.asString() == "target" }
            ?.let { (it.value as? KSType)?.declaration?.qualifiedName?.asString() }

        return MClass(
            type = decl.asType(emptyList()).toMType(),
            properties = properties,
            constructors = constructors,
            factories = factories,
            sealedSubtypes = subtypes,
            subtypeTargetOverride = subtypeOverride,
            enumEntries = enumEntries,
            enumFallback = enumFallback,
        )
    }

    /** Tipos de parámetros/retorno de métodos de interfaz `@Mapper`, traducidos por copia. */
    fun translateType(ref: KSTypeReference): MType = ref.toMType()

    /** Funciones `@MapFactory` del companion que retornan la propia clase (paso 2). */
    private fun companionFactories(decl: KSClassDeclaration): List<MFactory> {
        val selfName = decl.qualifiedName?.asString() ?: return emptyList()
        return decl.declarations
            .filterIsInstance<KSClassDeclaration>()
            .filter { it.isCompanionObject }
            .flatMap { companion -> companion.declarations.filterIsInstance<KSFunctionDeclaration>() }
            .filter { it.hasAnnotation(Ann.MAP_FACTORY) && !it.isSuspend() }
            .filter { it.returnType?.resolve()?.declaration?.qualifiedName?.asString() == selfName }
            .map { fn ->
                MFactory(
                    qualifiedName = "$selfName.${fn.simpleName.asString()}",
                    params = fn.parameters.map { it.toMConstructorParam(selfName) },
                    companionOf = selfName,
                )
            }
            .toList()
    }

    private fun KSValueParameter.toMConstructorParam(owner: String): MConstructorParam {
        val name = name?.asString() ?: "<unnamed>"
        val aspects = mapFieldOf(owner, name)
        return MConstructorParam(
            name = name,
            type = type.toMType(),
            hasDefault = hasDefault,
            strategies = listOfNotNull(aspects?.strategy),
            mappedFrom = aspects?.from,
            useConverter = aspects?.converter,
            ignored = aspects?.ignored ?: false,
        )
    }

    /** Los aspectos de una `@MapField`, ya leídos y validados. */
    internal data class FieldAspects(
        val from: String? = null,
        val converter: MQualifiedConverter? = null,
        val strategy: MNullStrategy? = null,
        /** El campo se excluye; los demás aspectos deben estar vacíos (KMX043). */
        val ignored: Boolean = false,
    )

    /**
     * Sede de CAMPO: la única `@MapField` del parámetro/propiedad. Más de una = KMX037;
     * `target` seteado = KMX036 (el campo anotado ES el destino).
     */
    private fun KSAnnotated.mapFieldOf(owner: String, member: String): FieldAspects? {
        val declared = annotations.filter { it.qualifiedName() == Ann.MAP_FIELD }.toList()
        if (declared.isEmpty()) return null
        if (MapFieldRules.duplicateTargets(declared.map { member }).isNotEmpty()) {
            reportOnce(Diagnostics.mapFieldDuplicate(MLocation(owner), member), this)
        }
        return aspectsOf(
            declared.first(), MapFieldRules.Site.FIELD, MLocation(owner), member, this,
            addressingLocation = MLocation(owner, member),
        )
    }

    /**
     * Los aspectos de una `@MapField` + la coherencia de la declaración. Las REGLAS (KMX036/038/
     * 039/043) viven en [MapFieldRules] (core) — la MISMA fuente que consume la inspección del
     * plugin de IntelliJ (cero duplicación); aquí solo se mapean a factories y sedes.
     */
    private fun aspectsOf(
        annotation: KSAnnotation,
        site: MapFieldRules.Site,
        base: MLocation,
        fieldName: String,
        node: KSAnnotated,
        addressingLocation: MLocation,
    ): FieldAspects {
        val default = annotation.stringArg("default") ?: UNSET_DEFAULT
        val onNull = annotation.enumEntryName("onNull") ?: "INHERIT"
        val aspects = FieldAspects(
            from = annotation.stringArg("from")?.takeIf { it.isNotEmpty() },
            converter = qualifiedConverterOf(annotation),
            strategy = MapFieldRules.strategyFor(onNull, default.takeIf { it != UNSET_DEFAULT }),
            ignored = annotation.boolArg("ignore"),
        )
        val declaration = MapFieldRules.Declaration(
            targetSet = !annotation.stringArg("target").isNullOrEmpty(),
            onNull = onNull,
            defaultSet = default != UNSET_DEFAULT,
            fromSet = aspects.from != null,
            converterSet = aspects.converter != null,
            ignore = aspects.ignored,
        )
        MapFieldRules.check(site, declaration).forEach { violation ->
            reportOnce(
                when (violation) {
                    MapFieldRules.Violation.BAD_ADDRESSING ->
                        Diagnostics.mapFieldBadAddressing(addressingLocation, methodSite = site == MapFieldRules.Site.METHOD)
                    MapFieldRules.Violation.LITERAL_WITHOUT_DEFAULT ->
                        Diagnostics.literalRequiresDefault(base, fieldName)
                    MapFieldRules.Violation.DEFAULT_IGNORED ->
                        Diagnostics.defaultIgnored(base, fieldName)
                    MapFieldRules.Violation.IGNORE_CONFLICTS ->
                        Diagnostics.ignoreConflictsWithAspects(base, fieldName)
                },
                node,
            )
        }
        return aspects
    }

    /**
     * Construye el [MQualifiedConverter] desde el aspecto `converter` de una `@MapField`.
     * A/B se leen de la supertype `dev.kmapx.runtime.Converts<A,B>` del object; ambos null si no
     * la implementa (KMX029). `Unit::class` = sin converter (centinela).
     */
    fun qualifiedConverterOf(annotation: KSAnnotation): MQualifiedConverter? {
        val decl = annotation.classDeclArg("converter") ?: return null
        val objQn = decl.qualifiedName?.asString() ?: decl.simpleName.asString()
        if (objQn == "kotlin.Unit") return null
        val converts = decl.getAllSuperTypes()
            .firstOrNull { it.declaration.qualifiedName?.asString() == CONVERTS }
        val from = converts?.arguments?.getOrNull(0)?.type?.toMType()
        val to = converts?.arguments?.getOrNull(1)?.type?.toMType()
        // Object → estático; class → bean inyectado (modo B).
        return MQualifiedConverter(objQn, from, to, isObject = decl.classKind == ClassKind.OBJECT)
    }

    /**
     * Modo B — config por MÉTODO agrupada por campo destino (`target = "..."`).
     * `@MapField` es @Repeatable: un método configura varios campos; el MISMO target dos veces
     * es KMX037 y `target` vacío en esta sede es KMX036.
     */
    data class MethodFieldConfig(
        val mappedFrom: String? = null,
        val useConverter: MQualifiedConverter? = null,
        val strategies: List<MNullStrategy> = emptyList(),
        /** `@MapField(target = "x", ignore = true)` en la sede de método. */
        val ignored: Boolean = false,
    )

    fun methodFieldConfig(method: KSFunctionDeclaration): Map<String, MethodFieldConfig> {
        val byTarget = LinkedHashMap<String, MethodFieldConfig>()
        val owner = method.parentDeclaration?.qualifiedName?.asString() ?: "<unknown>"
        val methodLocation = MLocation(owner, method.simpleName.asString())
        method.annotations.filter { it.qualifiedName() == Ann.MAP_FIELD }.forEach { ann ->
            val target = ann.stringArg("target").orEmpty()
            val aspects = aspectsOf(
                ann, MapFieldRules.Site.METHOD, MLocation(owner),
                target.ifEmpty { method.simpleName.asString() }, method,
                addressingLocation = methodLocation,
            )
            if (target.isEmpty()) return@forEach // BAD_ADDRESSING ya reportada por las reglas
            if (target in byTarget) {
                reportOnce(Diagnostics.mapFieldDuplicate(MLocation(owner), target), method)
                return@forEach
            }
            byTarget[target] = MethodFieldConfig(
                mappedFrom = aspects.from,
                useConverter = aspects.converter,
                strategies = listOfNotNull(aspects.strategy),
                ignored = aspects.ignored,
            )
        }
        return byTarget
    }

    /** `@SerialName("x")` como alias de matching (el motor lo consulta solo con opt-in). */
    private fun KSAnnotated.serialNameOrNull(): String? =
        annotations.firstOrNull { it.qualifiedName() == SERIAL_NAME }
            ?.arguments?.firstOrNull { a -> a.name?.asString() == "value" }
            ?.value as? String

    private fun KSAnnotation.qualifiedName(): String? =
        annotationType.resolve().declaration.qualifiedName?.asString()

    private fun KSAnnotated.hasAnnotation(qualifiedName: String): Boolean =
        annotations.any { it.qualifiedName() == qualifiedName }

    // El mapeo es síncrono; factories suspend se ignoran en v1.
    private fun KSFunctionDeclaration.isSuspend(): Boolean = Modifier.SUSPEND in modifiers

    private fun KSTypeReference.toMType(): MType = resolve().toMType()

    private fun KSType.toMType(): MType {
        val decl = declaration as? KSClassDeclaration
        val qualified = declaration.qualifiedName?.asString() ?: declaration.simpleName.asString()
        val kind = decl?.typeKind() ?: TypeKind.OTHER
        val underlying = if (kind == TypeKind.VALUE_CLASS) {
            decl?.primaryConstructor?.parameters?.singleOrNull()?.type?.toMType()
        } else null

        return MType(
            qualifiedName = qualified,
            nullable = isMarkedNullable,
            kind = kind,
            typeArgs = arguments.mapNotNull { it.type?.toMType() },
            underlying = underlying,
            // Paquete real — imprescindible para clases anidadas (Event.Approved).
            packageName = declaration.packageName.asString(),
        )
    }

    private fun KSClassDeclaration.typeKind(): TypeKind = when {
        // ANTES del check de value class — kotlin.Result ES value class.
        qualifiedName?.asString() == "kotlin.Result" -> TypeKind.RESULT
        qualifiedName?.asString() == "kotlin.Array" -> TypeKind.COLLECTION_ARRAY
        Modifier.VALUE in modifiers -> TypeKind.VALUE_CLASS
        Modifier.SEALED in modifiers && classKind == ClassKind.INTERFACE -> TypeKind.SEALED_INTERFACE
        Modifier.SEALED in modifiers -> TypeKind.SEALED_CLASS
        // OBJECT antes que DATA: un `data object` es OBJECT (se despacha por referencia).
        classKind == ClassKind.OBJECT -> TypeKind.OBJECT
        Modifier.DATA in modifiers -> TypeKind.DATA_CLASS
        classKind == ClassKind.ENUM_CLASS -> TypeKind.ENUM
        qualifiedName?.asString() == "kotlin.collections.List" -> TypeKind.COLLECTION_LIST
        qualifiedName?.asString() == "kotlin.collections.Set" -> TypeKind.COLLECTION_SET
        qualifiedName?.asString() == "kotlin.collections.Map" -> TypeKind.COLLECTION_MAP
        // Fuentes iterables — Iterable/Collection materializan a List con `.map{}`.
        qualifiedName?.asString() == "kotlin.collections.Iterable" -> TypeKind.COLLECTION_ITERABLE
        qualifiedName?.asString() == "kotlin.collections.Collection" -> TypeKind.COLLECTION_ITERABLE
        qualifiedName?.asString() == "kotlin.sequences.Sequence" -> TypeKind.COLLECTION_SEQUENCE
        origin == com.google.devtools.ksp.symbol.Origin.JAVA -> TypeKind.JAVA_CLASS
        classKind == ClassKind.CLASS -> TypeKind.REGULAR_CLASS
        else -> TypeKind.OTHER
    }

    private companion object {
        // FQNs NO-kmapx (los de las anotaciones públicas viven en [Ann], un solo lugar):
        const val CONVERTS = "dev.kmapx.runtime.Converts"
        const val SERIAL_NAME = "kotlinx.serialization.SerialName"

        /** Centinela de `MapField.default`: mismo valor que `MapField.UNSET_DEFAULT`. */
        const val UNSET_DEFAULT = "\u0000"
    }
}
