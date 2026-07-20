package dev.kmapx.intellij

import com.intellij.openapi.util.TextRange
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceBase
import com.intellij.psi.PsiReferenceContributor
import com.intellij.psi.PsiReferenceProvider
import com.intellij.psi.PsiReferenceRegistrar
import com.intellij.util.ProcessingContext
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType

/**
 * Los strings de las anotaciones kmapx dejan de ser strings mudos:
 *
 *  - `@MapField(target = "campo")` → propiedad del TIPO DE RETORNO del método;
 *  - `@MapField(from = "a.b.c")`   → una referencia POR SEGMENTO, navegando los tipos (v0.6);
 *  - `@MapTo(ignore = ["campo"])` / `@Mapper(ignore = [...])` → propiedad del target (v0.6) —
 *    con la referencia, el RENAME de la propiedad actualiza el string (antes se rompía mudo);
 *  - `@MapEntry(target = "ENTRY")` → el entry del enum DESTINO del `@MapTo`/`@BiMapTo` de la
 *    clase contenedora (v0.7);
 *  - `@MapField(from = ...)` en SEDE DE CAMPO (el DTO): el source se localiza por el índice
 *    INVERSO del proyecto ([ProjectMappingIndex]) — quién mapea hacia esta clase (v0.7).
 *
 * Con cada referencia vienen gratis: Ctrl+B, find-usages y el completado dentro de las comillas.
 * Los tipos se localizan por nombre corto vía light classes — heurística consciente.
 */
class MapFieldReferenceContributor : PsiReferenceContributor() {

    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        registrar.registerReferenceProvider(
            PlatformPatterns.psiElement(KtStringTemplateExpression::class.java),
            KmapxStringProvider(),
        )
    }
}

private class KmapxStringProvider : PsiReferenceProvider() {

    override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
        val string = element as? KtStringTemplateExpression ?: return PsiReference.EMPTY_ARRAY
        // 0 entries = string VACÍO ("") — el momento exacto en que el usuario invoca el
        // completado; sin la referencia ahí, no habría sugerencias hasta escribir algo.
        if (string.entries.size > 1 || string.hasInterpolation()) return PsiReference.EMPTY_ARRAY

        val argument = string.getParentOfType<KtValueArgument>(strict = true) ?: return PsiReference.EMPTY_ARRAY
        val annotation = argument.getParentOfType<KtAnnotationEntry>(strict = true) ?: return PsiReference.EMPTY_ARRAY

        return when (annotation.shortName?.asString()) {
            "MapField" -> mapFieldReferences(string, argument, annotation)
            "MapTo", "Mapper" -> ignoreReferences(string, argument, annotation)
            "MapEntry" -> mapEntryReferences(string, annotation)
            "InverseOf" -> inverseReferences(string, annotation)
            else -> PsiReference.EMPTY_ARRAY
        }
    }

    private fun mapFieldReferences(
        string: KtStringTemplateExpression,
        argument: KtValueArgument,
        annotation: KtAnnotationEntry,
    ): Array<PsiReference> {
        // `target` = tipo de retorno; `from` = tipo del primer parámetro. `target` admite forma
        // posicional (es el primer parámetro de @MapField); `from` siempre nombrado.
        val argName = argument.getArgumentName()?.asName?.asString()
            ?: "target".takeIf { (annotation.valueArguments.firstOrNull() as? KtValueArgument) == argument }
            ?: return PsiReference.EMPTY_ARRAY

        val method = annotation.getParentOfType<KtNamedFunction>(strict = true)
        val ownerTypeNames: List<String> = if (method != null) {
            // Sede de MÉTODO: el tipo dueño sale de la firma.
            listOfNotNull(MapFieldPsi.ownerTypeName(method, argName))
        } else {
            // v0.7 — sede de CAMPO (el DTO): solo `from` direcciona algo, y su dueño es QUIEN
            // MAPEA HACIA esta clase — el índice inverso del proyecto lo sabe.
            if (argName != "from") return PsiReference.EMPTY_ARRAY
            val owner = annotation.getParentOfType<KtClassOrObject>(strict = true)
                ?.let { if (it is org.jetbrains.kotlin.psi.KtEnumEntry) null else it }
                ?: return PsiReference.EMPTY_ARRAY
            val pkg = owner.containingKtFile.packageFqName.asString()
            val ownerQn = owner.name?.let { if (pkg.isEmpty()) it else "$pkg.$it" }
                ?: return PsiReference.EMPTY_ARRAY
            ProjectMappingIndex.of(owner.project).declaredMappings.keys
                .filter { it.second == ownerQn }
                .map { it.first.substringAfterLast('.') }
                .distinct()
        }
        if (ownerTypeNames.isEmpty()) return PsiReference.EMPTY_ARRAY

        // Una referencia POR SEGMENTO de la ruta — cada una navega su tipo dueño
        // (string vacío = un único segmento vacío: la referencia existe SOLO para completar).
        val segments = (string.entries.singleOrNull()?.text ?: "").split('.')
        var offset = 1 // dentro de las comillas
        return segments.mapIndexed { index, segment ->
            val range = TextRange(offset, offset + segment.length)
            offset += segment.length + 1
            PathSegmentReference(string, range, ownerTypeNames, segments, index)
        }.toTypedArray()
    }

    /** v0.7 — el string de `@MapEntry(target=)` referencia el ENTRY del enum destino. */
    private fun mapEntryReferences(
        string: KtStringTemplateExpression,
        annotation: KtAnnotationEntry,
    ): Array<PsiReference> {
        // Sede de entry: el padre-clase inmediato ES el KtEnumEntry; el enum es SU contenedor.
        val parent = annotation.getParentOfType<KtClassOrObject>(strict = true) ?: return PsiReference.EMPTY_ARRAY
        val enumClass = if (parent is org.jetbrains.kotlin.psi.KtEnumEntry) {
            parent.getParentOfType<KtClassOrObject>(strict = true)
        } else {
            parent
        } ?: return PsiReference.EMPTY_ARRAY

        // El enum destino: el class-literal de los @MapTo/@BiMapTo del enum contenedor.
        val targetShortNames = enumClass.annotationEntries
            .filter { it.shortName?.asString() == "MapTo" || it.shortName?.asString() == "BiMapTo" }
            .mapNotNull { ann ->
                (ann.valueArguments.firstOrNull() as? KtValueArgument)
                    ?.getArgumentExpression()?.text?.removeSuffix("::class")?.substringAfterLast('.')
            }
        if (targetShortNames.isEmpty()) return PsiReference.EMPTY_ARRAY
        return arrayOf(EnumEntryReference(string, targetShortNames))
    }

    /**
     * El string de `@InverseOf("toX")` referencia el método FORWARD del mismo mapper — con la
     * referencia, ctrl+click navega, el rename del forward actualiza el string, y el completado
     * ofrece SOLO los candidatos con la firma inversa exacta (el resto sería KMX045).
     */
    private fun inverseReferences(
        string: KtStringTemplateExpression,
        annotation: KtAnnotationEntry,
    ): Array<PsiReference> {
        val method = annotation.getParentOfType<KtNamedFunction>(strict = true) ?: return PsiReference.EMPTY_ARRAY
        val mapper = method.getParentOfType<KtClassOrObject>(strict = true) ?: return PsiReference.EMPTY_ARRAY
        if (mapper.annotationEntries.none { it.shortName?.asString() == "Mapper" }) return PsiReference.EMPTY_ARRAY
        return arrayOf(InverseForwardReference(string))
    }

    /** v0.6: los strings de `ignore = [...]` referencian la propiedad del TARGET que excluyen. */
    private fun ignoreReferences(
        string: KtStringTemplateExpression,
        argument: KtValueArgument,
        annotation: KtAnnotationEntry,
    ): Array<PsiReference> {
        if (argument.getArgumentName()?.asName?.asString() != "ignore") return PsiReference.EMPTY_ARRAY
        val owners: List<String> = when (annotation.shortName?.asString()) {
            // @MapTo: el target es el class-literal del primer argumento.
            "MapTo" -> listOfNotNull(
                (annotation.valueArguments.firstOrNull() as? KtValueArgument)
                    ?.getArgumentExpression()?.text?.removeSuffix("::class")?.substringAfterLast('.'),
            )
            // @Mapper: los targets son HETEROGÉNEOS — los tipos de retorno de sus métodos.
            else -> {
                val mapper = annotation.getParentOfType<KtClassOrObject>(strict = true) as? KtClass
                mapper?.declarations?.filterIsInstance<KtNamedFunction>()
                    ?.mapNotNull { it.typeReference?.text?.substringBefore('<')?.removeSuffix("?") }
                    ?.distinct().orEmpty()
            }
        }
        if (owners.isEmpty()) return PsiReference.EMPTY_ARRAY
        return arrayOf(IgnoredFieldReference(string, owners))
    }
}

/**
 * Un SEGMENTO de una ruta `a.b.c` (los nombres planos son la ruta de un segmento): [resolve]
 * navega los tipos de los segmentos previos y localiza el campo homónimo; [getVariants]
 * completa con los nombres direccionables del dueño de ESTE segmento.
 */
private class PathSegmentReference(
    element: KtStringTemplateExpression,
    range: TextRange,
    /** v0.7: puede haber VARIOS candidatos a raíz (sede de campo, índice inverso) — gana el primero que navega. */
    private val rootTypeNames: List<String>,
    private val segments: List<String>,
    private val index: Int,
) : PsiReferenceBase<KtStringTemplateExpression>(element, range) {

    private fun owner(): KtClassOrObject? = rootTypeNames.firstNotNullOfOrNull { root ->
        MapFieldPsi.ownerAtSegment(element.project, root, segments, index)
    }

    override fun resolve(): PsiElement? = owner()?.let { MapFieldPsi.resolveField(it, segments[index]) }

    override fun getVariants(): Array<Any> =
        owner()?.let { MapFieldPsi.addressableNames(it).toTypedArray<Any>() } ?: emptyArray()
}

/** Un string de `ignore = [...]` → el campo del target (el primero que matchee entre los dueños). */
private class IgnoredFieldReference(
    element: KtStringTemplateExpression,
    private val ownerTypeNames: List<String>,
) : PsiReferenceBase<KtStringTemplateExpression>(
    element,
    TextRange(1, element.textLength - 1),
) {

    private fun owners(): List<KtClassOrObject> =
        ownerTypeNames.mapNotNull { MapFieldPsi.ownerClass(element.project, it) }

    override fun resolve(): PsiElement? {
        val name = element.entries.singleOrNull()?.text ?: return null
        return owners().firstNotNullOfOrNull { MapFieldPsi.resolveField(it, name) }
    }

    override fun getVariants(): Array<Any> =
        owners().flatMap { MapFieldPsi.addressableNames(it) }.distinct().toTypedArray<Any>()
}

/** `@InverseOf("toX")` → el método forward del mapper (candidatos: firma inversa exacta). */
private class InverseForwardReference(
    element: KtStringTemplateExpression,
) : PsiReferenceBase<KtStringTemplateExpression>(
    element,
    TextRange(1, maxOf(1, element.textLength - 1)),
) {

    private fun context(): Pair<KtNamedFunction, List<KtNamedFunction>>? {
        val inverse = element.getParentOfType<KtNamedFunction>(strict = true) ?: return null
        val mapper = inverse.getParentOfType<KtClassOrObject>(strict = true) ?: return null
        val abstracts = mapper.declarations.filterIsInstance<KtNamedFunction>().filter { !it.hasBody() }
        return inverse to abstracts
    }

    private fun shortOf(text: String?): String? =
        text?.substringBefore('<')?.removeSuffix("?")?.substringAfterLast('.')?.takeIf { it.isNotEmpty() }

    /** Los forwards VÁLIDOS: firma inversa exacta, ni el propio método ni otro @InverseOf. */
    private fun candidates(): List<KtNamedFunction> {
        val (inverse, abstracts) = context() ?: return emptyList()
        val paramShort = shortOf(inverse.valueParameters.firstOrNull()?.typeReference?.text)
        val returnShort = shortOf(inverse.typeReference?.text)
        return abstracts.filter { fn ->
            fn !== inverse &&
                fn.annotationEntries.none { it.shortName?.asString() == "InverseOf" } &&
                fn.valueParameters.size == 1 &&
                shortOf(fn.valueParameters[0].typeReference?.text) == returnShort &&
                shortOf(fn.typeReference?.text) == paramShort
        }
    }

    override fun resolve(): PsiElement? {
        val name = element.entries.singleOrNull()?.text ?: return null
        val (_, abstracts) = context() ?: return null
        return abstracts.firstOrNull { it.name == name }
    }

    override fun getVariants(): Array<Any> =
        candidates().mapNotNull { it.name }.toTypedArray<Any>()
}

/** v0.7 — el string de `@MapEntry(target=)` → el entry homónimo del enum destino. */
private class EnumEntryReference(
    element: KtStringTemplateExpression,
    private val targetShortNames: List<String>,
) : PsiReferenceBase<KtStringTemplateExpression>(
    element,
    TextRange(1, element.textLength - 1),
) {

    private fun targets(): List<KtClassOrObject> =
        targetShortNames.mapNotNull { MapFieldPsi.ownerClass(element.project, it) }

    override fun resolve(): PsiElement? {
        val name = element.entries.singleOrNull()?.text ?: return null
        return targets().firstNotNullOfOrNull { MapFieldPsi.resolveEnumEntry(it, name) }
    }

    override fun getVariants(): Array<Any> =
        targets().flatMap { MapFieldPsi.enumEntryNames(it) }.distinct().toTypedArray<Any>()
}
