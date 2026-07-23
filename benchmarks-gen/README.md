# benchmarks-gen — velocidad de generación

Mide el **tiempo de generación de código** (procesamiento de anotaciones en el build), no el
runtime — el otro benchmark (`../benchmarks`) mide el runtime. Aquí es donde kmapx se despega:
usa **KSP**, MapStruct usa **kapt**, que arrastra la generación de stubs de Java + `javac`.

## Estructura

Dos módulos aislados con el MISMO modelo (N pares generados por `generate.py`):

- `ksp/` — solo kmapx/KSP.
- `kapt/` — solo MapStruct/kapt.

## Correr

```bash
python3 benchmarks-gen/generate.py 40   # (re)genera N=40 pares en ambos módulos
bash    benchmarks-gen/measure.sh 5     # mide la tarea de procesamiento, medianas de 5 corridas
```

La medición aísla la duración de la tarea con `--profile` (excluye el arranque de Gradle, que es
constante y enmascara la diferencia — en wall-clock crudo el ratio se comprime a ~1.2x).

## Resultado (N=40 pares)

| Procesamiento | Tiempo (mediana, menor = mejor) |
|---|---|
| **KSP (kmapx)** | **0.57 s** |
| kapt (MapStruct) | 2.93 s — stubs de Java 0.79 s + procesamiento 2.14 s |

**kmapx genera ~5x más rápido.** El costo de kapt es estructural: genera stubs de Java de todo
el código Kotlin y corre el processor sobre `javac`; KSP trabaja directo sobre los símbolos de
Kotlin y se salta ese paso. En builds grandes e incrementales esa diferencia se acumula (menos
tiempo de compilación, CI más rápido).

En conjunto con el benchmark de runtime, la comparación queda clara:

| Parámetro | kmapx | MapStruct |
|---|---|---|
| Runtime del mapeo | empate (0.257 vs 0.250 ops/µs) | empate |
| **Velocidad de generación** | **~5x más rápido** | — |
| Value classes de Kotlin | nativo | necesita helper manual |
| Seguridad | todo mapeo inseguro = error de compilación | política estricta opt-in |

_(Números en un equipo concreto; correlo para los tuyos. Indicativo, no gradle-profiler.)_
