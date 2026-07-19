package dev.kmapx.intellij

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import dev.kmapx.core.diagnostics.Diagnostic
import dev.kmapx.core.diagnostics.Diagnostics
import dev.kmapx.core.diagnostics.MLocation
import dev.kmapx.core.diagnostics.MapFieldRules
import dev.kmapx.core.diagnostics.Severity
import dev.kmapx.core.diagnostics.Suggestions
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.KtValueArgumentList
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType

/**
 * Los diagnósticos KMX de `@MapField` que son LOCALMENTE decidibles, en el
 * editor y con quick-fixes. Las reglas NO viven aquí: son [MapFieldRules] del core — la misma
 * fuente que ejecuta el compilador (cero duplicación); los mensajes salen de las MISMAS
 * factories de [Diagnostics]. Cubre KMX036/038/039/043 (coherencia) y el "target/from no
 * existe" con did-you-mean ([Suggestions], también del core).
 *
 * Los diagnósticos que dependen de estado CROSS-proyecto (KMX003, converters, anidados…)
 * siguen llegando por el build — su inspección exige el `adapter-psi` completo (v2).
 */
class MapFieldInspection : LocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        object : PsiElementVisitor() {
            override fun visitElement(element: PsiElement) {
                if (element is KtAnnotationEntry && MapFieldPsi.isMapField(element)) {
                    checkCoherence(element, holder)
                    checkUnresolvedNames(element, holder)
                }
            }
        }

    private fun checkCoherence(entry: KtAnnotationEntry, holder: ProblemsHolder) {
        val site = MapFieldPsi.siteOf(entry) ?: return
        val declaration = MapFieldPsi.declarationOf(entry)
        val location = MLocation(entry.containingKtFile.name)
        val fieldName = MapFieldPsi.stringValue(entry, "target") ?: "campo"

        MapFieldRules.check(site, declaration).forEach { violation ->
            val (diagnostic, fixes) = when (violation) {
                MapFieldRules.Violation.BAD_ADDRESSING ->
                    Diagnostics.mapFieldBadAddressing(location, methodSite = site == MapFieldRules.Site.METHOD) to
                        if (site == MapFieldRules.Site.FIELD) listOf(RemoveArgumentFix("target")) else emptyList()
                MapFieldRules.Violation.LITERAL_WITHOUT_DEFAULT ->
                    Diagnostics.literalRequiresDefault(location, fieldName) to
                        listOf(AddArgumentFix("""default = """""))
                MapFieldRules.Violation.DEFAULT_IGNORED ->
                    Diagnostics.defaultIgnored(location, fieldName) to
                        listOf(RemoveArgumentFix("default"))
                MapFieldRules.Violation.IGNORE_CONFLICTS ->
                    Diagnostics.ignoreConflictsWithAspects(location, fieldName) to
                        listOf(RemoveArgumentFix("ignore"))
            }
            holder.registerProblem(entry, diagnostic.presentation(), diagnostic.highlight(), *fixes.toTypedArray())
        }
    }

    /**
     * `target=`/`from=` con un nombre (o RUTA `a.b.c`) que no existe en su tipo →
     * did-you-mean sobre el PRIMER segmento irresoluble, con quick-fix de reemplazo.
     */
    private fun checkUnresolvedNames(entry: KtAnnotationEntry, holder: ProblemsHolder) {
        if (MapFieldPsi.siteOf(entry) != MapFieldRules.Site.METHOD) return
        val method = entry.getParentOfType<org.jetbrains.kotlin.psi.KtNamedFunction>(strict = true) ?: return

        for (argName in listOf("target", "from")) {
            val value = MapFieldPsi.stringValue(entry, argName) ?: continue
            if (value.isEmpty()) continue
            // `target` direcciona un campo PLANO (las rutas son cosa del `from`).
            if (argName == "target" && '.' in value) continue
            val typeName = MapFieldPsi.ownerTypeName(method, argName) ?: continue

            // v0.6: se valida SEGMENTO a segmento — el primero irresoluble reporta (el resto de
            // la ruta depende de él). Un dueño no localizable corta sin reportar (heurística).
            val segments = value.split('.')
            for (index in segments.indices) {
                val owner = MapFieldPsi.ownerAtSegment(entry.project, typeName, segments, index) ?: break
                val names = MapFieldPsi.addressableNames(owner)
                val segment = segments[index]
                if (segment in names) continue

                val ownerName = owner.name ?: typeName
                val suggestions = Suggestions.closest(segment, names)
                val diagnostic = when (argName) {
                    "target" -> Diagnostics.methodTargetMissing(
                        MLocation(ownerName), segment, ownerName, suggestions,
                    )
                    else -> Diagnostics.renamedSourceMissing(
                        MLocation(ownerName), segment, segment, suggestions, on = ownerName,
                    )
                }
                val anchor = MapFieldPsi.argument(entry, argName)?.getArgumentExpression() ?: entry
                holder.registerProblem(
                    anchor, diagnostic.presentation(), diagnostic.highlight(),
                    *suggestions.map { ReplaceSegmentFix(it, index) }.toTypedArray(),
                )
                break
            }
        }
    }

    private fun Diagnostic.presentation(): String = "[${code.id}] $message Fix: $fix"

    private fun Diagnostic.highlight(): ProblemHighlightType =
        if (severity == Severity.WARNING) ProblemHighlightType.WARNING
        else ProblemHighlightType.GENERIC_ERROR
}

/** Agrega un argumento a la `@MapField` (p. ej. `default = ""`). */
private class AddArgumentFix(private val argumentText: String) : LocalQuickFix {
    override fun getFamilyName(): String = "kmapx: agregar $argumentText"
    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val entry = descriptor.psiElement as? KtAnnotationEntry ?: return
        entry.valueArgumentList?.addArgument(KtPsiFactory(project).createArgument(argumentText))
    }
}

/** Quita un argumento nombrado de la `@MapField` (target/default/ignore…). */
private class RemoveArgumentFix(private val argumentName: String) : LocalQuickFix {
    override fun getFamilyName(): String = "kmapx: quitar $argumentName"
    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val entry = descriptor.psiElement as? KtAnnotationEntry ?: return
        val argument = MapFieldPsi.argument(entry, argumentName) ?: return
        (argument.parent as? KtValueArgumentList)?.removeArgument(argument)
    }
}

/** El did-you-mean aplicado: reemplaza el SEGMENTO [index] de la ruta por el candidato (v0.6). */
private class ReplaceSegmentFix(private val replacement: String, private val index: Int) : LocalQuickFix {
    override fun getFamilyName(): String = "kmapx: cambiar a \"$replacement\""
    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val string = descriptor.psiElement as? KtStringTemplateExpression ?: return
        val segments = string.entries.singleOrNull()?.text?.split('.')?.toMutableList() ?: return
        if (index !in segments.indices) return
        segments[index] = replacement
        string.replace(KtPsiFactory(project).createExpression("\"${segments.joinToString(".")}\""))
    }
}
