package dev.kmapx.intellij

import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiShortNamesCache
import dev.kmapx.core.diagnostics.MapFieldRules
import org.jetbrains.kotlin.asJava.classes.KtLightClass
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType

/**
 * Lectura PSI de `@MapField` compartida por la referencia y la inspección: parseo de argumentos
 * a la [MapFieldRules.Declaration] del core, detección de sede, y la heurística de localizar la
 * clase dueña de un `target=`/`from=` por nombre corto (light classes — documentado: sin resolve
 * completo, un homónimo de otro paquete podría ganar).
 */
internal object MapFieldPsi {

    fun isMapField(entry: KtAnnotationEntry): Boolean =
        entry.shortName?.asString() == "MapField" && entry.containingKtFile.usesKmapx()

    fun KtFile.usesKmapx(): Boolean =
        importDirectives.any { it.importedFqName?.asString()?.startsWith("dev.kmapx.annotations") == true }

    /** La sede de la anotación: método de mapper vs campo (param/property). null = otra cosa. */
    fun siteOf(entry: KtAnnotationEntry): MapFieldRules.Site? {
        entry.getParentOfType<KtParameter>(strict = true)?.let { return MapFieldRules.Site.FIELD }
        entry.getParentOfType<KtProperty>(strict = true)?.let { return MapFieldRules.Site.FIELD }
        entry.getParentOfType<KtNamedFunction>(strict = true)?.let { return MapFieldRules.Site.METHOD }
        return null
    }

    /** Los argumentos de la anotación → la declaración PURA que consumen las reglas del core. */
    fun declarationOf(entry: KtAnnotationEntry): MapFieldRules.Declaration {
        val converterText = argumentText(entry, "converter")
        return MapFieldRules.Declaration(
            targetSet = !stringValue(entry, "target").isNullOrEmpty(),
            onNull = argumentText(entry, "onNull")?.substringAfterLast('.') ?: "INHERIT",
            defaultSet = argument(entry, "default") != null,
            fromSet = !stringValue(entry, "from").isNullOrEmpty(),
            converterSet = converterText != null && converterText != "Unit::class",
            ignore = argumentText(entry, "ignore") == "true",
        )
    }

    /** El valor string PLANO de un argumento (null si falta, tiene interpolación o no es string). */
    fun stringValue(entry: KtAnnotationEntry, name: String): String? {
        val expr = argument(entry, name)?.getArgumentExpression() as? KtStringTemplateExpression ?: return null
        if (expr.entries.size != 1 || expr.hasInterpolation()) return null
        return expr.entries.single().text
    }

    /** Argumento por NOMBRE; `target` admite además la forma posicional (primer parámetro de @MapField). */
    fun argument(entry: KtAnnotationEntry, name: String): KtValueArgument? {
        val named = entry.valueArguments.filterIsInstance<KtValueArgument>()
            .firstOrNull { it.getArgumentName()?.asName?.asString() == name }
        if (named != null) return named
        if (name != "target") return null
        return (entry.valueArguments.firstOrNull() as? KtValueArgument)
            ?.takeIf { it.getArgumentName() == null }
    }

    private fun argumentText(entry: KtAnnotationEntry, name: String): String? =
        argument(entry, name)?.getArgumentExpression()?.text

    /**
     * El TIPO cuyos campos direcciona un argumento string de la sede de método:
     * `target` → tipo de retorno; `from` → tipo del primer parámetro.
     */
    fun ownerTypeName(method: KtNamedFunction, argName: String): String? = when (argName) {
        "target" -> method.typeReference?.text
        "from" -> method.valueParameters.firstOrNull()?.typeReference?.text
        else -> null
    }?.substringBefore('<')?.removeSuffix("?")

    /** La clase por nombre corto vía light classes ([PsiShortNamesCache]) — heurística consciente. */
    fun ownerClass(project: com.intellij.openapi.project.Project, typeName: String): KtClassOrObject? =
        PsiShortNamesCache.getInstance(project)
            .getClassesByName(typeName, GlobalSearchScope.projectScope(project))
            .firstNotNullOfOrNull { (it as? KtLightClass)?.kotlinOrigin }

    /** Un argumento booleano LITERAL (`useSerialNames = true`) — false si falta o no es literal. */
    fun booleanValue(entry: KtAnnotationEntry, name: String): Boolean =
        argumentText(entry, name) == "true"

    /**
     * Los aspectos EFECTIVOS de una `@MapField` para el modelo (from/estrategia/ignore) — la
     * conversión es la COMPARTIDA del core ([MapFieldRules.strategyFor]); la consumen el
     * [PsiAdapter] (sede de campo) y la inspección de mapeo (config por método, sede contract).
     */
    data class FieldAspects(
        val from: String?,
        val strategy: dev.kmapx.core.model.MNullStrategy?,
        val ignored: Boolean,
    )

    fun aspectsOf(entry: KtAnnotationEntry): FieldAspects {
        val declaration = declarationOf(entry)
        return FieldAspects(
            from = stringValue(entry, "from")?.takeIf { it.isNotEmpty() },
            strategy = MapFieldRules.strategyFor(declaration.onNull, stringValue(entry, "default")),
            ignored = declaration.ignore,
        )
    }

    /** El texto del entry de un argumento enum (`OnNull.THROW` → "THROW"). */
    fun enumEntryText(entry: KtAnnotationEntry, name: String): String? =
        argument(entry, name)?.getArgumentExpression()?.text?.substringAfterLast('.')

    /** Un argumento `Array<String>` (`ignore = ["a", "b"]`) como set de literales planos. */
    fun stringListValue(entry: KtAnnotationEntry, name: String): Set<String> {
        val expr = argument(entry, name)?.getArgumentExpression() ?: return emptySet()
        return Regex("\"([^\"]*)\"").findAll(expr.text).map { it.groupValues[1] }.toSet()
    }

    /** Los nombres DIRECCIONABLES de una clase (params del constructor primario + properties). */
    fun addressableNames(owner: KtClassOrObject): List<String> {
        val fromCtor = owner.primaryConstructor?.valueParameters?.mapNotNull { it.name }.orEmpty()
        val fromBody = owner.declarations.filterIsInstance<KtProperty>().mapNotNull { it.name }
        return (fromCtor + fromBody).distinct()
    }

    /** Los nombres de los ENTRIES de un enum (v0.7 — @MapEntry, KMX026/047). */
    fun enumEntryNames(owner: KtClassOrObject): List<String> =
        owner.declarations.filterIsInstance<org.jetbrains.kotlin.psi.KtEnumEntry>().mapNotNull { it.name }

    /** El ENTRY [name] de un enum (destino de las referencias de `@MapEntry`, v0.7). */
    fun resolveEnumEntry(owner: KtClassOrObject, name: String): org.jetbrains.kotlin.psi.KtEnumEntry? =
        owner.declarations.filterIsInstance<org.jetbrains.kotlin.psi.KtEnumEntry>().firstOrNull { it.name == name }

    /** El campo direccionable [name] de una clase (param del constructor o property de cuerpo). */
    fun resolveField(owner: KtClassOrObject, name: String): com.intellij.psi.PsiElement? {
        owner.primaryConstructor?.valueParameters?.firstOrNull { it.name == name }?.let { return it }
        return owner.declarations.firstOrNull { it is KtProperty && it.name == name }
    }

    /** El TEXTO del tipo de un campo direccionable (para navegar rutas y pares anidados). */
    fun propertyTypeText(owner: KtClassOrObject, name: String): String? {
        owner.primaryConstructor?.valueParameters?.firstOrNull { it.name == name }
            ?.let { return it.typeReference?.text }
        return owner.declarations.filterIsInstance<KtProperty>()
            .firstOrNull { it.name == name }?.typeReference?.text
    }

    /**
     * El DUEÑO del segmento [index] de una ruta `a.b.c`: navega los tipos de los
     * segmentos previos por nombre corto (misma heurística consciente de [ownerClass]).
     */
    fun ownerAtSegment(
        project: com.intellij.openapi.project.Project,
        rootTypeName: String,
        segments: List<String>,
        index: Int,
    ): KtClassOrObject? {
        var owner = ownerClass(project, rootTypeName) ?: return null
        for (step in 0 until index) {
            val typeShort = propertyTypeText(owner, segments[step])
                ?.substringBefore('<')?.removeSuffix("?")?.substringAfterLast('.')
                ?: return null
            owner = ownerClass(project, typeShort) ?: return null
        }
        return owner
    }
}
