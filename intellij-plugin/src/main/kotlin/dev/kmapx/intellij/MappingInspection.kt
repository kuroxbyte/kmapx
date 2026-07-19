package dev.kmapx.intellij

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.SmartPointerManager
import dev.kmapx.core.diagnostics.Diagnostic
import dev.kmapx.core.diagnostics.DiagnosticCode
import dev.kmapx.core.diagnostics.Severity
import dev.kmapx.core.diagnostics.Suggestions
import dev.kmapx.intellij.MapFieldPsi.usesKmapx
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtEnumEntry
import org.jetbrains.kotlin.psi.KtModifierListOwner
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

/**
 * El MISMO `MappingEngine` del compilador, corriendo en el editor sobre
 * la resolución compartida de [EditorMappingResolver] (ambos modos, con el estado del editor:
 * índice colaborativo + config global del build). FILTRA a los códigos seguros:
 *
 *  - **KMX002** (campo sin fuente) y **KMX003** (nulabilidad — la matriz decide ANTES que los
 *    converters): seguros desde v0.4.
 *  - **KMX004/KMX007/KMX009** (tipos incompatibles / anidado sin mapeo / converters ambiguos):
 *    seguros desde v0.6 gracias a [ProjectMappingIndex]. En multi-módulo el índice ve el
 *    proyecto entero (KSP indexa por módulo): puede callar de menos, jamás marcar de más.
 *  - **KMX026/KMX047/KMX023** (enums: entry sin par / fallback roto / entry extra del target,
 *    warning): seguros por definición desde v0.7 — comparar entries de dos enums no depende de
 *    ningún estado cross-proyecto.
 *
 * Los quick-fixes respetan la filosofía de cada modo: KMX003 anota el parámetro del TARGET en
 * embedded y el MÉTODO en contract (el dominio no se toca); KMX007 anota el tipo anidado del
 * SOURCE con el `@MapTo` que falta.
 */
class MappingInspection : LocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        object : PsiElementVisitor() {
            override fun visitElement(element: PsiElement) {
                if (element is KtClassOrObject && element.containingKtFile.usesKmapx()) {
                    checkMapTo(element, holder)
                    checkMapper(element, holder)
                }
            }
        }

    // ── Modo EMBEDDED: los `@MapTo` de una clase ──────────────────────────────────────────────

    private fun checkMapTo(source: KtClassOrObject, holder: ProblemsHolder) {
        for (resolved in EditorMappingResolver.mapTos(source)) {
            resolved.plan.diagnostics
                .filter { it.code in SAFE_CODES }
                .forEach { diagnostic ->
                    val member = diagnostic.location.member
                    val targetParam = member?.let { name ->
                        resolved.target.primaryConstructor?.valueParameters?.firstOrNull { it.name == name }
                    }
                    val fixes: List<LocalQuickFix> = when {
                        diagnostic.code == DiagnosticCode.KMX003 && targetParam != null -> listOf(
                            AnnotateFix(targetParam.pointer(), """@MapField(onNull = OnNull.THROW)""", MAP_FIELD_IMPORTS),
                            AnnotateFix(
                                targetParam.pointer(),
                                """@MapField(onNull = OnNull.LITERAL, default = "")""",
                                MAP_FIELD_IMPORTS,
                            ),
                        )
                        diagnostic.code == DiagnosticCode.KMX007 && member != null ->
                            listOfNotNull(nestedMapToFix(source, resolved.target, member))
                        // v0.7: los fixes de enums — anotar el entry sin par / reparar el fallback.
                        diagnostic.code == DiagnosticCode.KMX026 ->
                            listOfNotNull(mapEntryFix(source, resolved.target, diagnostic))
                        diagnostic.code == DiagnosticCode.KMX047 ->
                            listOfNotNull(fallbackFix(source, resolved.target))
                        else -> emptyList()
                    }
                    holder.registerProblem(
                        resolved.annotation,
                        diagnostic.presentation(),
                        diagnostic.highlight(),
                        *fixes.toTypedArray(),
                    )
                }
        }
    }

    // ── Modo CONTRACT: los métodos de mapeo de una interfaz `@Mapper` (v0.5/v0.6) ─────────────

    private fun checkMapper(mapper: KtClassOrObject, holder: ProblemsHolder) {
        // v0.8: las FORMAS no-simples — colecciones (KMX046 con fix), patch (KMX012+campos)
        // e @InverseOf (KMX045, validaciones locales).
        for (issue in EditorMappingResolver.mapperShapeIssues(mapper)) {
            val fixes: List<LocalQuickFix> = issue.missingElementPair?.let { (sourceElem, targetElem) ->
                listOf(AddElementMethodFix(mapper.pointer(), sourceElem, targetElem))
            } ?: emptyList()
            holder.registerProblem(
                issue.method.nameIdentifier ?: issue.method,
                issue.diagnostic.presentation(),
                issue.diagnostic.highlight(),
                *fixes.toTypedArray(),
            )
        }
        for (resolved in EditorMappingResolver.mapperMethods(mapper)) {
            val method = resolved.method
            resolved.plan.diagnostics
                .filter { it.code in SAFE_CODES }
                .forEach { diagnostic ->
                    val member = diagnostic.location.member
                    val fixes: List<LocalQuickFix> = when {
                        // En contract el fix anota el MÉTODO: el dominio no se toca.
                        diagnostic.code == DiagnosticCode.KMX003 && member != null -> listOf(
                            AnnotateFix(
                                method.pointer(),
                                """@MapField(target = "$member", onNull = OnNull.THROW)""",
                                MAP_FIELD_IMPORTS,
                            ),
                        )
                        diagnostic.code == DiagnosticCode.KMX007 && member != null ->
                            listOfNotNull(nestedMapToFix(resolved.sourceClass, resolved.targetClass, member))
                        diagnostic.code == DiagnosticCode.KMX026 ->
                            listOfNotNull(mapEntryFix(resolved.sourceClass, resolved.targetClass, diagnostic))
                        diagnostic.code == DiagnosticCode.KMX047 ->
                            listOfNotNull(fallbackFix(resolved.sourceClass, resolved.targetClass))
                        else -> emptyList()
                    }
                    holder.registerProblem(
                        method.nameIdentifier ?: method,
                        diagnostic.presentation(),
                        diagnostic.highlight(),
                        *fixes.toTypedArray(),
                    )
                }
        }
    }

    /**
     * KMX007 — el quick-fix declara el mapeo anidado que falta: anota el tipo del campo del
     * SOURCE con `@MapTo(<TipoDelTarget>::class)`. Solo para pares clase-a-clase localizables
     * por nombre corto (elementos de colección y homónimos irresueltos quedan sin fix).
     */
    private fun nestedMapToFix(
        sourceClass: KtClassOrObject,
        targetClass: KtClassOrObject,
        member: String,
    ): LocalQuickFix? {
        fun typeShortOf(owner: KtClassOrObject, name: String): String? =
            MapFieldPsi.propertyTypeText(owner, name)
                ?.substringBefore('<')?.removeSuffix("?")?.substringAfterLast('.')
                ?.takeIf { it.isNotEmpty() }

        val sourceTypeShort = typeShortOf(sourceClass, member) ?: return null
        val targetTypeShort = typeShortOf(targetClass, member) ?: return null
        val nested = MapFieldPsi.ownerClass(sourceClass.project, sourceTypeShort) ?: return null
        return AnnotateFix(
            nested.pointer(),
            "@MapTo($targetTypeShort::class)",
            listOf("dev.kmapx.annotations.embedded.MapTo"),
        )
    }

    /**
     * KMX026 — el fix anota el ENTRY sin par del source con `@MapEntry(target = <candidato>)`
     * (el más cercano por distancia de edición entre los entries del target; sin candidato,
     * sin fix — no se adivina).
     */
    private fun mapEntryFix(
        sourceEnum: KtClassOrObject,
        targetEnum: KtClassOrObject,
        diagnostic: Diagnostic,
    ): LocalQuickFix? {
        val entryName = diagnostic.location.qualifiedClassName.substringAfterLast('.')
        val entry = sourceEnum.declarations.filterIsInstance<KtEnumEntry>()
            .firstOrNull { it.name == entryName } ?: return null
        val candidate = Suggestions.closest(entryName, MapFieldPsi.enumEntryNames(targetEnum))
            .firstOrNull() ?: return null
        return AnnotateFix(
            entry.pointer(),
            """@MapEntry(target = "$candidate")""",
            listOf("dev.kmapx.annotations.MapEntry"),
        )
    }

    /** KMX047 — el fix repara el string del fallback de clase con el did-you-mean. */
    private fun fallbackFix(sourceEnum: KtClassOrObject, targetEnum: KtClassOrObject): LocalQuickFix? {
        val annotation = sourceEnum.annotationEntries
            .firstOrNull { it.shortName?.asString() == "MapEntry" } ?: return null
        val current = MapFieldPsi.stringValue(annotation, "target") ?: return null
        val candidate = Suggestions.closest(current, MapFieldPsi.enumEntryNames(targetEnum))
            .firstOrNull() ?: return null
        val string = MapFieldPsi.argument(annotation, "target")
            ?.getArgumentExpression() as? KtStringTemplateExpression ?: return null
        return ReplaceStringValueFix(
            SmartPointerManager.getInstance(string.project).createSmartPsiElementPointer(string),
            candidate,
        )
    }

    private fun Diagnostic.presentation(): String = "[${code.id}] $message Fix: $fix"

    private fun Diagnostic.highlight(): ProblemHighlightType =
        if (severity == Severity.WARNING) ProblemHighlightType.WARNING
        else ProblemHighlightType.GENERIC_ERROR

    private fun KtModifierListOwner.pointer(): SmartPsiElementPointer<KtModifierListOwner> =
        SmartPointerManager.getInstance(project).createSmartPsiElementPointer(this)

    private companion object {
        /** Los códigos SEGUROS con el estado del editor de v0.6 (ver KDoc de la clase). */
        val SAFE_CODES = setOf(
            DiagnosticCode.KMX002, DiagnosticCode.KMX003,
            DiagnosticCode.KMX004, DiagnosticCode.KMX007, DiagnosticCode.KMX009,
            DiagnosticCode.KMX026, DiagnosticCode.KMX047, DiagnosticCode.KMX023,
        )
        val MAP_FIELD_IMPORTS = listOf("dev.kmapx.annotations.MapField", "dev.kmapx.annotations.OnNull")
    }
}

/**
 * Anota la declaración señalada (posiblemente en otro archivo) con la anotación sugerida,
 * agregando los [imports] que falten. Sirve a los tres fixes: parámetro del target (embedded),
 * método del mapper (contract) y tipo anidado del source (KMX007).
 */
private class AnnotateFix(
    private val owner: SmartPsiElementPointer<KtModifierListOwner>,
    private val annotationText: String,
    private val imports: List<String>,
) : LocalQuickFix {

    override fun getFamilyName(): String = "kmapx: agregar $annotationText"

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val target = owner.element ?: return
        val factory = KtPsiFactory(project)
        target.addAnnotationEntry(factory.createAnnotationEntry(annotationText))

        val file = target.containingKtFile
        val present = file.importDirectives.mapNotNull { it.importedFqName?.asString() }.toSet()
        val importList = file.importList ?: return
        imports.filter { it.substringAfterLast('.') in annotationText }.filterNot { it in present }.forEach { fqn ->
            importList.add(factory.createImportDirective(org.jetbrains.kotlin.resolve.ImportPath.fromString(fqn)))
        }
    }
}

/** Reemplaza el CONTENIDO de un string de anotación por el candidato del did-you-mean (v0.7). */
private class ReplaceStringValueFix(
    private val string: SmartPsiElementPointer<KtStringTemplateExpression>,
    private val replacement: String,
) : LocalQuickFix {
    override fun getFamilyName(): String = "kmapx: cambiar a \"$replacement\""
    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val element = string.element ?: return
        element.replace(KtPsiFactory(project).createExpression("\"$replacement\""))
    }
}

/**
 * KMX046 (v0.8) — declara el método del ELEMENTO que falta en la interfaz del mapper:
 * `fun to<Target>(value: <Source>): <Target>` — la delegación de colecciones lo encuentra al instante.
 */
private class AddElementMethodFix(
    private val mapper: SmartPsiElementPointer<KtModifierListOwner>,
    private val sourceElement: String,
    private val targetElement: String,
) : LocalQuickFix {

    override fun getFamilyName(): String =
        "kmapx: declarar fun to$targetElement(value: $sourceElement): $targetElement"

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val owner = mapper.element as? KtClassOrObject ?: return
        // Un @Mapper con métodos siempre tiene cuerpo (aquí llegamos desde uno de sus métodos).
        val body = owner.body ?: return
        body.addBefore(
            KtPsiFactory(project).createFunction(
                "fun to$targetElement(value: $sourceElement): $targetElement",
            ),
            body.rBrace,
        )
    }
}
