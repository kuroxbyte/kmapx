package dev.kmapx.intellij

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.psi.PsiFile
import com.intellij.ui.components.JBScrollPane
import dev.kmapx.intellij.MapFieldPsi.usesKmapx
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import java.awt.Dimension
import java.awt.Font
import javax.swing.JTextArea

/**
 * Alt+enter sobre un `@MapTo` (o un método de `@Mapper`) muestra
 * el código que kmapx GENERARÁ, calculado por [GeneratedCodePreview] con el motor y el emitter
 * reales. Solo lectura, cero escritura: [startInWriteAction] = false.
 */
class PreviewGeneratedCodeIntention : IntentionAction {

    override fun getText(): String = "kmapx: ver el código generado"
    override fun getFamilyName(): String = text
    override fun startInWriteAction(): Boolean = false

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
        if (editor == null || file !is KtFile || !file.usesKmapx()) return false
        val element = file.findElementAt(editor.caretModel.offset) ?: return false
        element.getParentOfType<KtNamedFunction>(strict = false)
            ?.takeIf { it.getParentOfType<KtClassOrObject>(strict = true)?.hasMapperAnnotation() == true }
            ?.let { return true }
        return element.getParentOfType<KtClassOrObject>(strict = false)?.hasMapToAnnotation() == true
    }

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        if (editor == null || file !is KtFile) return
        val element = file.findElementAt(editor.caretModel.offset) ?: return

        val code = element.getParentOfType<KtNamedFunction>(strict = false)
            ?.takeIf { it.getParentOfType<KtClassOrObject>(strict = true)?.hasMapperAnnotation() == true }
            ?.let { GeneratedCodePreview.renderMapperMethod(it) }
            ?: element.getParentOfType<KtClassOrObject>(strict = false)
                ?.let { GeneratedCodePreview.renderMapTos(it) }
            ?: return

        val area = JTextArea(code).apply {
            isEditable = false
            font = Font(Font.MONOSPACED, Font.PLAIN, font.size)
        }
        JBPopupFactory.getInstance()
            .createComponentPopupBuilder(JBScrollPane(area).apply { preferredSize = Dimension(640, 360) }, area)
            .setTitle("kmapx — código generado")
            .setResizable(true)
            .setMovable(true)
            .setRequestFocus(true)
            .createPopup()
            .showInBestPositionFor(editor)
    }

    private fun KtClassOrObject.hasMapToAnnotation(): Boolean =
        annotationEntries.any { it.shortName?.asString() == "MapTo" }

    private fun KtClassOrObject.hasMapperAnnotation(): Boolean =
        annotationEntries.any { it.shortName?.asString() == "Mapper" }
}
