package dev.kmapx.ext.datetime

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime

/**
 * Converters para los tipos de kotlinx-datetime — formato ISO-8601 (`toString()`/`parse()`) y
 * epoch en millis. API pura de kotlinx.datetime: mismas semánticas en cualquier target cuando
 * el pack pase a multiplataforma. Otro formato o zona → tu propio `@Converter` (siempre gana).
 */

public fun instantToIso(value: Instant): String = value.toString()
public fun instantFromIso(value: String): Instant = Instant.parse(value)
public fun instantToEpochMillis(value: Instant): Long = value.toEpochMilliseconds()
public fun instantFromEpochMillis(value: Long): Instant = Instant.fromEpochMilliseconds(value)

public fun localDateToIso(value: LocalDate): String = value.toString()
public fun localDateFromIso(value: String): LocalDate = LocalDate.parse(value)
public fun localDateTimeToIso(value: LocalDateTime): String = value.toString()
public fun localDateTimeFromIso(value: String): LocalDateTime = LocalDateTime.parse(value)
public fun localTimeToIso(value: LocalTime): String = value.toString()
public fun localTimeFromIso(value: String): LocalTime = LocalTime.parse(value)
