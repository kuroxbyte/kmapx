package dev.kmapx.intellij

import com.intellij.openapi.project.Project
import dev.kmapx.core.model.MClass
import dev.kmapx.core.model.MConstructor
import dev.kmapx.core.model.MConstructorParam
import dev.kmapx.core.model.MEnumEntry
import dev.kmapx.core.model.MProperty
import dev.kmapx.core.model.MType
import dev.kmapx.core.model.TypeKind
import org.jetbrains.kotlin.asJava.classes.KtLightClass
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtEnumEntry
import org.jetbrains.kotlin.psi.KtModifierListOwner
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.lexer.KtTokens

/**
 * El `adapter-psi`: construye [MClass] desde el PSI del editor, el HERMANO de
 * `adapter-reflect` (tests) y de `KspTranslator` (compilación). Con él, la inspección ejecuta el
 * MISMO `MappingEngine` que el build — cero reglas duplicadas en el plugin.
 *
 * Fidelidad consciente (documentada aquí porque define qué diagnósticos son seguros):
 *  - la NULABILIDAD sale del texto del tipo (`?`) — fiable: los parámetros de data classes
 *    llevan tipo explícito;
 *  - los tipos kotlin comunes se mapean a su qualified name real; los del PROYECTO se resuelven
 *    por nombre corto (paquete real si la clase se encuentra; `unresolved.<Nombre>` si no —
 *    CONSISTENTE en ambos lados, así el matching por igualdad sigue funcionando);
 *  - sin registro de converters ni mapeos declarados: la inspección FILTRA a los códigos que no
 *    dependen de ese estado (KMX002/KMX003 — la matriz de nulabilidad decide ANTES que los
 *    converters en la cadena de resolución).
 */
internal class PsiAdapter(private val project: Project) {

    fun translate(declaration: KtClassOrObject): MClass {
        val ctorParams = declaration.primaryConstructor?.valueParameters.orEmpty()
        val ctorParamNames = ctorParams.mapNotNull { it.name }.toSet()

        val params = ctorParams.map { param ->
            val aspects = mapFieldAspects(param.annotationEntries)
            MConstructorParam(
                name = param.name ?: "<unnamed>",
                type = param.typeReference.toMType(),
                hasDefault = param.hasDefaultValue(),
                strategies = listOfNotNull(aspects?.strategy),
                mappedFrom = aspects?.from,
                useConverter = aspects?.converter,
                ignored = aspects?.ignored ?: false,
            )
        }

        val bodyProperties = declaration.declarations.filterIsInstance<KtProperty>().map { prop ->
            val aspects = mapFieldAspects(prop.annotationEntries)
            MProperty(
                name = prop.name ?: "<unnamed>",
                type = prop.typeReference.toMType(),
                mutable = prop.isVar,
                inConstructor = false,
                computed = prop.getter != null && prop.initializer == null,
                strategies = listOfNotNull(aspects?.strategy),
                mappedFrom = aspects?.from,
                serialName = serialNameOf(prop.annotationEntries),
                useConverter = aspects?.converter,
                ignored = aspects?.ignored ?: false,
            )
        }
        // Las properties del constructor también son fuentes/destinos direccionables:
        val ctorProperties = ctorParams.filter { it.hasValOrVar() }.map { param ->
            val aspects = mapFieldAspects(param.annotationEntries)
            MProperty(
                name = param.name ?: "<unnamed>",
                type = param.typeReference.toMType(),
                mutable = param.isMutable,
                inConstructor = true,
                strategies = listOfNotNull(aspects?.strategy),
                mappedFrom = aspects?.from,
                serialName = serialNameOf(param.annotationEntries),
                useConverter = aspects?.converter,
                ignored = aspects?.ignored ?: false,
            )
        }

        // Entries del enum con sus overrides `@MapEntry` y el fallback de sede
        // de clase — con ellos, KMX026/KMX047/KMX023 se vuelven decidibles en el editor.
        val isEnum = declaration is KtClass && declaration.isEnum()
        val enumEntries = if (isEnum) {
            declaration.declarations.filterIsInstance<KtEnumEntry>().map { entry ->
                MEnumEntry(
                    name = entry.name ?: "<unnamed>",
                    targetOverride = entry.annotationEntries
                        .firstOrNull { it.shortName?.asString() == "MapEntry" }
                        ?.let { MapFieldPsi.stringValue(it, "target") },
                )
            }
        } else {
            emptyList()
        }
        val enumFallback = if (isEnum) {
            declaration.annotationEntries.firstOrNull { it.shortName?.asString() == "MapEntry" }
                ?.let { MapFieldPsi.stringValue(it, "target") }
        } else {
            null
        }

        // Jerarquías SEALED: los subtipos declarados en el MISMO archivo (anidados en el cuerpo
        // o top-level — el patrón dominante). Subtipos en otros archivos no se ven: el resolver
        // se ABSTIENE cuando una jerarquía queda vacía, para no fabricar KMX010 falsos.
        val kind = declaration.kindOf()
        val sealedSubtypes =
            if (kind == TypeKind.SEALED_CLASS || kind == TypeKind.SEALED_INTERFACE) {
                sealedSubtypesOf(declaration).map { translate(it) }
            } else {
                emptyList()
            }

        return MClass(
            type = declaration.toMTypeRef(),
            properties = ctorProperties + bodyProperties,
            constructors = listOf(MConstructor(params = params, isPrimary = true, visible = true)),
            sealedSubtypes = sealedSubtypes,
            subtypeTargetOverride = subtypeOverrideOf(declaration),
            enumEntries = enumEntries,
            enumFallback = enumFallback,
        )
    }

    /**
     * Subtipos directos de una sealed: los del archivo (cuerpo + top-level, por texto) UNIDOS a
     * los del proyecto vía light classes (`DirectClassInheritorsSearch` — cubre el caso
     * cross-file del mismo paquete). Si ambos caminos fallan, la lista queda vacía y el
     * resolver se abstiene.
     */
    private fun sealedSubtypesOf(sealed: KtClassOrObject): List<KtClassOrObject> {
        val name = sealed.name ?: return emptyList()
        val nested = sealed.declarations.filterIsInstance<KtClassOrObject>()
        val topLevel = sealed.containingKtFile.declarations.filterIsInstance<KtClassOrObject>()
        val sameFile = (nested + topLevel).filter { candidate ->
            candidate !== sealed && candidate.superTypeListEntries.any { entry ->
                entry.typeReference?.text?.substringBefore('<')?.substringBefore('(')
                    ?.trim()?.substringAfterLast('.') == name
            }
        }
        val crossFile = runCatching {
            sealed.toLightClass()?.let { light ->
                com.intellij.psi.search.searches.DirectClassInheritorsSearch
                    .search(light, com.intellij.psi.search.GlobalSearchScope.projectScope(project))
                    .findAll()
                    .mapNotNull { (it as? KtLightClass)?.kotlinOrigin }
            }
        }.getOrNull().orEmpty()
        return (sameFile + crossFile).distinct()
    }

    /** `@MapSubtype(target = X::class)` sobre un subtipo SOURCE → qualified name del target. */
    private fun subtypeOverrideOf(declaration: KtClassOrObject): String? =
        declaration.annotationEntries.firstOrNull { it.shortName?.asString() == "MapSubtype" }
            ?.valueArguments?.firstOrNull()?.getArgumentExpression()?.text
            ?.removeSuffix("::class")?.substringAfterLast('.')
            ?.let { short ->
                MapFieldPsi.ownerClass(project, short)?.let {
                    val pkg = it.containingKtFile.packageFqName.asString()
                    if (pkg.isEmpty()) short else "$pkg.$short"
                }
            }

    /** `@SerialName("x")` — alias de matching; el motor lo consulta solo con `useSerialNames`. */
    private fun serialNameOf(entries: List<KtAnnotationEntry>): String? =
        entries.firstOrNull { it.shortName?.asString() == "SerialName" }
            ?.valueArguments?.firstOrNull()?.getArgumentExpression()?.text
            ?.removeSurrounding("\"")

    /** Los aspectos de `@MapField` en sede de campo — la conversión compartida de [MapFieldPsi]. */
    private fun mapFieldAspects(entries: List<KtAnnotationEntry>): MapFieldPsi.FieldAspects? =
        entries.firstOrNull { it.shortName?.asString() == "MapField" }?.let(MapFieldPsi::aspectsOf)

    // ── Tipos: texto del PSI → MType con la MISMA clasificación que los otros adapters ──

    /** El MType de una referencia de tipo suelta — lo consume [ProjectMappingIndex] (converters). */
    fun typeOf(reference: KtTypeReference?): MType = reference.toMType()

    private fun KtTypeReference?.toMType(): MType {
        val text = this?.text?.trim() ?: return MType("unresolved.Unknown", nullable = false)
        val nullable = text.endsWith("?")
        val bare = text.removeSuffix("?")
        val rawName = bare.substringBefore('<').trim()
        val typeArgs = typeArgumentsOf(bare)
        return mTypeFor(rawName, nullable, typeArgs)
    }

    private fun typeArgumentsOf(bare: String): List<MType> {
        val inner = bare.substringAfter('<', "").substringBeforeLast('>', "")
        if (inner.isBlank()) return emptyList()
        // split de primer nivel (respeta genéricos anidados):
        val args = mutableListOf<String>()
        var depth = 0
        val current = StringBuilder()
        for (ch in inner) {
            when (ch) {
                '<' -> { depth++; current.append(ch) }
                '>' -> { depth--; current.append(ch) }
                ',' -> if (depth == 0) { args += current.toString(); current.clear() } else current.append(ch)
                else -> current.append(ch)
            }
        }
        if (current.isNotBlank()) args += current.toString()
        return args.map { arg ->
            val t = arg.trim()
            val nullable = t.endsWith("?")
            val b = t.removeSuffix("?")
            mTypeFor(b.substringBefore('<').trim(), nullable, typeArgumentsOf(b))
        }
    }

    private fun mTypeFor(shortName: String, nullable: Boolean, typeArgs: List<MType>): MType {
        KOTLIN_TYPES[shortName]?.let { (qn, kind) ->
            return MType(qn, nullable, kind, typeArgs)
        }
        val resolved = MapFieldPsi.ownerClass(project, shortName)
        val qualified = resolved?.let {
            val pkg = it.containingKtFile.packageFqName.asString()
            if (pkg.isEmpty()) shortName else "$pkg.$shortName"
        }
            // Tipos del RUNTIME de kmapx (viven en un jar, no en el proyecto): sin este mapeo,
            // `Patch<T>` degradaría a `unresolved.Patch` y el motor no vería el tri-estado.
            ?: RUNTIME_TYPES[shortName]
            ?: "unresolved.$shortName"
        val kind = resolved?.kindOf() ?: TypeKind.OTHER
        return MType(qualified, nullable, kind, typeArgs, underlying = resolved.underlyingOf(kind))
    }

    /** Solo para VALUE_CLASS: el tipo de la única property del constructor — sin él, el motor
     *  no puede decidir el wrap/unwrap y degrada a un KMX004 falso. */
    private fun KtClassOrObject?.underlyingOf(kind: TypeKind): MType? =
        if (kind == TypeKind.VALUE_CLASS) {
            this?.primaryConstructor?.valueParameters?.singleOrNull()?.typeReference.toMType()
        } else {
            null
        }

    private fun KtClassOrObject.toMTypeRef(): MType {
        val pkg = containingKtFile.packageFqName.asString()
        val name = name ?: "Unknown"
        val kind = kindOf()
        return MType(
            qualifiedName = if (pkg.isEmpty()) name else "$pkg.$name",
            nullable = false,
            kind = kind,
            underlying = underlyingOf(kind),
            packageName = pkg,
        )
    }

    private fun KtClassOrObject.kindOf(): TypeKind = when {
        hasModifier(KtTokens.VALUE_KEYWORD) -> TypeKind.VALUE_CLASS
        hasModifier(KtTokens.SEALED_KEYWORD) && this is KtClass && isInterface() -> TypeKind.SEALED_INTERFACE
        hasModifier(KtTokens.SEALED_KEYWORD) -> TypeKind.SEALED_CLASS
        this is KtClass && isEnum() -> TypeKind.ENUM
        // OBJECT antes que DATA (un `data object` es OBJECT) — igual que KspTranslator.
        this is KtObjectDeclaration -> TypeKind.OBJECT
        hasModifier(KtTokens.DATA_KEYWORD) -> TypeKind.DATA_CLASS
        else -> TypeKind.REGULAR_CLASS
    }

    private fun KtModifierListOwner.hasModifier(token: org.jetbrains.kotlin.lexer.KtModifierKeywordToken): Boolean =
        modifierList?.hasModifier(token) == true

    private companion object {
        /** Los tipos kotlin que el motor distingue — misma tabla conceptual que los otros adapters. */
        val KOTLIN_TYPES: Map<String, Pair<String, TypeKind>> = buildMap {
            for (scalar in listOf("String", "Int", "Long", "Short", "Byte", "Double", "Float", "Boolean", "Char", "Any", "Unit")) {
                put(scalar, "kotlin.$scalar" to TypeKind.REGULAR_CLASS)
            }
            put("List", "kotlin.collections.List" to TypeKind.COLLECTION_LIST)
            put("Set", "kotlin.collections.Set" to TypeKind.COLLECTION_SET)
            put("Map", "kotlin.collections.Map" to TypeKind.COLLECTION_MAP)
            put("Collection", "kotlin.collections.Collection" to TypeKind.COLLECTION_ITERABLE)
            put("Iterable", "kotlin.collections.Iterable" to TypeKind.COLLECTION_ITERABLE)
            put("Sequence", "kotlin.sequences.Sequence" to TypeKind.COLLECTION_SEQUENCE)
            put("Array", "kotlin.Array" to TypeKind.COLLECTION_ARRAY)
            put("Result", "kotlin.Result" to TypeKind.RESULT)
        }

        /** Tipos de `dev.kmapx.runtime` — resolubles por nombre corto cuando el proyecto no los pisa. */
        val RUNTIME_TYPES: Map<String, String> = mapOf(
            "Patch" to "dev.kmapx.runtime.Patch",
            "Converts" to "dev.kmapx.runtime.Converts",
        )
    }
}
