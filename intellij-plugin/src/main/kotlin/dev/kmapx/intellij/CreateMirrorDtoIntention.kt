package dev.kmapx.intellij

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import dev.kmapx.intellij.MapFieldPsi.usesKmapx
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType

/**
 * El plugin como ASISTENTE, no solo detector: alt+enter sobre una data class
 * sin `@MapTo` genera su DTO espejo (mismos campos, sufijo `Dto`) en el mismo archivo y anota
 * la clase con `@MapTo(<Clase>Dto::class)` — el punto de partida del modo embedded en un gesto.
 *
 * Requiere el import de kmapx en el archivo O que el DTO espejo no exista aún y la clase sea
 * una data class con constructor primario (nada que adivinar). El DTO nace IDÉNTICO: el usuario
 * lo edita después y la inspección/el motor le dicen qué configurar (esa es la gracia).
 */
class CreateMirrorDtoIntention : IntentionAction {

    override fun getText(): String = "kmapx: crear el DTO espejo con @MapTo"
    override fun getFamilyName(): String = text
    override fun startInWriteAction(): Boolean = true

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
        val declaration = declarationAt(editor, file) ?: return false
        val name = declaration.name ?: return false
        // Solo si el espejo no existe todavía (si existe, lo que falta es la anotación, no la clase).
        return MapFieldPsi.ownerClass(project, "${name}Dto") == null
    }

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        val declaration = declarationAt(editor, file) ?: return
        val name = declaration.name ?: return
        val params = declaration.primaryConstructor?.valueParameters
            ?.mapNotNull { p -> p.name?.let { n -> p.typeReference?.text?.let { t -> "val $n: $t" } } }
            ?.takeIf { it.isNotEmpty() } ?: return
        val factory = KtPsiFactory(project)

        // 1. El DTO espejo, después de la clase anotada (mismo archivo: revisable en un vistazo).
        val mirror = factory.createClass("data class ${name}Dto(${params.joinToString()})")
        val added = declaration.parent.addAfter(mirror, declaration)
        declaration.parent.addBefore(factory.createNewLine(2), added)

        // 2. La anotación + el import (si falta).
        declaration.addAnnotationEntry(factory.createAnnotationEntry("@MapTo(${name}Dto::class)"))
        val ktFile = declaration.containingKtFile
        val present = ktFile.importDirectives.mapNotNull { it.importedFqName?.asString() }
        if ("dev.kmapx.annotations.embedded.MapTo" !in present) {
            ktFile.importList?.add(
                factory.createImportDirective(
                    org.jetbrains.kotlin.resolve.ImportPath.fromString("dev.kmapx.annotations.embedded.MapTo"),
                ),
            )
        }
    }

    private fun declarationAt(editor: Editor?, file: PsiFile?): KtClass? {
        if (editor == null || file !is KtFile) return null
        val declaration = file.findElementAt(editor.caretModel.offset)
            ?.getParentOfType<KtClass>(strict = false) ?: return null
        if (!declaration.isData() || declaration.isEnum()) return null
        if (declaration.primaryConstructor?.valueParameters.isNullOrEmpty()) return null
        // Con un @MapTo ya declarado no hay nada que crear.
        if (declaration.annotationEntries.any { it.shortName?.asString() == "MapTo" }) return null
        return declaration
    }
}
