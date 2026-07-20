package dev.kmapx.intellij

import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerProvider
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction

/**
 * Gutter icons de kmapx, ahora a nivel FUNCIÓN:
 *
 *  - clase `@MapTo`/`@BiMapTo` (embedded) → las extension functions generadas en
 *    `<Source>Mappings.kt` cuyo receiver es la clase (una por mapeo declarado);
 *  - interfaz `@Mapper` (contract) → la declaración de `<Interface>Impl`;
 *  - MÉTODO abstracto de un `@Mapper` → su `override fun` en el Impl.
 *
 * Detección pragmática sin resolve de PSI (nombre corto + import `dev.kmapx.annotations` en el
 * archivo — cero costo de análisis en el pintado). Si el proyecto aún no compiló no hay marker:
 * mejor ausencia que un salto roto. Fallback: si el archivo generado existe pero la búsqueda
 * fina no encuentra la declaración, se navega al archivo.
 */
class GeneratedMappingLineMarkerProvider : RelatedItemLineMarkerProvider() {

    override fun collectNavigationMarkers(
        element: PsiElement,
        result: MutableCollection<in RelatedItemLineMarkerInfo<*>>,
    ) {
        when (val declaration = element.parent) {
            is KtClassOrObject ->
                if (element == declaration.nameIdentifier) {
                    classMarker(element, declaration, result)
                    reverseMarker(element, declaration, result)
                }
            is KtNamedFunction ->
                if (element == declaration.nameIdentifier) methodMarker(element, declaration, result)
        }
    }

    /**
     * v0.7 — navegación INVERSA: sobre un TARGET (el DTO, que típicamente ni importa kmapx),
     * un marker hacia las clases source cuyos `@MapTo`/`@BiMapTo` lo producen — la respuesta
     * a "¿quién construye este tipo?" con un clic. Los datos salen del índice del proyecto.
     */
    private fun reverseMarker(
        element: PsiElement,
        declaration: KtClassOrObject,
        result: MutableCollection<in RelatedItemLineMarkerInfo<*>>,
    ) {
        val name = declaration.name ?: return
        val pkg = declaration.containingKtFile.packageFqName.asString()
        val qualified = if (pkg.isEmpty()) name else "$pkg.$name"
        val producers = ProjectMappingIndex.of(element.project).declaredMappings.keys
            .filter { it.second == qualified }
            .mapNotNull { (sourceQn, _) ->
                MapFieldPsi.ownerClass(element.project, sourceQn.substringAfterLast('.'))
            }
            .distinct()
        if (producers.isEmpty()) return
        result.add(
            NavigationGutterIconBuilder.create(KmapxIcons.FromGenerated)
                .setTargets(producers)
                .setTooltipText("kmapx: mapeos que producen este tipo")
                .createLineMarkerInfo(element),
        )
    }

    private fun classMarker(
        element: PsiElement,
        declaration: KtClassOrObject,
        result: MutableCollection<in RelatedItemLineMarkerInfo<*>>,
    ) {
        if (!declaration.containingKtFile.usesKmapx()) return
        val shortNames = declaration.annotationShortNames()
        val className = declaration.name ?: return

        val targets: List<PsiElement> = when {
            "MapTo" in shortNames || "BiMapTo" in shortNames -> {
                val file = findGeneratedFile(element, "${className}Mappings.kt") ?: return
                // Las funciones raíz del archivo: receiver == la clase anotada (las sub-funciones
                // de sealed tienen receiver `Clase.Subtipo` y no matchean — a propósito).
                generatedFunctions(file) { it.receiverTypeReference?.text == className }
                    .ifEmpty { listOf(file) }
            }
            // MapperConfig no genera código; su shortName no es "Mapper" — excluido por igualdad.
            "Mapper" in shortNames -> {
                val file = findGeneratedFile(element, "${className}Impl.kt") ?: return
                implDeclaration(file, "${className}Impl")?.let { listOf(it) } ?: listOf(file)
            }
            else -> return
        }
        result.add(marker(element, targets, "kmapx: ir al código generado"))
    }

    /** Método abstracto de una interfaz `@Mapper` → su `override fun` homónima en el Impl. */
    private fun methodMarker(
        element: PsiElement,
        method: KtNamedFunction,
        result: MutableCollection<in RelatedItemLineMarkerInfo<*>>,
    ) {
        if (method.hasBody()) return // los métodos default (after<Método>) no generan override
        val owner = method.containingClassOrObject() ?: return
        if ("Mapper" !in owner.annotationShortNames()) return
        if (!method.containingKtFile.usesKmapx()) return

        val ownerName = owner.name ?: return
        val file = findGeneratedFile(element, "${ownerName}Impl.kt") ?: return
        val impl = implDeclaration(file, "${ownerName}Impl") ?: return
        val override = impl.declarations
            .filterIsInstance<KtNamedFunction>()
            .firstOrNull { it.name == method.name }
            ?: return
        result.add(marker(element, listOf(override), "kmapx: ir a la implementación generada"))
    }

    private fun marker(
        element: PsiElement,
        targets: List<PsiElement>,
        tooltip: String,
    ): RelatedItemLineMarkerInfo<*> =
        NavigationGutterIconBuilder.create(KmapxIcons.ToGenerated)
            .setTargets(targets)
            .setTooltipText(tooltip)
            .createLineMarkerInfo(element)

    private fun findGeneratedFile(context: PsiElement, name: String): KtFile? =
        FilenameIndex.getVirtualFilesByName(name, GlobalSearchScope.projectScope(context.project))
            .mapNotNull { PsiManager.getInstance(context.project).findFile(it) }
            .filterIsInstance<KtFile>()
            .firstOrNull()

    private fun generatedFunctions(file: KtFile, filter: (KtNamedFunction) -> Boolean): List<PsiElement> =
        file.declarations.filterIsInstance<KtNamedFunction>().filter(filter)

    private fun implDeclaration(file: KtFile, name: String): KtClassOrObject? =
        file.declarations.filterIsInstance<KtClassOrObject>().firstOrNull { it.name == name }

    private fun KtNamedFunction.containingClassOrObject(): KtClassOrObject? =
        parent?.parent as? KtClassOrObject // fun → KtClassBody → KtClassOrObject

    private fun PsiFile.usesKmapx(): Boolean =
        (this as? KtFile)?.importDirectives?.any {
            it.importedFqName?.asString()?.startsWith("dev.kmapx.annotations") == true
        } == true

    private fun KtClassOrObject.annotationShortNames(): Set<String> =
        annotationEntries.mapNotNull { it.shortName?.asString() }.toSet()
}
