# Guía — Patrones de mapeo

## Cuándo NO usar bidireccional

`@BiMapTo` es para pares SIMÉTRICOS. Usa dos `@MapTo` unidireccionales cuando la asimetría es
legítima — y `KMX028` es la señal de que estás en ese caso:

- **Campos de un solo lado** (auditoría, flags internos): el round-trip no puede reconstruirlos.
- **Converter sin inverso razonable** (`Instant → "hace 3 días"`): no fuerces un inverso falso.
- **Fan-out** (un campo alimenta dos): la vuelta no puede dividir un valor.
- **Defaults del target como fallback**: la omisión pierde información a propósito.

Regla práctica: si para silenciar un KMX028 tendrías que INVENTAR datos, la relación es
unidireccional. kmapx valida estructura; que tus converters sean inversos exactos
(`isoToInstant(instantToIso(x)) == x`) es responsabilidad tuya.

## Aplanar objetos con rutas

`@MapField(from = "address.country.code")` — la matriz de nulabilidad por segmento:

| Segmentos intermedios | Target | Resultado |
|---|---|---|
| todos no-nullables | `T` | `address.country.code` — directo |
| alguno nullable | `T?` | `address.country?.code` — `?.` DESDE el primer nullable |
| alguno nullable | `T` | `KMX003` nombrando el segmento culpable — salvo estrategia |
| alguno nullable | `T` + `onNull = THROW` | `address?.city ?: throw IllegalArgumentException(...)` |

- El did-you-mean de un segmento inexistente se calcula sobre el TIPO de ese segmento
  (`'cty' does not exist on Address. Did you mean 'city'?`).
- Tras resolver la ruta aplican TODAS las reglas sobre el tipo final: converters, wrap/unwrap
  (`address.zip.value`), estrategias.
- En PATCH la ruta lee del patch: `note = patch.meta?.note ?: target.note`.
- Solo lectura de propiedades: sin métodos, sin índices, sin des-aplanar (construir anidados
  desde planos no existe en v1).
