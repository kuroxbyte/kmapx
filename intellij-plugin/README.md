# kmapx — plugin de IntelliJ (v0.9)

**Navegación** — gutter icons hacia el código generado por KSP, a nivel declaración:

| Sobre | Salta a |
|---|---|
| clase `@MapTo` / `@BiMapTo` | las extension functions generadas (una por mapeo) en `<Source>Mappings.kt` |
| interfaz `@Mapper` | la declaración de `<Interface>Impl` |
| MÉTODO abstracto del `@Mapper` | su `override fun` en el Impl |

Si el proyecto aún no compiló (no existe el generado), no hay marker — mejor ausencia que un
salto roto.

**Referencias** — los strings de `@MapField` en la sede de método dejan de ser strings mudos:
`target = "x"` referencia la propiedad del tipo de RETORNO y `from = "y"` la del primer
parámetro. Con eso vienen Ctrl+B, find-usages y el COMPLETADO dentro de las comillas.
v0.2 cubre nombres planos (las rutas `a.b.c` y la sede de campo: v2).

**Inspecciones con quick-fixes** — los KMX localmente decidibles, en el editor y con alt+enter:
KMX036 (target en sede de campo → quitarlo), KMX038 (`LITERAL` sin `default` → agregarlo),
KMX039 (`default` ignorado → quitarlo), KMX043 (`ignore` + otros aspectos) y el
"target/from no existe" con did-you-mean aplicable. Las reglas y los textos vienen del MISMO
core que usa el compilador (`MapFieldRules`/`Diagnostics` vía composite build) — el plugin no
duplica ni una regla.

**El motor real en el editor** (`adapter-psi`) — AMBOS modos. Los `@MapTo` del modo embedded se resuelven
con el MISMO `MappingEngine` del compilador: KMX003 (nulabilidad) y KMX002 (campo sin fuente,
con el did-you-mean del motor) aparecen al escribir, y el alt+enter de KMX003 anota el
parámetro del target con `@MapField(onNull = OnNull.THROW)` — imports incluidos. En el modo
CONTRACT, los métodos de mapeo de un `@Mapper` se inspeccionan con la cascada completa
(`@MapField(target=)` del método > `@Mapper(onNull=)` > profile `@MapperConfig`) y el alt+enter
anota el MÉTODO (`@MapField(target = "x", onNull = OnNull.THROW)`) — el dominio no se toca.
Desde v0.6 el motor corre con el ESTADO del editor completo: la config global del build
(`kmapx.onNull`/`kmapx.stdConverters`, leídos de `gradle.properties` y del bloque `kmapx {}`)
y el índice colaborativo del proyecto (mapeos declarados y `@Converter`s) — con él,
KMX004/KMX007/KMX009 también aparecen al escribir, y el alt+enter de KMX007 declara el
`@MapTo` anidado que falta. Salvedad restante: con `useSerialNames`, patch, multi-fuente o
`@InverseOf` la inspección se abstiene (el build los cubre).

**Preview del código generado**: alt+enter sobre un `@MapTo` o un método de mapper →
"kmapx: ver el código generado" — el plan del motor materializado con el PlanEmitter REAL del
backend; los planes inválidos se muestran como diagnósticos comentados.

**Rutas e ignore**: `from = "a.b.c"` navega/completa/renombra POR SEGMENTO (did-you-mean que
repara solo el tramo roto) y los strings de `ignore = [...]` referencian la propiedad del
target — renombrarla actualiza el string.

**Verificado**: 60 tests con el IDE headless (`BasePlatformTestCase`) cubren markers,
referencias, completado, ambas inspecciones y la APLICACIÓN de cada quick-fix.

## Build

Es un proyecto **standalone** (no participa del build raíz: el IntelliJ Platform Gradle Plugin
descarga una distribución completa del IDE — decisión de diseño):

```bash
./gradlew -p intellij-plugin buildPlugin    # → intellij-plugin/build/distributions/*.zip
./gradlew -p intellij-plugin runIde         # sandbox para probarlo a mano
```

Instalación manual: Settings → Plugins → ⚙ → Install Plugin from Disk → el zip de distributions.

**v0.7–v0.9**: enums en el editor (KMX026/047/023 con quick-fixes y referencias en
`@MapEntry`), referencias de `@MapField(from=)` en sede de CAMPO (vía el índice inverso),
gutter de navegación inversa sobre el TARGET, las formas colección/patch/@InverseOf del modo
contract en la inspección (KMX046 con fix que declara el método del elemento, KMX012, KMX045),
y la intención "crear el DTO espejo con @MapTo".

## Publicación (preparada, pendiente del hito 0.1.0)

`./gradlew -p intellij-plugin verifyPlugin` valida el plugin contra los IDEs recomendados
del rango de compatibilidad antes de publicarlo al Marketplace.

## Qué NO hace (todavía) — por diseño

- Inlay hints de la cascada efectiva (API declarativa aún inestable entre versiones).
- Patch con `useSerialNames` (el `resolvePatch` del core no acepta el alias): abstención.
- Materializar interfaces con patch/colecciones/`@InverseOf`/`componentModel` (aborta con hint).
