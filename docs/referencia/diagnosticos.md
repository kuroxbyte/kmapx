# Catálogo de diagnósticos (KMX001–KMX047)

> **GENERADO** desde `core/.../diagnostics/Diagnostics.kt` por `DiagnosticsCatalog` —
> no editar a mano (el test `DiagnosticsCatalogTest` falla si difiere; regenerar con
> `./gradlew :core:test -Dkmapx.updateDocs=true`). Las descripciones salen del KDoc del
> catálogo y los ejemplos se RENDERIZAN con las factories reales.

Formato canónico de todo diagnóstico: `[KMXnnn] <ubicación> <problema>. Fix: <acción>.`
Los WARNING se silencian por código con `@SuppressKmapx("KMXnnn")`; los errores, nunca.

## KMX001 — ERROR

Internal invariant broken while resolving. Always a kmapx bug.

```
[KMX001] com.example.PersonDto internal error: unexpected symbol kind. Fix: this is a kmapx bug; please report it with a minimal reproducer.
```

## KMX002 — ERROR

Target constructor parameter has no source property and no declared mapping.

```
[KMX002] com.example.PersonDto.age no source property found for constructor parameter 'age'. Did you mean 'ageYears'? Fix: add a matching source property, or use @MapField(from = "...") to rename.
```

## KMX003 — ERROR

Nullability violation: source `T?` mapped to non-nullable target without a strategy.

```
[KMX003] com.example.PersonDto.nickname is non-nullable but source Person.nickname is nullable. Fix: add a default parameter (with onNull = TARGET_DEFAULT), or annotate with @MapField(onNull = LITERAL/THROW/UNSAFE).
```

## KMX004 — ERROR

Incompatible types with no known conversion and no registered converter.

```
[KMX004] com.example.PersonDto.createdAt cannot convert java.time.Instant to kotlin.String. Fix: register a @Converter fun (java.time.Instant) -> kotlin.String, or declare a mapping between the types.
```

## KMX005 — ERROR

No resolvable constructor: primary not visible/usable and no @MapConstructor/@MapFactory.

```
[KMX005] com.example.PersonDto no resolvable constructor: the primary constructor is not visible (or absent) and no @MapConstructor/@MapFactory is declared. Fix: annotate a constructor with @MapConstructor, or a companion/top-level function returning PersonDto with @MapFactory.
```

## KMX006 — ERROR

Ambiguous construction: more than one @MapConstructor or @MapFactory applies.

```
[KMX006] com.example.PersonDto ambiguous construction: 2 candidates (@MapConstructor(name), @MapFactory of). Fix: keep exactly one @MapConstructor or @MapFactory; remove the annotation from the unwanted candidates.
```

## KMX007 — ERROR

Nested mapping required but not declared: annotate the nested type or register a converter.

```
[KMX007] com.example.PersonDto.address no mapping found for Address -> AddressDto required by 'address'. Fix: annotate Address with @MapTo(AddressDto::class) or register a @Converter.
```

## KMX008 — ERROR

Mapping cycle detected across declared mappings.

```
[KMX008] com.example.PersonDto mapping cycle detected: Person -> Address -> Person. Fix: break the cycle: map the nested type with a @Converter, or remove one of the declarations.
```

## KMX009 — ERROR

Ambiguous converters: more than one @Converter applies to the same conversion.

```
[KMX009] com.example.PersonDto.createdAt ambiguous converters for java.time.Instant -> kotlin.String: toIso, toEpoch. Fix: keep exactly one @Converter for the pair, or convert explicitly per field (post-1.0).
```

## KMX010 — ERROR

Sealed dispatch: source subtype has no counterpart in the target hierarchy.

```
[KMX010] com.example.Event.Refunded has no counterpart in EventDto. Fix: add EventDto.Refunded or declare @MapSubtype on the source subtype.
```

## KMX011 — ERROR

Renamed source property (@MapFrom(from=...)) does not exist.

```
[KMX011] com.example.PersonDto.name source property 'firstnme' does not exist. Did you mean 'firstname'? Fix: point @MapField(from = "...") to an existing source property.
```

## KMX012 — ERROR

@PatchMapper target is not a data class: copy() is unavailable.

```
[KMX012] com.example.PersonDto patch mapping requires the target to be a data class (copy() is needed for immutable update). Fix: make com.example.PersonDto a data class, or declare a regular mapping that constructs a new instance.
```

## KMX013 — ERROR

Two @MapTo targets produce the same generated function name; an explicit name is required.

```
[KMX013] com.example.PersonDto ambiguous generated function name 'toDto': 2 @MapTo targets share it (a.Dto, b.Dto). Fix: set an explicit name with @MapTo(..., name = "...") on the conflicting declarations.
```

## KMX014 — ERROR

@Mapper after-function has the wrong signature: must be (Source, Target) -> Target.

```
[KMX014] com.example.PersonDto.afterToDto after-function 'afterToDto' must have signature (Person, PersonDto) -> PersonDto. Fix: declare it as a default method: fun afterToDto(source: Person, result: PersonDto): PersonDto.
```

## KMX015 — ERROR

@Mapper interface shape not supported in v1: generics or inheritance between @Mapper interfaces.

```
[KMX015] com.example.PersonDto unsupported @Mapper shape: generic @Mapper interfaces are not supported in v1. Fix: declare a plain, non-generic interface that does not extend another @Mapper interface.
```

## KMX016 — ERROR

More than one null-handling strategy declared on the same target parameter/property.

```
[KMX016] com.example.PersonDto.nickname multiple null-handling strategies declared. Fix: declare exactly one strategy: @MapField(onNull = ...) is exclusive by construction.
```

## KMX017 — ERROR

@WithDefault literal not parseable for the target type, or type unsupported.

```
[KMX017] com.example.PersonDto.age "abc" is not a valid Int default. Fix: provide a parseable literal, or use onNull = THROW.
```

## KMX018 — WARNING

Null-handling strategy declared where there is no `T? -> T` violation: dead strategy (WARNING).

```
[KMX018] com.example.PersonDto.nickname null-handling strategy declared but the source is not nullable (dead strategy). Fix: remove the annotation.
```

## KMX019 — ERROR

@Converter function with an invalid shape: must be a pure top-level `fun (A) -> B`.

```
[KMX019] com.example.toIso invalid @Converter signature: must take exactly one parameter. Fix: declare a pure top-level function with exactly one parameter and a non-Unit return type: @Converter fun (A) -> B.
```

## KMX020 — ERROR

@MapFrom(from = ...) with malformed path syntax: empty segments, leading/trailing dots.

```
[KMX020] com.example.PersonDto.city malformed path 'address..city': empty segments are not allowed. Fix: use dot-separated property names, e.g. @MapField(from = "address.city").
```

## KMX021 — WARNING

Parameter has no source but declares a default: filled by the target default (WARNING).

```
[KMX021] com.example.PersonDto.nickname filled by the target default (no matching source property). Fix: add a matching source property if the omission is unintended.
```

## KMX022 — ERROR

More omissible defaults than the emission limit K=2.

```
[KMX022] com.example.PersonDto 3 parameters would need conditional omission (nickname, bio, tag); the limit is 2. Fix: annotate the extra ones with @MapField(onNull = LITERAL/THROW/UNSAFE), or make them nullable in the target.
```

## KMX023 — WARNING

Sealed dispatch: TARGET subtype has no source counterpart — the when stays exhaustive (WARNING).

```
[KMX023] com.example.EventDto.Cancelled has no counterpart in Event (the generated when remains exhaustive over the source). Fix: remove the target subtype, or add the source subtype if it was expected.
```

## KMX024 — ERROR

Sealed dispatch: nested sealed hierarchies — one level only in v1.

```
[KMX024] com.example.Event.Inner nested sealed hierarchies are not supported in v1 (one level only). Fix: flatten the hierarchy, or map the nested level with its own @MapTo.
```

## KMX025 — ERROR

KMP: `expect` declarations cannot be mapped in v1.

```
[KMX025] com.example.PlatformClock expect declarations cannot be mapped in v1. Fix: annotate the actual class per target, or map a common concrete class.
```

## KMX026 — ERROR

Enum dispatch: SOURCE entry has no counterpart in the target enum, or @MapEntry points nowhere.

```
[KMX026] com.example.Color.CRIMSON has no counterpart in ColorDto. Did you mean 'CARMINE'? Fix: add ColorDto.CRIMSON or declare @MapEntry on the source entry.
```

## KMX027 — ERROR

@UseConverter: the referenced Converts<A,B> does not match the field's source/target types.

```
[KMX027] com.example.PersonDto.startDate converter ShortDate expects LocalDate -> String but field requires Int -> String. Fix: use a converter whose Converts<A, B> matches the field, or remove the converter aspect.
```

## KMX028 — ERROR

@BiMapTo: the mapping is not invertible — asymmetric field, missing inverse converter, fan-out.

```
[KMX028] com.example.PersonDto.createdAt not invertible: missing converter kotlin.String -> java.time.Instant for the reverse direction. Fix: register the inverse @Converter, or use two one-way @MapTo declarations.
```

## KMX029 — ERROR

@UseConverter: the referenced object does not implement `dev.kmapx.runtime.Converts`.

```
[KMX029] com.example.PersonDto.startDate @MapField converter NotAConverter does not implement dev.kmapx.runtime.Converts. Fix: reference an object that implements Converts<A, B> for the field's types.
```

## KMX030 — ERROR

componentModel requires a framework that is not on the compile classpath.

```
[KMX030] com.example.CustomerMapper componentModel SPRING requires org.springframework:spring-context on the classpath. Fix: add the dependency, or use componentModel NONE.
```

## KMX031 — WARNING

@UseConverter declared where source and target types are identical: unnecessary (WARNING).

```
[KMX031] com.example.PersonDto.name @MapField converter Trim is unnecessary: source and target types are identical. Fix: remove the converter aspect; the value maps directly.
```

## KMX032 — WARNING

Per-field config declared both on the field and on the @Mapper method; the method wins (WARNING).

```
[KMX032] com.example.PersonDto.name configuration for 'name' is declared both on the field and on the @Mapper method. Fix: keep it in one place; the method-level configuration wins.
```

## KMX033 — ERROR

@OrEmpty declared on a field that is not a List/Set/Map: no empty collection applies.

```
[KMX033] com.example.PersonDto.meta onNull = TYPE_DEFAULT applies only to List/Set/Map and Int/Long/Short/Byte/Double/Float/Boolean/String, not com.example.Meta. Fix: use onNull = LITERAL/THROW for this field, or remove the strategy.
```

## KMX034 — ERROR

@UseConverter with an injected (class) converter used in mode A: nowhere to inject.

```
[KMX034] com.example.PersonDto.customerName injected converter CustomerName (a class with dependencies) can only be used from a @Mapper (mode B). Fix: declare the mapping in a @Mapper interface, or make the converter a stateless object.
```

## KMX035 — ERROR

Injected converter class is not a @Component but componentModel = SPRING.

```
[KMX035] com.example.CustomerMapper injected converter RiskLabeler must be a @Component to be injected with componentModel = SPRING. Fix: annotate RiskLabeler with @Component, or use componentModel = NONE.
```

## KMX036 — ERROR

@MapField addressing: `target` is required on the method site and forbidden on the field site.

```
[KMX036] com.example.PersonDto.nickname @MapField on a field must not set target: the annotated field IS the destination. Fix: remove target = "..." (or move the annotation to the mapper method).
```

## KMX037 — ERROR

More than one @MapField for the same destination field.

```
[KMX037] com.example.PersonDto.nickname multiple @MapField declarations for 'nickname'. Fix: merge them into a single @MapField: one annotation per destination field.
```

## KMX038 — ERROR

@MapField(onNull = LITERAL) without a `default` literal.

```
[KMX038] com.example.PersonDto.nickname onNull = LITERAL requires a default literal. Fix: set default = "...", or choose another OnNull strategy.
```

## KMX039 — WARNING

@MapField `default` set but onNull != LITERAL: the literal is ignored (WARNING).

```
[KMX039] com.example.PersonDto.nickname default is ignored when onNull != LITERAL. Fix: remove default, or set onNull = OnNull.LITERAL.
```

## KMX040 — ERROR

@MapField(onNull = TARGET_DEFAULT) on a parameter without a usable constructor default.

```
[KMX040] com.example.PersonDto.nickname onNull = TARGET_DEFAULT but 'nickname' has no usable constructor default (a default value and identical types are required). Fix: add a default value to the target parameter, or choose another OnNull strategy.
```

## KMX041 — ERROR

onNull = LITERAL/UNSAFE used as a mapper/mapping-level policy: those are field-only.

```
[KMX041] com.example.CustomerMapper onNull = LITERAL is not a mapper/mapping-level policy: a level cannot carry a literal or a blanket !!. Fix: move it to @MapField(onNull = LITERAL) on the target field, or use STRICT/THROW/TYPE_DEFAULT/TARGET_DEFAULT at this level.
```

## KMX042 — ERROR

Ignored field cannot be omitted: no constructor default.

```
[KMX042] com.example.PersonDto.createdAt 'createdAt' is ignored but has no constructor default: the argument cannot be omitted. Fix: add a default value to the target parameter (for nullables: `= null`), or remove the ignore.
```

## KMX043 — ERROR

@MapField(ignore = true) combined with other aspects: dead configuration.

```
[KMX043] com.example.PersonDto.createdAt ignore = true makes the other @MapField aspects dead configuration. Fix: remove from/converter/onNull/default, or remove ignore = true.
```

## KMX044 — ERROR

@Mapper(config = X::class) where X is not a valid @MapperConfig profile.

```
[KMX044] com.example.CustomerMapper config NotAProfile is not annotated with @MapperConfig. Fix: annotate NotAProfile with @MapperConfig and keep it free of abstract methods (a profile carries settings, not mappings).
```

## KMX045 — ERROR

@InverseOf: forward method missing/ambiguous, wrong shape, or own @MapField declared.

```
[KMX045] com.example.CustomerMapper.fromDto invalid @InverseOf: no method with the inverse signature found (auto-detection). Fix: point @InverseOf at a single-parameter mapping method with the exact inverse signature, and keep the inverse method free of its own @MapField.
```

## KMX046 — ERROR

Contract collection method with no element mapping to delegate to.

```
[KMX046] com.example.OrderMapper.toDtos no element mapping to delegate to for com.example.Order -> com.example.OrderDto. Fix: declare an abstract method mapping com.example.Order to com.example.OrderDto in this mapper, or a @MapTo on the source element.
```

## KMX047 — ERROR

Class-level @MapEntry fallback pointing at a non-existent target entry.

```
[KMX047] com.example.LegacyStatus class-level @MapEntry fallback 'UNKNWN' does not exist in Status. Did you mean 'UNKNOWN'? Fix: point the fallback at an existing entry of Status.
```

