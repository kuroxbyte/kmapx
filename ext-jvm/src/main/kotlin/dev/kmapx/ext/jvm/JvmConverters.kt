package dev.kmapx.ext.jvm

import java.math.BigDecimal
import java.math.BigInteger
import java.net.URI
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

/**
 * Funciones de conversión JVM que el código generado invoca por nombre (con import, sin
 * reflection). Son la implementación real detrás de los pares que [KmapxJvmExtension] registra.
 * Puras y top-level: cumplen el contrato `(A) -> B` de `@Converter` — de hecho el usuario podría
 * anotarlas él mismo; el pack solo evita que las escriba.
 *
 * Formatos: todos ISO-8601 (`Instant.toString()`, `LocalDate.toString()`…) y epoch en millis.
 * Si necesitas otro formato o zona, escribe tu propio `@Converter` — ganará sobre estos (regla
 * de precedencia del SPI: el converter del usuario siempre gana).
 */

// ── java.time.Instant ──────────────────────────────────────────────────────
public fun instantToIso(value: Instant): String = value.toString()
public fun instantFromIso(value: String): Instant = Instant.parse(value)
public fun instantToEpochMillis(value: Instant): Long = value.toEpochMilli()
public fun instantFromEpochMillis(value: Long): Instant = Instant.ofEpochMilli(value)

// ── java.time.LocalDate / LocalDateTime / Duration ─────────────────────────
public fun localDateToIso(value: LocalDate): String = value.toString()
public fun localDateFromIso(value: String): LocalDate = LocalDate.parse(value)
public fun localDateTimeToIso(value: LocalDateTime): String = value.toString()
public fun localDateTimeFromIso(value: String): LocalDateTime = LocalDateTime.parse(value)
public fun durationToIso(value: Duration): String = value.toString()
public fun durationFromIso(value: String): Duration = Duration.parse(value)

// ── java.util.UUID ─────────────────────────────────────────────────────────
public fun uuidToString(value: UUID): String = value.toString()
public fun uuidFromString(value: String): UUID = UUID.fromString(value)

// ── java.math ──────────────────────────────────────────────────────────────
public fun bigDecimalToString(value: BigDecimal): String = value.toPlainString()
public fun bigDecimalFromString(value: String): BigDecimal = BigDecimal(value)
public fun bigIntegerToString(value: BigInteger): String = value.toString()
public fun bigIntegerFromString(value: String): BigInteger = BigInteger(value)

// ── java.net.URI ───────────────────────────────────────────────────────────
public fun uriToString(value: URI): String = value.toString()
public fun uriFromString(value: String): URI = URI(value)
