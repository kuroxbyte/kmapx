package dev.kmapx.core.engine

import dev.kmapx.core.diagnostics.Diagnostic
import dev.kmapx.core.diagnostics.MLocation
import dev.kmapx.core.model.MConstructor
import dev.kmapx.core.model.MConstructorParam
import dev.kmapx.core.model.MFactory
import dev.kmapx.core.model.MPath
import dev.kmapx.core.model.MProperty

/**
 * Cascada `onNull`— una política de nulabilidad de NIVEL (mapper/mapeo o global) ante
 * `T? -> T` sin estrategia de campo. La resolución camina la cadena campo → mapper/mapeo → global:
 * `STRICT` corta (KMX003); `OR_THROW` = `?: throw`; las CONDICIONALES aplican donde pueden y donde
 * no, la violación CAE al siguiente nivel: `TYPE_DEFAULT` (colección idéntica → `?: emptyXxx()`),
 * `TARGET_DEFAULT` (default de constructor + tipos iguales → omite el argumento).
 * `LITERAL`/`UNSAFE` no existen como política de nivel (KMX041): son per-field por diseño.
 */
public enum class NullPolicy { STRICT, OR_THROW, TYPE_DEFAULT, TARGET_DEFAULT }

/**
 * La severidad de la omisión ("campo sin fuente llenado por su default",
 * KMX021). El default es [WARN] — el comportamiento histórico: la omisión nunca es silenciosa.
 * [IGNORE] la acalla (auditoría deliberadamente laxa); [ERROR] la convierte en bloqueo (el
 * mapper no tolera campos sin mapear). No aplica a campos `ignore` (ese consentimiento ya
 * acalla) ni a `TARGET_DEFAULT` (eso es mapeo declarado, no campo sin mapear).
 */
public enum class UnmappedPolicy { IGNORE, WARN, ERROR }

/**
 * Contexto inmutable de UNA resolución source→target. Lo comparten el orquestador
 * ([MappingEngine]) y sus colaboradores ([SourceMatcher], [ValueResolver], …): así cada
 * colaborador recibe el mismo estado sin que el motor sea un god-object.
 */
internal data class Ctx(
    val targetLocation: MLocation,
    val mappingPair: String,
    /** (sourceQn, targetQn) → función de extension declarada. */
    val declaredMappings: Map<Pair<String, String>, String>,
    /**
     * Resolución cross-module (design/cross-module-mapping.md): (sourceQn, targetQn) → FQN de una
     * extensión generada en OTRO módulo, descubierta por el frontend en el classpath. Se consulta
     * solo cuando el par NO está en [declaredMappings] local. Default no-op (core puro / tests).
     */
    val crossModuleMappings: (String, String) -> String? = { _, _ -> null },
    /** (fromQn, toQn) → FQNs de `@Converter` — lista para detectar KMX009. */
    val converters: Map<Pair<String, String>, List<String>>,
    /** Rutas `a.b.c` pre-NAVEGADAS por el frontend (el motor juzga, no navega). */
    val resolvedPaths: Map<String, MPath>,
    /** `@SerialName` del source como alias de matching (el nombre real gana). */
    val useSerialNames: Boolean,
    /**
     * La CADENA de políticas de nivel, en orden de precedencia (mapper/mapeo primero,
     * global después), ya sin `INHERIT` (lo dropea el frontend). Lista vacía o agotada = STRICT.
     */
    val nullPolicies: List<NullPolicy> = emptyList(),
    /** `true` solo en modo B inline — habilita converters-class inyectados (si no, KMX034). */
    val allowInjectedConverters: Boolean = false,
    /** Habilita las conversiones ESTÁNDAR opt-in (el widening es incondicional). */
    val stdConverters: Boolean = false,
    /** Severidad de la omisión (KMX021) — cascada mapeo > mapper > profile > global. */
    val unmapped: UnmappedPolicy = UnmappedPolicy.WARN,
    val diagnostics: MutableList<Diagnostic>,
)

/** Fuente hallada + el segmento nullable culpable (para el KMX003 enriquecido). */
internal data class SourceLookup(val property: MProperty, val nullableSegment: String? = null)

/** Mecanismo de construcción elegido (constructor primario/anotado o factory). */
internal sealed interface Mechanism {
    val params: List<MConstructorParam>

    data class Ctor(val constructor: MConstructor) : Mechanism {
        override val params: List<MConstructorParam> get() = constructor.params
    }

    data class Factory(val factory: MFactory) : Mechanism {
        override val params: List<MConstructorParam> get() = factory.params
    }
}
