package dev.kmapx.intellij

import com.intellij.codeInsight.hint.HintManager
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import dev.kmapx.codegen.PlanEmitter
import dev.kmapx.intellij.MapFieldPsi.usesKmapx
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType

/**
 * El "delombok" de kmapx: alt+enter sobre una clase `@MapTo` MATERIALIZA el mapeo — escribe el
 * `<Source>Mappings.kt` que el build generaría (mismo motor, mismo emitter que el preview) como
 * archivo FUENTE junto a la clase, y elimina las anotaciones `@MapTo`. A partir de ahí el mapeo
 * es código del usuario: editable, sin KSP, indistinguible del escrito a mano.
 *
 * Guardas: solo con TODOS los planes válidos (un plan con KMX pendientes materializaría un
 * comentario, no código), y solo si el archivo destino no existe (jamás pisar código del
 * usuario). Quitar la anotación es parte del contrato: dejarla duplicaría la función generada.
 */
class MaterializeMappingIntention : IntentionAction {

    override fun getText(): String = "kmapx: materializar el mapeo como código fuente"
    override fun getFamilyName(): String = text
    override fun startInWriteAction(): Boolean = false

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
        if (editor == null || file !is KtFile || !file.usesKmapx()) return false
        val declaration = file.findElementAt(editor.caretModel.offset)
            ?.getParentOfType<KtClassOrObject>(strict = false) ?: return false
        return declaration.annotationEntries.any {
            it.shortName?.asString() == "MapTo" || it.shortName?.asString() == "Mapper"
        }
    }

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        if (editor == null || file !is KtFile) return
        val source = file.findElementAt(editor.caretModel.offset)
            ?.getParentOfType<KtClassOrObject>(strict = false) ?: return

        if (source.annotationEntries.any { it.shortName?.asString() == "Mapper" }) {
            materializeMapper(project, editor, file, source)
            return
        }
        val resolved = EditorMappingResolver.mapTos(source)
        if (resolved.isEmpty()) {
            HintManager.getInstance().showErrorHint(editor, "kmapx: no hay mapeos resolubles en el editor para esta clase")
            return
        }
        val invalid = resolved.filter { !it.plan.valid }
        if (invalid.isNotEmpty()) {
            val codes = invalid.flatMap { it.plan.diagnostics }.map { it.code.id }.distinct()
            HintManager.getInstance().showErrorHint(
                editor,
                "kmapx: corrige los diagnósticos pendientes antes de materializar (${codes.joinToString()})",
            )
            return
        }

        val generated = PlanEmitter().emit(resolved.map { it.plan })
        val fileName = "${generated.fileName}.kt"
        val directory = file.containingDirectory ?: return
        if (directory.findFile(fileName) != null) {
            HintManager.getInstance().showErrorHint(editor, "kmapx: $fileName ya existe — bórralo o renómbralo antes")
            return
        }

        writeMaterialized(project, file, fileName, generated.content, source, "MapTo")
    }

    /**
     * Contract: el `XImpl` como FUENTE. Solo cuando el editor puede construir TODOS los métodos
     * (mapeo simple/multi-fuente); patch/colecciones/@InverseOf/componentModel abortan con hint —
     * materializar a medias dejaría una interfaz sin implementar.
     */
    private fun materializeMapper(project: Project, editor: Editor, file: KtFile, mapper: KtClassOrObject) {
        val mapperAnn = mapper.annotationEntries.first { it.shortName?.asString() == "Mapper" }
        if (MapFieldPsi.enumEntryText(mapperAnn, "componentModel")?.takeIf { it != "INHERIT" && it != "NONE" } != null) {
            HintManager.getInstance().showErrorHint(editor, "kmapx: materializar con componentModel no está soportado")
            return
        }
        val abstract = mapper.declarations
            .filterIsInstance<org.jetbrains.kotlin.psi.KtNamedFunction>().filter { !it.hasBody() }
        val resolved = EditorMappingResolver.mapperMethods(mapper)
        if (resolved.size != abstract.size) {
            HintManager.getInstance().showErrorHint(
                editor,
                "kmapx: solo se materializan mapeos simples/multi-fuente (patch, colecciones e @InverseOf quedan para el build)",
            )
            return
        }
        val invalid = resolved.filter { !it.plan.valid }
        if (invalid.isNotEmpty()) {
            val codes = invalid.flatMap { it.plan.diagnostics }.map { it.code.id }.distinct()
            HintManager.getInstance().showErrorHint(
                editor,
                "kmapx: corrige los diagnósticos pendientes antes de materializar (${codes.joinToString()})",
            )
            return
        }

        val adapter = PsiAdapter(project)
        val pkg = mapper.containingKtFile.packageFqName.asString()
        val qn = mapper.name?.let { if (pkg.isEmpty()) it else "$pkg.$it" } ?: return
        val methods = resolved.map { r ->
            dev.kmapx.core.plan.MapperMethod(
                name = r.method.name ?: "map",
                parameters = r.method.valueParameters.map {
                    dev.kmapx.core.plan.MParam(it.name ?: "p", adapter.typeOf(it.typeReference))
                },
                returns = adapter.typeOf(r.method.typeReference),
                body = dev.kmapx.core.plan.MethodBody.InlineConstruction(
                    receiverParam = r.method.valueParameters.first().name ?: "p",
                    plan = r.plan,
                    supplementaryParams = r.method.valueParameters.drop(1).mapNotNull { it.name }.toSet(),
                ),
            )
        }
        val generated = PlanEmitter().emitMapper(
            dev.kmapx.core.plan.MapperImplPlan(
                interfaceQualifiedName = qn,
                componentModel = dev.kmapx.core.plan.Emission.Component.NONE,
                methods = methods,
            ),
        )
        val fileName = "${generated.fileName}.kt"
        if (file.containingDirectory?.findFile(fileName) != null) {
            HintManager.getInstance().showErrorHint(editor, "kmapx: $fileName ya existe — bórralo o renómbralo antes")
            return
        }
        writeMaterialized(project, file, fileName, generated.content, mapper, "Mapper")
    }

    private fun writeMaterialized(
        project: Project,
        file: KtFile,
        fileName: String,
        content: String,
        annotated: KtClassOrObject,
        annotationShortName: String,
    ) {
        val directory = file.containingDirectory ?: return
        WriteCommandAction.runWriteCommandAction(
            project, text, null,
            {
                val newFile = PsiFileFactory.getInstance(project)
                    .createFileFromText(fileName, KotlinFileType.INSTANCE, content)
                val added = directory.add(newFile) as PsiFile
                // Sin la anotación: el archivo materializado ES el mapeo — dejarla haría que
                // KSP volviera a generar la misma declaración (colisión).
                annotated.annotationEntries
                    .filter { it.shortName?.asString() == annotationShortName }
                    .forEach { it.delete() }
                // En contract, la config por método (`@MapField(target=...)`) también queda
                // MUERTA sin la @Mapper: su semántica ya vive en el impl materializado.
                if (annotationShortName == "Mapper") {
                    annotated.declarations
                        .filterIsInstance<org.jetbrains.kotlin.psi.KtNamedFunction>()
                        .flatMap { it.annotationEntries }
                        .filter { it.shortName?.asString() == "MapField" }
                        .forEach { it.delete() }
                }
                // Los imports que quedaron huérfanos (por PSI: un uso en un comentario no
                // cuenta — si otra clase del archivo sigue anotada, el import se queda).
                val ktFile = annotated.containingKtFile
                for (shortName in setOf(annotationShortName, "MapField")) {
                    val stillUsed = com.intellij.psi.util.PsiTreeUtil
                        .findChildrenOfType(ktFile, org.jetbrains.kotlin.psi.KtAnnotationEntry::class.java)
                        .any { it.shortName?.asString() == shortName }
                    if (!stillUsed) {
                        ktFile.importDirectives
                            .filter { it.importedFqName?.asString()?.endsWith(".$shortName") == true }
                            .forEach { it.delete() }
                    }
                }
                // El GENERADO de la corrida anterior sigue en build/generated hasta el próximo
                // build — borrarlo aquí evita el "Redeclaration" mientras tanto (KSP ya no lo
                // regenerará: la anotación acaba de irse).
                com.intellij.psi.search.FilenameIndex
                    .getVirtualFilesByName(fileName, com.intellij.psi.search.GlobalSearchScope.allScope(project))
                    .filter { "/build/generated/" in it.path && it != added.virtualFile }
                    .forEach { stale -> runCatching { stale.delete(this) } }
                added.virtualFile?.let { FileEditorManager.getInstance(project).openFile(it, true) }
            },
            file,
        )
    }
}
