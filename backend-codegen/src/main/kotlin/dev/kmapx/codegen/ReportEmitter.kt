package dev.kmapx.codegen

/**
 * Reporte de cobertura de mapeo. Materializa lo que el motor DECIDIÓ (la trazabilidad
 * `Resolution` viaja en el plan): por campo, de dónde vino y por qué forma.
 * JSON con schema VERSIONADO (es API: hay quien lo parseará en CI) + HTML autocontenido
 * (cero referencias externas — mismo espíritu que "sin java." en el generado).
 * El reporte NUNCA altera diagnósticos ni el build: observabilidad pura.
 */
public object ReportEmitter {

    public const val SCHEMA: Int = 1

    public data class ReportField(
        val target: String,
        val from: String,
        /** Trazabilidad: USER_CONVERTER / DECLARED_MAPTO / IMPLICIT_SAFE. */
        val origin: String,
        /** Forma concreta: direct, converter, mapper, unwrap, wrap, map-elements, map-entries, … */
        val shape: String,
        /** FQN del converter/mapper si aplica. */
        val ref: String? = null,
        /** `?: target.x` (null = no tocar). */
        val fallbackToTarget: Boolean? = null,
    )

    public data class ReportMapping(
        val source: String,
        val target: String,
        /** extension | bidirectional | interface | patch */
        val mode: String,
        val function: String,
        val file: String? = null,
        val line: Int? = null,
        val fields: List<ReportField> = emptyList(),
        /** Delegación (modo interface): FQN de la extension a la que delega. */
        val delegatesTo: String? = null,
        val unusedSourceProperties: List<String> = emptyList(),
        /** Diagnósticos WARNING renderizados (KMX018/021/023); con error no se emite nada. */
        val warnings: List<String> = emptyList(),
    )

    public fun toJson(
        module: String,
        mappings: List<ReportMapping>,
        /** Config global efectiva (solo lo NO-default; vacío = nada que mostrar). Additivo al schema 1. */
        config: Map<String, String> = emptyMap(),
    ): String = buildString {
        append("{\n")
        append("  \"schema\": $SCHEMA,\n")
        append("  \"module\": ${js(module)},\n")
        if (config.isNotEmpty()) {
            append("  \"config\": {")
            append(config.entries.joinToString(", ") { "${js(it.key)}: ${js(it.value)}" })
            append("},\n")
        }
        append("  \"mappings\": [\n")
        mappings.forEachIndexed { i, m ->
            append("    {\n")
            append("      \"source\": ${js(m.source)}, \"target\": ${js(m.target)},\n")
            append("      \"mode\": ${js(m.mode)}, \"function\": ${js(m.function)},\n")
            if (m.file != null) append("      \"file\": ${js(m.file)}, \"line\": ${m.line ?: 0},\n")
            if (m.delegatesTo != null) append("      \"delegatesTo\": ${js(m.delegatesTo)},\n")
            append("      \"fields\": [")
            append(
                m.fields.joinToString(", ") { f ->
                    buildString {
                        append("{\"target\": ${js(f.target)}, \"from\": ${js(f.from)}, ")
                        append("\"origin\": ${js(f.origin)}, \"shape\": ${js(f.shape)}")
                        if (f.ref != null) append(", \"ref\": ${js(f.ref)}")
                        if (f.fallbackToTarget != null) append(", \"fallbackToTarget\": ${f.fallbackToTarget}")
                        append("}")
                    }
                },
            )
            append("],\n")
            append("      \"unusedSourceProperties\": [${m.unusedSourceProperties.joinToString(", ") { js(it) }}],\n")
            append("      \"warnings\": [${m.warnings.joinToString(", ") { js(it) }}]\n")
            append("    }")
            if (i < mappings.lastIndex) append(",")
            append("\n")
        }
        append("  ]\n")
        append("}\n")
    }

    public fun toHtml(
        module: String,
        mappings: List<ReportMapping>,
        config: Map<String, String> = emptyMap(),
    ): String = buildString {
        append("<!DOCTYPE html>\n<html lang=\"en\"><head><meta charset=\"utf-8\">\n")
        append("<title>kmapx coverage — ${h(module)}</title>\n")
        append("<style>")
        append("body{font-family:system-ui,sans-serif;margin:2rem;color:#222}")
        append("table{border-collapse:collapse;margin:.5rem 0 2rem}")
        append("td,th{border:1px solid #ccc;padding:.3rem .6rem;text-align:left;font-size:.9rem}")
        append("th{background:#f5f5f5}.badge{padding:.1rem .4rem;border-radius:.3rem;font-size:.8rem}")
        append(".USER_CONVERTER{background:#ffe8b3}.DECLARED_MAPTO{background:#cde8ff}.IMPLICIT_SAFE{background:#d8f5d0}")
        append(".warn{color:#a15c00}")
        append("</style></head><body>\n")
        append("<h1>kmapx coverage — ${h(module)}</h1>\n<p>schema $SCHEMA · ${mappings.size} mappings</p>\n")
        if (config.isNotEmpty()) {
            append("<p>config: ${config.entries.joinToString(", ") { "${h(it.key)}=<code>${h(it.value)}</code>" }}</p>\n")
        }
        for (m in mappings) {
            append("<h2>${h(m.source)} → ${h(m.target)} <small>(${h(m.mode)} · ${h(m.function)})</small></h2>\n")
            if (m.delegatesTo != null) append("<p>delegates to <code>${h(m.delegatesTo)}</code></p>\n")
            if (m.fields.isNotEmpty()) {
                append("<table><tr><th>target</th><th>from</th><th>origin</th><th>shape</th><th>ref</th></tr>\n")
                for (f in m.fields) {
                    append("<tr><td>${h(f.target)}</td><td><code>${h(f.from)}</code></td>")
                    append("<td><span class=\"badge ${h(f.origin)}\">${h(f.origin)}</span></td>")
                    append("<td>${h(f.shape)}${if (f.fallbackToTarget == true) " ?: target" else ""}</td>")
                    append("<td>${f.ref?.let { "<code>${h(it)}</code>" } ?: "—"}</td></tr>\n")
                }
                append("</table>\n")
            }
            if (m.unusedSourceProperties.isNotEmpty()) {
                append("<p>unused source properties: ${m.unusedSourceProperties.joinToString { h(it) }}</p>\n")
            }
            m.warnings.forEach { append("<p class=\"warn\">⚠ ${h(it)}</p>\n") }
        }
        append("</body></html>\n")
    }

    private fun js(s: String): String =
        "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\""

    private fun h(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}
