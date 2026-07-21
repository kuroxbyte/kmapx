# benchmarks

Benchmarks JMH del RUNTIME del mapeo generado. No se publica ni corre en CI (es on-demand).

## Correr

```bash
./gradlew :benchmarks:jmh
```

## Modelo

`Order → OrderDto`, un caso representativo: value class (`OrderId`), objeto anidado
(`Customer`/`Address`), colección de 10 elementos (`List<Item>`), enum (`Status`) y converter
(`Instant → String`, `Long → String`). Ver `src/main/kotlin/.../Model.kt`.

## Resultado (kmapx vs escrito a mano)

kmapx genera Kotlin directo, sin reflection. El baseline es el MISMO mapeo escrito a mano — el
techo de rendimiento. Un empate es el resultado esperado y bueno:

| Benchmark | Throughput (ops/µs, mayor = mejor) |
|---|---|
| escrito a mano | 0.245 ± 0.004 |
| **kmapx** | **0.247 ± 0.003** |

Dentro del margen de error: **el código generado por kmapx es indistinguible del escrito a
mano**. La garantía de "todo mapeo inseguro es error de compilación" no cuesta nada en runtime.

_(Números en un equipo concreto; corré `./gradlew :benchmarks:jmh` para los tuyos. La comparación
vs MapStruct/Konvert/Mappie se añade en iteraciones siguientes.)_
