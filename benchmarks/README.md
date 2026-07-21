# benchmarks

Benchmarks JMH del RUNTIME del mapeo generado. No se publica ni corre en CI (es on-demand).

## Correr

```bash
./gradlew :benchmarks:jmh        # los benchmarks (tarda unos minutos)
./gradlew :benchmarks:run        # sanity: los tres mappers dan el mismo resultado
```

## Modelo

`Order → OrderDto`, un caso representativo: value class (`OrderId`), objeto anidado
(`Customer`/`Address`), colección de 10 elementos (`List<Item>`), enum (`Status`) y converter
(`Instant → String`, `Long → String`). Ver `src/main/kotlin/.../Model.kt`.

## Resultado

kmapx genera Kotlin directo, sin reflection; MapStruct genera Java directo, sin reflection. El
baseline escrito a mano es el techo de rendimiento. Los tres producen el MISMO `OrderDto`
(verificado por `Verify.kt`), así que la comparación es justa:

| Mapper | Throughput (ops/µs, mayor = mejor) |
|---|---|
| escrito a mano | 0.248 ± 0.005 |
| **kmapx** | **0.257 ± 0.003** |
| MapStruct | 0.250 ± 0.005 |

**Los tres están estadísticamente empatados.** kmapx está a la par de MapStruct y del código
escrito a mano: la garantía de "todo mapeo inseguro es error de compilación", el cero reflection
y el código navegable **no cuestan nada en runtime**. La diferencia de kmapx no es la velocidad
—es igual de rápido— sino la seguridad y la ergonomía en compile-time.

Nota Kotlin: MapStruct no entiende los value classes de Kotlin (el getter va mangled), así que su
mapper necesita un método helper para `OrderId`; kmapx los (des)envuelve solo. Ver
`MapStructMappers.kt` vs `Model.kt`.

_(Números en un equipo concreto; corré `./gradlew :benchmarks:jmh` para los tuyos. Konvert/Mappie
se añaden en iteraciones siguientes.)_
