package dev.kmapx.core.diagnostics

/**
 * Las reglas de COHERENCIA de una `@MapField`, como LÓGICA PURA y compartida:
 * las consumen el frontend KSP ([dev.kmapx.ksp.KspTranslator]) y la inspección del plugin de
 * IntelliJ — una sola fuente de verdad, cero duplicación de reglas .
 *
 * Este objeto DECIDE (qué violaciones hay); los mensajes/ubicaciones los construye cada
 * consumidor con las MISMAS factories de [Diagnostics] — así tampoco se duplican los textos.
 */
public object MapFieldRules {

    /** Las dos sedes: campo (embedded) o método de un `@Mapper` (contract). */
    public enum class Site { FIELD, METHOD }

    /** Una `@MapField` ya parseada a datos, sin PSI ni KSP: lo mínimo que las reglas necesitan. */
    public data class Declaration(
        val targetSet: Boolean,
        /** Nombre del entry de `onNull` ("INHERIT", "STRICT", "LITERAL"…). */
        val onNull: String,
        val defaultSet: Boolean,
        val fromSet: Boolean,
        val converterSet: Boolean,
        val ignore: Boolean,
    )

    public enum class Violation(public val code: DiagnosticCode) {
        /** KMX036: `target` seteado en sede de campo, o faltante en sede de método. */
        BAD_ADDRESSING(DiagnosticCode.KMX036),

        /** KMX038: `onNull = LITERAL` sin `default`. */
        LITERAL_WITHOUT_DEFAULT(DiagnosticCode.KMX038),

        /** KMX039 (warning): `default` seteado con `onNull != LITERAL`. */
        DEFAULT_IGNORED(DiagnosticCode.KMX039),

        /** KMX043: `ignore = true` junto a cualquier otro aspecto — config muerta. */
        IGNORE_CONFLICTS(DiagnosticCode.KMX043),
    }

    /** Las violaciones de UNA declaración (KMX037 —duplicados— es multi-declaración: ver abajo). */
    public fun check(site: Site, declaration: Declaration): List<Violation> = buildList {
        val badAddressing = when (site) {
            Site.FIELD -> declaration.targetSet
            Site.METHOD -> !declaration.targetSet
        }
        if (badAddressing) add(Violation.BAD_ADDRESSING)
        if (declaration.onNull == "LITERAL" && !declaration.defaultSet) add(Violation.LITERAL_WITHOUT_DEFAULT)
        if (declaration.defaultSet && declaration.onNull != "LITERAL") add(Violation.DEFAULT_IGNORED)
        if (declaration.ignore &&
            (declaration.fromSet || declaration.converterSet || declaration.defaultSet ||
                (declaration.onNull != "INHERIT" && declaration.onNull.isNotEmpty()))
        ) {
            add(Violation.IGNORE_CONFLICTS)
        }
    }

    /**
     * KMX037 — targets repetidos entre VARIAS `@MapField` del mismo dueño. En sede de campo los
     * nombres son el campo anotado (así >1 anotación = duplicado); en sede de método, los `target`.
     */
    public fun duplicateTargets(targets: List<String>): Set<String> =
        targets.groupingBy { it }.eachCount().filterValues { it > 1 }.keys

    /**
     * El aspecto `onNull` → estrategia del MODELO ([dev.kmapx.core.model.MNullStrategy]) —
     * compartido por el frontend KSP y el `adapter-psi` del plugin (una conversión).
     * [default] null = no seteado (el centinela lo interpreta cada frontend); `LITERAL` sin
     * default no produce estrategia (la violación KMX038 la reporta [check]).
     */
    public fun strategyFor(onNull: String, default: String?): dev.kmapx.core.model.MNullStrategy? =
        when (onNull) {
            "LITERAL" -> default?.let { dev.kmapx.core.model.MNullStrategy.WithDefault(it) }
            "TYPE_DEFAULT" -> dev.kmapx.core.model.MNullStrategy.OrEmpty
            "TARGET_DEFAULT" -> dev.kmapx.core.model.MNullStrategy.TargetDefault
            "THROW" -> dev.kmapx.core.model.MNullStrategy.OrThrow
            "UNSAFE" -> dev.kmapx.core.model.MNullStrategy.AllowUnsafe
            // STRICT EXPLÍCITO corta la cascada de niveles (≠ INHERIT, que no aporta).
            "STRICT" -> dev.kmapx.core.model.MNullStrategy.Strict
            else -> null // INHERIT (el default): la violación camina la cascada
        }

    /**
     * El `onNull` de una sede de NIVEL (`@Mapper`/`@MapTo`/`@MapperConfig`) → política de la
     * cascada. null = INHERIT o valor solo-de-campo (KMX041, que reporta el llamador).
     */
    public fun levelPolicyFor(onNull: String?): dev.kmapx.core.engine.NullPolicy? = when (onNull) {
        "STRICT" -> dev.kmapx.core.engine.NullPolicy.STRICT
        "THROW" -> dev.kmapx.core.engine.NullPolicy.OR_THROW
        "TYPE_DEFAULT" -> dev.kmapx.core.engine.NullPolicy.TYPE_DEFAULT
        "TARGET_DEFAULT" -> dev.kmapx.core.engine.NullPolicy.TARGET_DEFAULT
        else -> null
    }

    /** `LITERAL`/`UNSAFE` no son políticas de nivel (KMX041) — el llamador decide reportar. */
    public fun isFieldOnlyPolicy(onNull: String?): Boolean = onNull == "LITERAL" || onNull == "UNSAFE"
}
