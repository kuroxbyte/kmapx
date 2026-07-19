package dev.kmapx.intellij

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import dev.kmapx.intellij.MapFieldPsi.usesKmapx
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtValueArgument

/**
 * El estado COLABORATIVO del proyecto para el motor en el editor:
 * mapeos declarados (`@MapTo`/`@BiMapTo` de clases top-level) y `@Converter`s top-level,
 * escaneados del PSI y CACHEADOS por [PsiModificationTracker] (se recalcula solo cuando el PSI
 * del proyecto cambia). Con este índice, KMX004/KMX007/KMX009 se vuelven seguros en el editor.
 *
 * Paridad consciente con el build: KSP indexa POR MÓDULO; aquí se ve el proyecto entero — en
 * multi-módulo el editor puede CALLAR un error real del build (converter de otro módulo), pero
 * jamás marcar de más. La dirección segura.
 */
internal object ProjectMappingIndex {

    data class Snapshot(
        /** (sourceQn, targetQn) → función de extension calificada — el formato del motor. */
        val declaredMappings: Map<Pair<String, String>, String>,
        /** (fromQn, toQn) → FQNs de `@Converter` (lista: el motor detecta KMX009). */
        val converters: Map<Pair<String, String>, List<String>>,
    )

    fun of(project: Project): Snapshot =
        CachedValuesManager.getManager(project).getCachedValue(project) {
            CachedValueProvider.Result.create(scan(project), PsiModificationTracker.MODIFICATION_COUNT)
        }

    private fun scan(project: Project): Snapshot {
        val declared = mutableMapOf<Pair<String, String>, String>()
        val converters = mutableMapOf<Pair<String, String>, MutableList<String>>()
        val psiManager = PsiManager.getInstance(project)
        val adapter = PsiAdapter(project)

        for (virtualFile in FileTypeIndex.getFiles(KotlinFileType.INSTANCE, GlobalSearchScope.projectScope(project))) {
            val file = psiManager.findFile(virtualFile) as? KtFile ?: continue
            if (!file.usesKmapx()) continue
            val pkg = file.packageFqName.asString()
            fun qualified(name: String) = if (pkg.isEmpty()) name else "$pkg.$name"

            for (declaration in file.declarations) {
                when (declaration) {
                    is KtClassOrObject -> indexMappings(project, declaration, ::qualified, declared)
                    is KtNamedFunction -> indexConverter(declaration, adapter, ::qualified, converters)
                    else -> Unit
                }
            }
        }
        return Snapshot(declared, converters)
    }

    private fun indexMappings(
        project: Project,
        declaration: KtClassOrObject,
        qualified: (String) -> String,
        declared: MutableMap<Pair<String, String>, String>,
    ) {
        val sourceName = declaration.name ?: return
        val sourceQn = qualified(sourceName)
        for (annotation in declaration.annotationEntries) {
            val kind = annotation.shortName?.asString()
            if (kind != "MapTo" && kind != "BiMapTo") continue
            val targetShort = (annotation.valueArguments.firstOrNull() as? KtValueArgument)
                ?.getArgumentExpression()?.text?.removeSuffix("::class")?.substringAfterLast('.') ?: continue
            val target = MapFieldPsi.ownerClass(project, targetShort) ?: continue
            val targetPkg = target.containingKtFile.packageFqName.asString()
            val targetQn = if (targetPkg.isEmpty()) targetShort else "$targetPkg.$targetShort"

            val functionName = MapFieldPsi.stringValue(annotation, "name")?.takeIf { it.isNotEmpty() }
                ?: "to$targetShort"
            declared[sourceQn to targetQn] = qualified(functionName)
            if (kind == "BiMapTo") {
                val reverseName = MapFieldPsi.stringValue(annotation, "reverseName")?.takeIf { it.isNotEmpty() }
                    ?: "to$sourceName"
                declared[targetQn to sourceQn] = qualified(reverseName)
            }
        }
    }

    private fun indexConverter(
        function: KtNamedFunction,
        adapter: PsiAdapter,
        qualified: (String) -> String,
        converters: MutableMap<Pair<String, String>, MutableList<String>>,
    ) {
        if (function.annotationEntries.none { it.shortName?.asString() == "Converter" }) return
        val functionName = function.name ?: return
        val parameter = function.valueParameters.singleOrNull() ?: return
        val from = adapter.typeOf(parameter.typeReference)
        val to = adapter.typeOf(function.typeReference)
        converters.getOrPut(from.qualifiedName to to.qualifiedName) { mutableListOf() } += qualified(functionName)
    }
}
