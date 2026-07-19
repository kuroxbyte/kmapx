package dev.kmapx.core

import dev.kmapx.core.diagnostics.DiagnosticCode
import dev.kmapx.core.engine.MappingEngine
import dev.kmapx.core.engine.NullPolicy
import dev.kmapx.core.engine.UnmappedPolicy
import dev.kmapx.core.plan.Construction
import dev.kmapx.core.model.TypeKind
import dev.kmapx.core.plan.Emission
import dev.kmapx.core.plan.ValueSource
import dev.kmapx.reflect.mclassOf
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

// ── Fixtures (casos de test) ─────────────────────────────────────

@JvmInline value class UserId(val value: String)

data class Person(val name: String, val age: Int)
data class PersonDto(val name: String, val age: Int)

data class NickSource(val name: String, val nickname: String?)
data class NickTargetStrict(val name: String, val nickname: String)     // T? -> T sin estrategia

data class TagsSource(val tags: List<String>?)
data class TagsTarget(val tags: List<String>)                            // política TYPE_DEFAULT
data class NickTargetNullable(val name: String, val nickname: String?)  // T? -> T?

data class Wrapped(val id: UserId)
data class Unwrapped(val id: String)

data class WrappedNullable(val id: UserId?)
data class UnwrappedNullable(val id: String?)
data class Wrapped2(val id: UserId)

@JvmInline value class WrapperOfWrapper(val id: UserId)
data class DoubleWrapped(val id: WrapperOfWrapper)

data class TypoTarget(val nmae: String, val age: Int)

sealed interface Payment {
    data class Approved(val amount: Long) : Payment
    data object Pending : Payment
}

sealed interface PaymentDto {
    data class Approved(val amount: Long) : PaymentDto
    data object Pending : PaymentDto
}

sealed interface PaymentDtoExtra {
    data class Approved(val amount: Long) : PaymentDtoExtra
    data object Pending : PaymentDtoExtra
    data object Cancelled : PaymentDtoExtra
}

sealed interface RefundedPayment {
    data class Approved(val amount: Long) : RefundedPayment
    data object Refunded : RefundedPayment
}

sealed interface DeepEvent {
    sealed interface Inner : DeepEvent { data class A(val x: Int) : Inner }
}

sealed interface StrictSrcEvent {
    data class Note(val text: String?) : StrictSrcEvent
}

sealed interface StrictDtoEvent {
    data class Note(val text: String) : StrictDtoEvent
}

class RegularWithVar(val id: String) { var status: String = "NEW" }

enum class Color { RED, GREEN }
enum class ColorDto { RED, GREEN }
enum class ColorExtra { RED, GREEN, BLUE }
enum class ColorDtoExtra { RED, GREEN, CANCELLED }

class EngineTest {

    private val engine = MappingEngine()
    private fun ext(name: String) = Emission.ExtensionFunction(name)

    @Test
    fun `matching 1-1 homonimo produce todos Direct y plan valido`() {
        val plan = engine.resolve(mclassOf<Person>(), mclassOf<PersonDto>(), ext("toPersonDto"))

        assertTrue(plan.valid)
        val ctor = assertNotNull(plan.construction) as dev.kmapx.core.plan.Construction.ConstructorCall
        assertEquals(setOf("name", "age"), ctor.arguments.map { it.paramName }.toSet())
        ctor.arguments.forEach { assertIs<ValueSource.Direct>(it.value) }
    }

    @Test
    fun `propiedad target sin fuente produce KMX002 como dato, no excepcion`() {
        data class Missing(val name: String, val email: String)
        val plan = engine.resolve(mclassOf<Person>(), mclassOf<Missing>(), ext("toMissing"))

        assertFalse(plan.valid)
        val diag = plan.diagnostics.single()
        assertEquals(DiagnosticCode.KMX002, diag.code)
        assertEquals("email", diag.location.member)
    }

    @Test
    fun `cascada - politica THROW inyecta OrThrow implicito en vez de KMX003`() {
        val plan = engine.resolve(
            mclassOf<NickSource>(), mclassOf<NickTargetStrict>(), ext("toStrict"),
            nullPolicies = listOf(NullPolicy.OR_THROW),
        )
        assertTrue(plan.valid, plan.diagnostics.toString())
        val ctor = assertIs<Construction.ConstructorCall>(plan.construction)
        assertIs<ValueSource.NullOrThrow>(ctor.arguments.single { it.paramName == "nickname" }.value)
    }

    @Test
    fun `cascada - politica TYPE_DEFAULT cierra List nullable con emptyList`() {
        val plan = engine.resolve(
            mclassOf<TagsSource>(), mclassOf<TagsTarget>(), ext("toTags"),
            nullPolicies = listOf(NullPolicy.TYPE_DEFAULT),
        )
        assertTrue(plan.valid, plan.diagnostics.toString())
        val ctor = assertIs<Construction.ConstructorCall>(plan.construction)
        assertEquals("emptyList()", assertIs<ValueSource.NullFallbackToValue>(ctor.arguments.single().value).default)
    }

    @Test
    fun `config global - sin las flags, T nullable a T sigue en KMX003 (STRICT por defecto)`() {
        val plan = engine.resolve(mclassOf<TagsSource>(), mclassOf<TagsTarget>(), ext("toTags"))
        assertFalse(plan.valid)
        assertEquals(DiagnosticCode.KMX003, plan.diagnostics.single().code)
    }

    @Test
    fun `T nullable a T no-nullable produce KMX003`() {
        val plan = engine.resolve(mclassOf<NickSource>(), mclassOf<NickTargetStrict>(), ext("toStrict"))

        assertFalse(plan.valid)
        val diag = plan.diagnostics.single()
        assertEquals(DiagnosticCode.KMX003, diag.code)
        assertEquals("nickname", diag.location.member)
        // Contrato de nulabilidad: las 3 estrategias siempre (vocabulario @MapField);
        // TARGET_DEFAULT se sugiere como salida directa solo cuando el parámetro tiene default.
        assertTrue(diag.fix.contains("@MapField"))
        assertTrue(diag.fix.contains("LITERAL"))
        assertTrue(diag.fix.contains("THROW"))
        assertTrue(diag.fix.contains("UNSAFE"))
        assertTrue(diag.fix.contains("add a default parameter"), diag.fix)
    }

    @Test
    fun `T nullable a T nullable es silencioso`() {
        val plan = engine.resolve(mclassOf<NickSource>(), mclassOf<NickTargetNullable>(), ext("toNullable"))
        assertTrue(plan.valid)
    }

    @Test
    fun `value class - underlying poblado y unwrap representado`() {
        val wrapped = mclassOf<Wrapped>()
        val idType = wrapped.property("id")!!.type
        assertEquals(TypeKind.VALUE_CLASS, idType.kind)
        assertEquals("kotlin.String", idType.underlying?.qualifiedName)

        val plan = engine.resolve(wrapped, mclassOf<Unwrapped>(), ext("toUnwrapped"))
        assertTrue(plan.valid)
        val ctor = plan.construction as dev.kmapx.core.plan.Construction.ConstructorCall
        assertIs<ValueSource.UnwrapValueClass>(ctor.arguments.single().value)
    }

    @Test
    fun `value class - wrap representado`() {
        val plan = engine.resolve(mclassOf<Unwrapped>(), mclassOf<Wrapped>(), ext("toWrapped"))
        assertTrue(plan.valid)
        val ctor = plan.construction as dev.kmapx.core.plan.Construction.ConstructorCall
        assertIs<ValueSource.WrapValueClass>(ctor.arguments.single().value)
    }

    @Test
    fun `composicion nullable - unwrap y wrap con safeCall`() {
        // `UserId? -> String?` = `id?.value`:
        val unwrapPlan = engine.resolve(
            mclassOf<WrappedNullable>(), mclassOf<UnwrappedNullable>(), ext("toUnwrapped"),
        )
        val unwrap = assertIs<ValueSource.UnwrapValueClass>(singleValue(unwrapPlan))
        assertTrue(unwrap.safeCall)

        // `String? -> UserId?` = `s?.let { UserId(it) }`:
        val wrapPlan = engine.resolve(
            mclassOf<UnwrappedNullable>(), mclassOf<WrappedNullable>(), ext("toWrapped"),
        )
        val wrap = assertIs<ValueSource.WrapValueClass>(singleValue(wrapPlan))
        assertTrue(wrap.safeCall)
    }

    @Test
    fun `passthrough UserId a UserId es directo, sin unwrap redundante`() {
        val plan = engine.resolve(mclassOf<Wrapped>(), mclassOf<Wrapped2>(), ext("toWrapped2"))
        assertIs<ValueSource.Direct>(singleValue(plan))
    }

    @Test
    fun `UserId nullable a String no-nullable exige estrategia (KMX003)`() {
        val plan = engine.resolve(mclassOf<WrappedNullable>(), mclassOf<Unwrapped>(), ext("toUnwrapped"))
        assertFalse(plan.valid)
        assertEquals(DiagnosticCode.KMX003, plan.diagnostics.single().code)
    }

    @Test
    fun `la estrategia aplica SOBRE el resultado del unwrap nullable`() {
        val userIdType = dev.kmapx.core.model.MType(
            "fx.UserId", nullable = true, kind = TypeKind.VALUE_CLASS,
            underlying = mtype("kotlin.String"),
        )
        val source = dev.kmapx.core.model.MClass(
            type = mtype("fx.Src"),
            properties = listOf(dev.kmapx.core.model.MProperty("id", userIdType)),
        )
        val target = dev.kmapx.core.model.MClass(
            type = mtype("fx.Dto"),
            constructors = listOf(
                dev.kmapx.core.model.MConstructor(
                    params = listOf(
                        dev.kmapx.core.model.MConstructorParam(
                            "id", mtype("kotlin.String"),
                            strategies = listOf(dev.kmapx.core.model.MNullStrategy.OrThrow),
                        ),
                    ),
                ),
            ),
        )
        val orThrow = engine.resolve(source, target, ext("toDto"))
        assertTrue(orThrow.valid, orThrow.diagnostics.joinToString { it.render() })
        val over = assertIs<ValueSource.NullStrategyOver>(
            assertIs<dev.kmapx.core.plan.Construction.ConstructorCall>(orThrow.construction).arguments.single().value,
        )
        assertTrue(assertIs<ValueSource.UnwrapValueClass>(over.inner).safeCall)
        assertIs<dev.kmapx.core.plan.StrategyOutcome.Throw>(over.outcome)
    }

    @Test
    fun `el converter del usuario gana sobre el wrap implicito`() {
        val plan = engine.resolve(
            mclassOf<Unwrapped>(), mclassOf<Wrapped>(), ext("toWrapped"),
            converters = mapOf(("kotlin.String" to "dev.kmapx.core.UserId") to listOf("fx.parseUserId")),
        )
        assertIs<ValueSource.ViaConverter>(singleValue(plan))
    }

    @Test
    fun `encadenado value-de-value de dos niveles produce KMX004`() {
        val plan = engine.resolve(mclassOf<DoubleWrapped>(), mclassOf<Unwrapped>(), ext("toUnwrapped"))
        assertFalse(plan.valid)
        assertEquals(DiagnosticCode.KMX004, plan.diagnostics.single().code)
    }

    @Test
    fun `did you mean sugiere el nombre con distancia menor o igual a 2`() {
        val plan = engine.resolve(mclassOf<Person>(), mclassOf<TypoTarget>(), ext("toTypo"))
        val diag = plan.diagnostics.first { it.code == DiagnosticCode.KMX002 }
        assertTrue(diag.message.contains("Did you mean 'name'?"), diag.message)
    }

    @Test
    fun `did you mean con empate lista 2 candidatos`() {
        data class TiedSource(val namex: String, val namey: String)
        data class Target(val name: String)
        val plan = engine.resolve(mclassOf<TiedSource>(), mclassOf<Target>(), ext("toTarget"))
        val diag = plan.diagnostics.single { it.code == DiagnosticCode.KMX002 }
        assertTrue(diag.message.contains("Did you mean 'namex' or 'namey'?"), diag.message)
    }

    @Test
    fun `did you mean es case-insensitive`() {
        data class UpperSource(val fullName: String)
        data class Target(val fullname: String)
        val plan = engine.resolve(mclassOf<UpperSource>(), mclassOf<Target>(), ext("toTarget"))
        val diag = plan.diagnostics.single { it.code == DiagnosticCode.KMX002 }
        assertTrue(diag.message.contains("Did you mean 'fullName'?"), diag.message)
    }

    @Test
    fun `KMX008 detecta el ciclo con su camino completo`() {
        val cycle = dev.kmapx.core.engine.MappingGraph.findCycle(
            mapOf(
                "fx.Person" to listOf("fx.Address"),
                "fx.Address" to listOf("fx.Person"),
            ),
        )
        assertEquals(listOf("fx.Person", "fx.Address", "fx.Person"), cycle)

        val diag = dev.kmapx.core.diagnostics.Diagnostics.mappingCycle(
            dev.kmapx.core.diagnostics.MLocation("fx.Person"),
            cycle!!.map { it.substringAfterLast('.') },
        )
        assertEquals(DiagnosticCode.KMX008, diag.code)
        assertTrue(diag.message.contains("Person -> Address -> Person"), diag.message)
    }

    @Test
    fun `grafo aciclico no reporta ciclo y self-loop si`() {
        assertEquals(
            null,
            dev.kmapx.core.engine.MappingGraph.findCycle(
                mapOf("A" to listOf("B"), "B" to listOf("C")),
            ),
        )
        assertEquals(
            listOf("A", "A"),
            dev.kmapx.core.engine.MappingGraph.findCycle(mapOf("A" to listOf("A"))),
        )
    }

    @Test
    fun `sealed - sealedSubtypes poblado por el adapter`() {
        val payment = mclassOf<Payment>()
        assertEquals(TypeKind.SEALED_INTERFACE, payment.type.kind)
        assertEquals(
            setOf("Approved", "Pending"),
            payment.sealedSubtypes.map { it.type.simpleName }.toSet(),
        )
    }

    @Test
    fun `clase regular con var en cuerpo - modelo correcto`() {
        val regular = mclassOf<RegularWithVar>()
        assertEquals(TypeKind.REGULAR_CLASS, regular.type.kind)
        val status = assertNotNull(regular.property("status"))
        assertTrue(status.mutable)
        assertFalse(status.inConstructor)
        val id = assertNotNull(regular.property("id"))
        assertTrue(id.inConstructor)
    }

    @Test
    fun `hasDefault detectado via isOptional`() {
        data class WithDefault(val name: String, val nickname: String = "N/A")
        val ctor = assertNotNull(mclassOf<WithDefault>().primaryConstructor)
        assertTrue(ctor.params.first { it.name == "nickname" }.hasDefault)
        assertFalse(ctor.params.first { it.name == "name" }.hasDefault)
    }

    @Test
    fun `el motor nunca lanza - siempre diagnosticos`() {
        // Un target sin propiedades coincidentes en absoluto:
        data class Alien(val zzz: Long, val qqq: Boolean)
        val plan = engine.resolve(mclassOf<Person>(), mclassOf<Alien>(), ext("toAlien"))
        assertFalse(plan.valid)
        assertEquals(2, plan.diagnostics.size)
        plan.diagnostics.forEach { assertEquals(DiagnosticCode.KMX002, it.code) }
    }

    // ── Resolución determinista de construcción ────────────────────────
    // @MapConstructor/@MapFactory tienen retención SOURCE (reflection no las ve):
    // estos tests construyen el modelo a mano — el core es datos puros.

    private fun mtype(qualified: String) = dev.kmapx.core.model.MType(qualified, nullable = false)
    private fun param(name: String, type: String) = dev.kmapx.core.model.MConstructorParam(name, mtype(type))

    private val moneySource = dev.kmapx.core.model.MClass(
        type = mtype("fx.MoneySrc"),
        properties = listOf(
            dev.kmapx.core.model.MProperty("cents", mtype("kotlin.Long")),
            dev.kmapx.core.model.MProperty("currency", mtype("kotlin.String")),
        ),
    )

    @Test
    fun `MapConstructor en secundario gana sobre el primario`() {
        val target = dev.kmapx.core.model.MClass(
            type = mtype("fx.Money"),
            constructors = listOf(
                dev.kmapx.core.model.MConstructor(
                    params = listOf(param("cents", "kotlin.Long")),
                    isPrimary = true,
                    visible = false, // private
                ),
                dev.kmapx.core.model.MConstructor(
                    params = listOf(param("cents", "kotlin.Long"), param("currency", "kotlin.String")),
                    isPrimary = false,
                    annotatedMapConstructor = true,
                ),
            ),
        )
        val plan = engine.resolve(moneySource, target, ext("toMoney"))
        assertTrue(plan.valid, plan.diagnostics.joinToString { it.render() })
        val ctor = assertIs<dev.kmapx.core.plan.Construction.ConstructorCall>(plan.construction)
        assertEquals(listOf("cents", "currency"), ctor.arguments.map { it.paramName })
    }

    @Test
    fun `MapFactory usada cuando el primario es privado`() {
        val target = dev.kmapx.core.model.MClass(
            type = mtype("fx.Temperature"),
            constructors = listOf(
                dev.kmapx.core.model.MConstructor(
                    params = listOf(param("cents", "kotlin.Long")),
                    isPrimary = true,
                    visible = false,
                ),
            ),
            factories = listOf(
                dev.kmapx.core.model.MFactory(
                    qualifiedName = "fx.Temperature.fromCents",
                    params = listOf(param("cents", "kotlin.Long")),
                    companionOf = "fx.Temperature",
                ),
            ),
        )
        val plan = engine.resolve(moneySource, target, ext("toTemperature"))
        assertTrue(plan.valid, plan.diagnostics.joinToString { it.render() })
        val factory = assertIs<dev.kmapx.core.plan.Construction.FactoryCall>(plan.construction)
        assertEquals("fx.Temperature.fromCents", factory.qualifiedFunction)
        assertEquals("fx.Temperature", factory.companionOf)
    }

    @Test
    fun `dos candidatos anotados producen KMX006`() {
        val target = dev.kmapx.core.model.MClass(
            type = mtype("fx.Money"),
            constructors = listOf(
                dev.kmapx.core.model.MConstructor(
                    params = listOf(param("cents", "kotlin.Long")),
                    annotatedMapConstructor = true,
                ),
            ),
            factories = listOf(
                dev.kmapx.core.model.MFactory("fx.fromCents", listOf(param("cents", "kotlin.Long"))),
            ),
        )
        val plan = engine.resolve(moneySource, target, ext("toMoney"))
        assertFalse(plan.valid)
        assertEquals(DiagnosticCode.KMX006, plan.diagnostics.single().code)
    }

    @Test
    fun `dos MapConstructor producen KMX006`() {
        val target = dev.kmapx.core.model.MClass(
            type = mtype("fx.Money"),
            constructors = listOf(
                dev.kmapx.core.model.MConstructor(
                    params = listOf(param("cents", "kotlin.Long")),
                    isPrimary = true,
                    annotatedMapConstructor = true,
                ),
                dev.kmapx.core.model.MConstructor(
                    params = listOf(param("cents", "kotlin.Long"), param("currency", "kotlin.String")),
                    isPrimary = false,
                    annotatedMapConstructor = true,
                ),
            ),
        )
        val plan = engine.resolve(moneySource, target, ext("toMoney"))
        assertFalse(plan.valid)
        assertEquals(DiagnosticCode.KMX006, plan.diagnostics.single().code)
    }

    @Test
    fun `val de cuerpo del target no requerido se ignora sin ruido`() {
        // `code` es val de cuerpo (no asignable); existe en el source pero NO en el mecanismo:
        class WithBodyVal(val id: String) { @Suppress("unused") val code: String = "X" }
        data class SrcWithCode(val id: String, val code: String)
        val plan = engine.resolve(mclassOf<SrcWithCode>(), mclassOf<WithBodyVal>(), ext("toTarget"))
        assertTrue(plan.valid, plan.diagnostics.joinToString { it.render() })
        val ctor = assertIs<dev.kmapx.core.plan.Construction.ConstructorCall>(plan.construction)
        assertTrue(ctor.postAssignments.isEmpty())
    }

    @Test
    fun `primario privado sin anotaciones produce KMX005 con fix`() {
        val target = dev.kmapx.core.model.MClass(
            type = mtype("fx.Money"),
            constructors = listOf(
                dev.kmapx.core.model.MConstructor(
                    params = listOf(param("cents", "kotlin.Long")),
                    isPrimary = true,
                    visible = false,
                ),
            ),
        )
        val plan = engine.resolve(moneySource, target, ext("toMoney"))
        assertFalse(plan.valid)
        val diag = plan.diagnostics.single()
        assertEquals(DiagnosticCode.KMX005, diag.code)
        assertTrue(diag.fix.contains("@MapFactory"), diag.fix)
    }

    @Test
    fun `var de cuerpo cubierta por el matching se asigna post-construccion`() {
        data class TaskSrc(val id: String, val status: String)
        val plan = engine.resolve(mclassOf<TaskSrc>(), mclassOf<RegularWithVar>(), ext("toTask"))
        assertTrue(plan.valid, plan.diagnostics.joinToString { it.render() })
        val ctor = assertIs<dev.kmapx.core.plan.Construction.ConstructorCall>(plan.construction)
        assertEquals(listOf("id"), ctor.arguments.map { it.paramName })
        val post = ctor.postAssignments.single()
        assertEquals("status", post.propertyName)
        assertIs<ValueSource.Direct>(post.value)
    }

    @Test
    fun `var de cuerpo sin fuente se ignora en silencio`() {
        data class OnlyId(val id: String)
        val plan = engine.resolve(mclassOf<OnlyId>(), mclassOf<RegularWithVar>(), ext("toTask"))
        assertTrue(plan.valid)
        val ctor = assertIs<dev.kmapx.core.plan.Construction.ConstructorCall>(plan.construction)
        assertTrue(ctor.postAssignments.isEmpty())
    }

    @Test
    fun `var post-construccion participa de la matriz de nulabilidad (KMX003)`() {
        data class NullableStatus(val id: String, val status: String?)
        val plan = engine.resolve(mclassOf<NullableStatus>(), mclassOf<RegularWithVar>(), ext("toTask"))
        assertFalse(plan.valid)
        val diag = plan.diagnostics.single()
        assertEquals(DiagnosticCode.KMX003, diag.code)
        assertEquals("status", diag.location.member)
    }

    // ── Estrategias T? -> T ────────────────────────────────────────────
    // Las anotaciones tienen retención SOURCE: los targets se construyen a mano.

    private fun strategyTarget(
        paramType: String,
        vararg strategies: dev.kmapx.core.model.MNullStrategy,
        kind: TypeKind = TypeKind.OTHER,
    ) = dev.kmapx.core.model.MClass(
        type = mtype("fx.NickDto"),
        constructors = listOf(
            dev.kmapx.core.model.MConstructor(
                params = listOf(
                    dev.kmapx.core.model.MConstructorParam(
                        name = "nickname",
                        type = dev.kmapx.core.model.MType(paramType, nullable = false, kind = kind),
                        strategies = strategies.toList(),
                    ),
                ),
            ),
        ),
    )

    private fun nullableSource(propType: String, kind: TypeKind = TypeKind.OTHER) =
        dev.kmapx.core.model.MClass(
            type = mtype("fx.NickSrc"),
            properties = listOf(
                dev.kmapx.core.model.MProperty(
                    "nickname",
                    dev.kmapx.core.model.MType(propType, nullable = true, kind = kind),
                ),
            ),
        )

    private fun singleValue(plan: dev.kmapx.core.plan.MappingPlan): ValueSource {
        assertTrue(plan.valid, plan.diagnostics.joinToString { it.render() })
        return assertIs<dev.kmapx.core.plan.Construction.ConstructorCall>(plan.construction)
            .arguments.single().value
    }

    @Test
    fun `WithDefault tipado - String, Int, Boolean y enum`() {
        val string = singleValue(
            engine.resolve(
                nullableSource("kotlin.String"),
                strategyTarget("kotlin.String", dev.kmapx.core.model.MNullStrategy.WithDefault("N/A")),
                ext("toDto"),
            ),
        )
        assertEquals("\"N/A\"", assertIs<ValueSource.NullFallbackToValue>(string).default)

        val int = singleValue(
            engine.resolve(
                nullableSource("kotlin.Int"),
                strategyTarget("kotlin.Int", dev.kmapx.core.model.MNullStrategy.WithDefault("42")),
                ext("toDto"),
            ),
        )
        assertEquals("42", assertIs<ValueSource.NullFallbackToValue>(int).default)

        val boolean = singleValue(
            engine.resolve(
                nullableSource("kotlin.Boolean"),
                strategyTarget("kotlin.Boolean", dev.kmapx.core.model.MNullStrategy.WithDefault("true")),
                ext("toDto"),
            ),
        )
        assertEquals("true", assertIs<ValueSource.NullFallbackToValue>(boolean).default)

        val enum = singleValue(
            engine.resolve(
                nullableSource("fx.Color", kind = TypeKind.ENUM),
                strategyTarget("fx.Color", dev.kmapx.core.model.MNullStrategy.WithDefault("RED"), kind = TypeKind.ENUM),
                ext("toDto"),
            ),
        )
        assertEquals("fx.Color.RED", assertIs<ValueSource.NullFallbackToValue>(enum).default)
    }

    @Test
    fun `OrThrow con mensaje que nombra campo y par de tipos`() {
        val value = singleValue(
            engine.resolve(
                nullableSource("kotlin.String"),
                strategyTarget("kotlin.String", dev.kmapx.core.model.MNullStrategy.OrThrow),
                ext("toDto"),
            ),
        )
        assertEquals("nickname must not be null mapping NickSrc -> NickDto", assertIs<ValueSource.NullOrThrow>(value).message)
    }

    @Test
    fun `AllowUnsafe genera NullUnsafe`() {
        val value = singleValue(
            engine.resolve(
                nullableSource("kotlin.String"),
                strategyTarget("kotlin.String", dev.kmapx.core.model.MNullStrategy.AllowUnsafe),
                ext("toDto"),
            ),
        )
        assertIs<ValueSource.NullUnsafe>(value)
    }

    @Test
    fun `WithDefault cubre toda la tabla del DoR - Char, Long y Double`() {
        val char = singleValue(
            engine.resolve(
                nullableSource("kotlin.Char"),
                strategyTarget("kotlin.Char", dev.kmapx.core.model.MNullStrategy.WithDefault("X")),
                ext("toDto"),
            ),
        )
        assertEquals("'X'", assertIs<ValueSource.NullFallbackToValue>(char).default)

        val long = singleValue(
            engine.resolve(
                nullableSource("kotlin.Long"),
                strategyTarget("kotlin.Long", dev.kmapx.core.model.MNullStrategy.WithDefault("42")),
                ext("toDto"),
            ),
        )
        assertEquals("42L", assertIs<ValueSource.NullFallbackToValue>(long).default)

        val double = singleValue(
            engine.resolve(
                nullableSource("kotlin.Double"),
                strategyTarget("kotlin.Double", dev.kmapx.core.model.MNullStrategy.WithDefault("1.5")),
                ext("toDto"),
            ),
        )
        assertEquals("1.5", assertIs<ValueSource.NullFallbackToValue>(double).default)
    }

    @Test
    fun `la estrategia SE APLICA en la post-asignacion de una var de cuerpo`() {
        // Target: class Task(val id: String) { @WithDefault("NEW") var status: String }
        val target = dev.kmapx.core.model.MClass(
            type = mtype("fx.Task"),
            properties = listOf(
                dev.kmapx.core.model.MProperty("id", mtype("kotlin.String")),
                dev.kmapx.core.model.MProperty(
                    "status", mtype("kotlin.String"),
                    mutable = true, inConstructor = false,
                    strategies = listOf(dev.kmapx.core.model.MNullStrategy.WithDefault("NEW")),
                ),
            ),
            constructors = listOf(
                dev.kmapx.core.model.MConstructor(
                    params = listOf(dev.kmapx.core.model.MConstructorParam("id", mtype("kotlin.String"))),
                ),
            ),
        )
        val source = dev.kmapx.core.model.MClass(
            type = mtype("fx.TaskSrc"),
            properties = listOf(
                dev.kmapx.core.model.MProperty("id", mtype("kotlin.String")),
                dev.kmapx.core.model.MProperty("status", mtype("kotlin.String").asNullable()),
            ),
        )
        val plan = engine.resolve(source, target, ext("toTask"))
        assertTrue(plan.valid, plan.diagnostics.joinToString { it.render() })
        val ctor = assertIs<dev.kmapx.core.plan.Construction.ConstructorCall>(plan.construction)
        val post = ctor.postAssignments.single()
        assertEquals("status", post.propertyName)
        assertEquals("\"NEW\"", assertIs<ValueSource.NullFallbackToValue>(post.value).default)
    }

    @Test
    fun `doble estrategia produce KMX016`() {
        val plan = engine.resolve(
            nullableSource("kotlin.String"),
            strategyTarget(
                "kotlin.String",
                dev.kmapx.core.model.MNullStrategy.WithDefault("x"),
                dev.kmapx.core.model.MNullStrategy.OrThrow,
            ),
            ext("toDto"),
        )
        assertFalse(plan.valid)
        assertEquals(DiagnosticCode.KMX016, plan.diagnostics.single().code)
    }

    @Test
    fun `default no parseable produce KMX017`() {
        val plan = engine.resolve(
            nullableSource("kotlin.Int"),
            strategyTarget("kotlin.Int", dev.kmapx.core.model.MNullStrategy.WithDefault("abc")),
            ext("toDto"),
        )
        assertFalse(plan.valid)
        val diag = plan.diagnostics.single()
        assertEquals(DiagnosticCode.KMX017, diag.code)
        assertTrue(diag.message.contains("\"abc\""), diag.message)
        assertTrue(diag.message.contains("Int"), diag.message)
    }

    @Test
    fun `WithDefault sobre tipo no soportado produce KMX017 con fix converter`() {
        val plan = engine.resolve(
            nullableSource("fx.Address"),
            strategyTarget("fx.Address", dev.kmapx.core.model.MNullStrategy.WithDefault("x")),
            ext("toDto"),
        )
        assertFalse(plan.valid)
        val diag = plan.diagnostics.single()
        assertEquals(DiagnosticCode.KMX017, diag.code)
        assertTrue(diag.fix.contains("@Converter"), diag.fix)
    }

    @Test
    fun `estrategia muerta produce KMX018 como warning y el plan sigue valido`() {
        val source = dev.kmapx.core.model.MClass(
            type = mtype("fx.NickSrc"),
            properties = listOf(
                dev.kmapx.core.model.MProperty("nickname", mtype("kotlin.String")), // NO nullable
            ),
        )
        val plan = engine.resolve(
            source,
            strategyTarget("kotlin.String", dev.kmapx.core.model.MNullStrategy.WithDefault("N/A")),
            ext("toDto"),
        )
        assertTrue(plan.valid, "un warning no invalida el plan")
        val diag = plan.diagnostics.single()
        assertEquals(DiagnosticCode.KMX018, diag.code)
        assertEquals(dev.kmapx.core.diagnostics.Severity.WARNING, diag.severity)
        assertIs<ValueSource.Direct>(singleValue(plan))
    }

    // ── Conversiones implícitas (lista cerrada) ────────────────────────

    private fun listOfType(element: dev.kmapx.core.model.MType) = dev.kmapx.core.model.MType(
        "kotlin.collections.List", nullable = false, kind = TypeKind.COLLECTION_LIST, typeArgs = listOf(element),
    )

    private fun setOfType(element: dev.kmapx.core.model.MType) = dev.kmapx.core.model.MType(
        "kotlin.collections.Set", nullable = false, kind = TypeKind.COLLECTION_SET, typeArgs = listOf(element),
    )

    private fun classOf(name: String, propType: dev.kmapx.core.model.MType) = dev.kmapx.core.model.MClass(
        type = mtype(name),
        properties = listOf(dev.kmapx.core.model.MProperty("items", propType)),
        constructors = listOf(
            dev.kmapx.core.model.MConstructor(params = listOf(dev.kmapx.core.model.MConstructorParam("items", propType))),
        ),
    )

    private fun srcOf(propType: dev.kmapx.core.model.MType) = dev.kmapx.core.model.MClass(
        type = mtype("fx.Src"),
        properties = listOf(dev.kmapx.core.model.MProperty("items", propType)),
    )

    private fun targetOf(propType: dev.kmapx.core.model.MType) = dev.kmapx.core.model.MClass(
        type = mtype("fx.Dto"),
        constructors = listOf(
            dev.kmapx.core.model.MConstructor(params = listOf(dev.kmapx.core.model.MConstructorParam("items", propType))),
        ),
    )

    private val addressType = dev.kmapx.core.model.MType("fx.Address", nullable = false, kind = TypeKind.DATA_CLASS)
    private val addressDtoType = dev.kmapx.core.model.MType("fx.AddressDto", nullable = false, kind = TypeKind.DATA_CLASS)
    private val declared = mapOf(("fx.Address" to "fx.AddressDto") to "fx.toAddressDto")

    @Test
    fun `lista identica con genericos es referencia directa, sin map`() {
        val stringType = mtype("kotlin.String")
        val plan = engine.resolve(srcOf(listOfType(stringType)), targetOf(listOfType(stringType)), ext("toDto"))
        assertIs<ValueSource.Direct>(singleValue(plan))
    }

    @Test
    fun `lista con ensanchamiento de nulabilidad del elemento es directa (covarianza)`() {
        val plan = engine.resolve(
            srcOf(listOfType(mtype("kotlin.String"))),
            targetOf(listOfType(mtype("kotlin.String").asNullable())),
            ext("toDto"),
        )
        assertIs<ValueSource.Direct>(singleValue(plan))
    }

    @Test
    fun `lista con mapper declarado del elemento produce un solo MapElements-ViaMapper`() {
        val plan = engine.resolve(
            srcOf(listOfType(addressType)),
            targetOf(listOfType(addressDtoType)),
            ext("toDto"),
            declaredMappings = declared,
        )
        val mapped = assertIs<ValueSource.MapElements>(singleValue(plan))
        assertEquals(TypeKind.COLLECTION_LIST, mapped.into)
        val viaMapper = assertIs<ValueSource.ViaMapper>(mapped.element)
        assertEquals("it", viaMapper.source.name)
    }

    @Test
    fun `Set a Set con mapper declarado materializa en set (una pasada)`() {
        val plan = engine.resolve(
            srcOf(setOfType(addressType)),
            targetOf(setOfType(addressDtoType)),
            ext("toDto"),
            declaredMappings = declared,
        )
        val mapped = assertIs<ValueSource.MapElements>(singleValue(plan))
        assertEquals(TypeKind.COLLECTION_SET, mapped.into)
    }

    @Test
    fun `Collection como target acepta lista directa o mapeada`() {
        val collectionOfAddressDto = dev.kmapx.core.model.MType(
            "kotlin.collections.Collection", nullable = false, kind = TypeKind.OTHER,
            typeArgs = listOf(addressDtoType),
        )
        val direct = engine.resolve(
            srcOf(
                dev.kmapx.core.model.MType(
                    "kotlin.collections.List", nullable = false, kind = TypeKind.COLLECTION_LIST,
                    typeArgs = listOf(addressDtoType),
                ),
            ),
            targetOf(collectionOfAddressDto),
            ext("toDto"),
        )
        assertIs<ValueSource.Direct>(singleValue(direct))

        val mapped = engine.resolve(
            srcOf(listOfType(addressType)),
            targetOf(collectionOfAddressDto),
            ext("toDto"),
            declaredMappings = declared,
        )
        assertIs<ValueSource.MapElements>(singleValue(mapped))
    }

    @Test
    fun `lista de nullables a lista de no-nullables produce KMX003 en el elemento`() {
        val plan = engine.resolve(
            srcOf(listOfType(mtype("kotlin.String").asNullable())),
            targetOf(listOfType(mtype("kotlin.String"))),
            ext("toDto"),
        )
        assertFalse(plan.valid)
        assertEquals(DiagnosticCode.KMX003, plan.diagnostics.single().code)
    }

    @Test
    fun `estrategia del parametro aplica dentro de la coleccion`() {
        val src = srcOf(listOfType(mtype("kotlin.String").asNullable()))
        val target = dev.kmapx.core.model.MClass(
            type = mtype("fx.Dto"),
            constructors = listOf(
                dev.kmapx.core.model.MConstructor(
                    params = listOf(
                        dev.kmapx.core.model.MConstructorParam(
                            "items", listOfType(mtype("kotlin.String")),
                            strategies = listOf(dev.kmapx.core.model.MNullStrategy.WithDefault("N/A")),
                        ),
                    ),
                ),
            ),
        )
        val plan = engine.resolve(src, target, ext("toDto"))
        val mapped = assertIs<ValueSource.MapElements>(singleValue(plan))
        val fallback = assertIs<ValueSource.NullFallbackToValue>(mapped.element)
        assertEquals("it", fallback.source.name)
        assertEquals("\"N/A\"", fallback.default)
    }

    @Test
    fun `String a Int NO es implicito - KMX004 (la lista sigue cerrada)`() {
        // Int→Long pasó a ser widening; el principio de lista cerrada se
        // conserva: un par fuera de las tablas sigue siendo KMX004.
        val plan = engine.resolve(
            srcOf(mtype("kotlin.String")),
            targetOf(mtype("kotlin.Int")),
            ext("toDto"),
        )
        assertFalse(plan.valid)
        assertEquals(DiagnosticCode.KMX004, plan.diagnostics.single().code)
    }

    @Test
    fun `List a Set NO es implicito - KMX004 (Q1, el dedup pierde informacion)`() {
        val stringType = mtype("kotlin.String")
        val plan = engine.resolve(
            srcOf(listOfType(stringType)),
            targetOf(setOfType(stringType)),
            ext("toDto"),
        )
        assertFalse(plan.valid)
        assertEquals(DiagnosticCode.KMX004, plan.diagnostics.single().code)
    }

    @Test
    fun `List de UserId a List de String es un solo map con unwrap`() {
        val userIdType = dev.kmapx.core.model.MType(
            "fx.UserId", nullable = false, kind = TypeKind.VALUE_CLASS,
            underlying = mtype("kotlin.String"),
        )
        val plan = engine.resolve(
            srcOf(listOfType(userIdType)),
            targetOf(listOfType(mtype("kotlin.String"))),
            ext("toDto"),
        )
        val mapped = assertIs<ValueSource.MapElements>(singleValue(plan))
        assertIs<ValueSource.UnwrapValueClass>(mapped.element)
    }

    @Test
    fun `anidamiento List de List con una lambda por nivel`() {
        val plan = engine.resolve(
            srcOf(listOfType(listOfType(addressType))),
            targetOf(listOfType(listOfType(addressDtoType))),
            ext("toDto"),
            declaredMappings = declared,
        )
        val outer = assertIs<ValueSource.MapElements>(singleValue(plan))
        val inner = assertIs<ValueSource.MapElements>(outer.element)
        assertEquals("it", inner.source.name)
        assertIs<ValueSource.ViaMapper>(inner.element)
    }

    // ── Converters ─────────────────────────────────────────────────────

    private val instantType = mtype("java.time.Instant")
    private val isoConverter = mapOf(("java.time.Instant" to "kotlin.String") to listOf("fx.instantToIso"))

    @Test
    fun `converter aplicado, referenciado por FQN (contrato refactor-safe)`() {
        val plan = engine.resolve(
            srcOf(instantType), targetOf(mtype("kotlin.String")), ext("toDto"),
            converters = isoConverter,
        )
        val via = assertIs<ValueSource.ViaConverter>(singleValue(plan))
        assertEquals("fx.instantToIso", via.converter.qualifiedFunction)
        assertEquals(dev.kmapx.core.plan.Resolution.USER_CONVERTER, via.origin)
        assertFalse(via.safeCall)
    }

    @Test
    fun `el converter gana sobre el mapper declarado`() {
        val plan = engine.resolve(
            srcOf(addressType), targetOf(addressDtoType), ext("toDto"),
            declaredMappings = declared,
            converters = mapOf(("fx.Address" to "fx.AddressDto") to listOf("fx.addressToDto")),
        )
        assertIs<ValueSource.ViaConverter>(singleValue(plan))
    }

    @Test
    fun `el converter gana sobre la conversion implicita identica`() {
        val plan = engine.resolve(
            srcOf(mtype("kotlin.String")), targetOf(mtype("kotlin.String")), ext("toDto"),
            converters = mapOf(("kotlin.String" to "kotlin.String") to listOf("fx.normalize")),
        )
        assertIs<ValueSource.ViaConverter>(singleValue(plan))
    }

    @Test
    fun `dos converters del mismo par producen KMX009 con ambos nombres`() {
        val plan = engine.resolve(
            srcOf(instantType), targetOf(mtype("kotlin.String")), ext("toDto"),
            converters = mapOf(
                ("java.time.Instant" to "kotlin.String") to listOf("fx.instantToIso", "fx.instantToEpoch"),
            ),
        )
        assertFalse(plan.valid)
        val diag = plan.diagnostics.single()
        assertEquals(DiagnosticCode.KMX009, diag.code)
        assertTrue(diag.message.contains("instantToIso, instantToEpoch"), diag.message)
    }

    @Test
    fun `A nullable a B nullable se envuelve con safeCall`() {
        val plan = engine.resolve(
            srcOf(instantType.asNullable()), targetOf(mtype("kotlin.String").asNullable()), ext("toDto"),
            converters = isoConverter,
        )
        val via = assertIs<ValueSource.ViaConverter>(singleValue(plan))
        assertTrue(via.safeCall)
    }

    @Test
    fun `A nullable a B no-nullable sigue exigiendo estrategia de nulabilidad (KMX003)`() {
        val plan = engine.resolve(
            srcOf(instantType.asNullable()), targetOf(mtype("kotlin.String")), ext("toDto"),
            converters = isoConverter,
        )
        assertFalse(plan.valid)
        assertEquals(DiagnosticCode.KMX003, plan.diagnostics.single().code)
    }

    @Test
    fun `elemento de coleccion via converter`() {
        val plan = engine.resolve(
            srcOf(listOfType(instantType)), targetOf(listOfType(mtype("kotlin.String"))), ext("toDto"),
            converters = isoConverter,
        )
        val mapped = assertIs<ValueSource.MapElements>(singleValue(plan))
        val via = assertIs<ValueSource.ViaConverter>(mapped.element)
        assertEquals("it", via.source.name)
    }

    // ── Converters calificados @UseConverter (paso 0) ─────────

    private val localDate = mtype("java.time.LocalDate")
    private fun qConv(obj: String, from: dev.kmapx.core.model.MType?, to: dev.kmapx.core.model.MType?) =
        dev.kmapx.core.model.MQualifiedConverter(obj, from, to)

    private fun targetWithConverter(propType: dev.kmapx.core.model.MType, uc: dev.kmapx.core.model.MQualifiedConverter) =
        dev.kmapx.core.model.MClass(
            type = mtype("fx.Dto"),
            constructors = listOf(
                dev.kmapx.core.model.MConstructor(
                    params = listOf(dev.kmapx.core.model.MConstructorParam("items", propType, useConverter = uc)),
                ),
            ),
        )

    @Test
    fun `@UseConverter escalar emite ViaQualifiedConverter por el object`() {
        val plan = engine.resolve(
            srcOf(localDate),
            targetWithConverter(mtype("kotlin.String"), qConv("fx.ShortDate", localDate, mtype("kotlin.String"))),
            ext("toDto"),
        )
        val via = assertIs<ValueSource.ViaQualifiedConverter>(singleValue(plan))
        assertEquals("fx.ShortDate", via.converterObject)
        assertFalse(via.safeCall)
    }

    @Test
    fun `A nullable a B nullable compone con safeCall`() {
        val plan = engine.resolve(
            srcOf(localDate.asNullable()),
            targetWithConverter(
                mtype("kotlin.String").asNullable(),
                qConv("fx.ShortDate", localDate, mtype("kotlin.String")),
            ),
            ext("toDto"),
        )
        assertTrue(assertIs<ValueSource.ViaQualifiedConverter>(singleValue(plan)).safeCall)
    }

    @Test
    fun `@UseConverter gana sobre el @Converter global y el mapper declarado`() {
        val plan = engine.resolve(
            srcOf(localDate),
            targetWithConverter(mtype("kotlin.String"), qConv("fx.ShortDate", localDate, mtype("kotlin.String"))),
            ext("toDto"),
            declaredMappings = mapOf(("java.time.LocalDate" to "kotlin.String") to "fx.dateToString"),
            converters = mapOf(("java.time.LocalDate" to "kotlin.String") to listOf("fx.globalDate")),
        )
        assertIs<ValueSource.ViaQualifiedConverter>(singleValue(plan))
    }

    @Test
    fun `tipos no coinciden produce KMX027 con esperado vs declarado`() {
        val plan = engine.resolve(
            srcOf(mtype("kotlin.Int")),
            targetWithConverter(mtype("kotlin.String"), qConv("fx.ShortDate", localDate, mtype("kotlin.String"))),
            ext("toDto"),
        )
        assertFalse(plan.valid)
        val diag = plan.diagnostics.single()
        assertEquals(DiagnosticCode.KMX027, diag.code)
        assertTrue(diag.message.contains("LocalDate -> String"), diag.message)
        assertTrue(diag.message.contains("Int -> String"), diag.message)
    }

    @Test
    fun `object que no implementa Converts produce KMX029`() {
        val plan = engine.resolve(
            srcOf(localDate),
            targetWithConverter(mtype("kotlin.String"), qConv("fx.NotAConverter", null, null)),
            ext("toDto"),
        )
        assertFalse(plan.valid)
        assertEquals(DiagnosticCode.KMX029, plan.diagnostics.single().code)
    }

    @Test
    fun `converter innecesario (A igual B) es warning pero resuelve`() {
        val plan = engine.resolve(
            srcOf(mtype("kotlin.String")),
            targetWithConverter(mtype("kotlin.String"), qConv("fx.Trim", mtype("kotlin.String"), mtype("kotlin.String"))),
            ext("toDto"),
        )
        assertTrue(plan.valid) // KMX031 es WARNING
        assertEquals(DiagnosticCode.KMX031, plan.diagnostics.single().code)
        assertIs<ValueSource.ViaQualifiedConverter>(singleValue(plan))
    }

    @Test
    fun `elemento de coleccion via converter calificado`() {
        val plan = engine.resolve(
            srcOf(listOfType(localDate)),
            targetWithConverter(
                listOfType(mtype("kotlin.String")),
                qConv("fx.ShortDate", localDate, mtype("kotlin.String")),
            ),
            ext("toDto"),
        )
        val mapped = assertIs<ValueSource.MapElements>(singleValue(plan))
        val via = assertIs<ValueSource.ViaQualifiedConverter>(mapped.element)
        assertEquals("it", via.source.name)
        assertEquals("fx.ShortDate", via.converterObject)
    }

    // ── Renombrado plano @MapFrom(from = "...") ───────────────────────────
    // La anotación tiene retención SOURCE: los targets se construyen a mano.

    private fun renamedTarget(paramName: String, from: String?, type: dev.kmapx.core.model.MType = mtype("kotlin.String")) =
        dev.kmapx.core.model.MClass(
            type = mtype("fx.Dto"),
            constructors = listOf(
                dev.kmapx.core.model.MConstructor(
                    params = listOf(
                        dev.kmapx.core.model.MConstructorParam(paramName, type, mappedFrom = from),
                    ),
                ),
            ),
        )

    private val firstnameSource = dev.kmapx.core.model.MClass(
        type = mtype("fx.Src"),
        properties = listOf(dev.kmapx.core.model.MProperty("firstname", mtype("kotlin.String"))),
    )

    @Test
    fun `renombre basico redirige el matching`() {
        val plan = engine.resolve(firstnameSource, renamedTarget("name", from = "firstname"), ext("toDto"))
        val direct = assertIs<ValueSource.Direct>(singleValue(plan))
        assertEquals("firstname", direct.source.name)
    }

    @Test
    fun `tras el renombre aplican las demas reglas - nulabilidad KMX003`() {
        val source = dev.kmapx.core.model.MClass(
            type = mtype("fx.Src"),
            properties = listOf(
                dev.kmapx.core.model.MProperty("firstname", mtype("kotlin.String").asNullable()),
            ),
        )
        val plan = engine.resolve(source, renamedTarget("name", from = "firstname"), ext("toDto"))
        assertFalse(plan.valid)
        assertEquals(DiagnosticCode.KMX003, plan.diagnostics.single().code)
    }

    @Test
    fun `renombre + converter`() {
        val source = dev.kmapx.core.model.MClass(
            type = mtype("fx.Src"),
            properties = listOf(dev.kmapx.core.model.MProperty("created", instantType)),
        )
        val plan = engine.resolve(
            source, renamedTarget("createdAt", from = "created"), ext("toDto"),
            converters = isoConverter,
        )
        val via = assertIs<ValueSource.ViaConverter>(singleValue(plan))
        assertEquals("created", via.source.name)
    }

    @Test
    fun `from inexistente produce KMX011 con did-you-mean`() {
        val plan = engine.resolve(firstnameSource, renamedTarget("name", from = "firstnme"), ext("toDto"))
        assertFalse(plan.valid)
        val diag = plan.diagnostics.single()
        assertEquals(DiagnosticCode.KMX011, diag.code)
        assertTrue(diag.message.contains("Did you mean 'firstname'?"), diag.message)
    }

    @Test
    fun `sintaxis malformada produce KMX020 y ruta sin pre-resolucion KMX011`() {
        val malformed = engine.resolve(firstnameSource, renamedTarget("city", from = "address..city"), ext("toDto"))
        assertFalse(malformed.valid)
        assertEquals(DiagnosticCode.KMX020, malformed.diagnostics.single().code)

        // Ruta bien formada pero sin pre-resolución del frontend → inexistente desde el root:
        val missing = engine.resolve(firstnameSource, renamedTarget("city", from = "address.city"), ext("toDto"))
        assertFalse(missing.valid)
        assertEquals(DiagnosticCode.KMX011, missing.diagnostics.single().code)
    }

    @Test
    fun `ruta resuelta compone la expresion con el interrogante donde toca`() {
        val paths = mapOf(
            "address.city" to dev.kmapx.core.model.MPath.Resolved(
                segments = listOf(
                    dev.kmapx.core.model.MPathSegment("address", nullable = true),
                    dev.kmapx.core.model.MPathSegment("city", nullable = false),
                ),
                finalType = mtype("kotlin.String"),
            ),
        )
        // Target nullable: silencioso, expresión `address?.city`:
        val ok = engine.resolve(
            firstnameSource,
            renamedTarget("city", from = "address.city", type = mtype("kotlin.String").asNullable()),
            ext("toDto"), resolvedPaths = paths,
        )
        assertTrue(ok.valid, ok.diagnostics.joinToString { it.render() })
        assertEquals("address?.city", assertIs<ValueSource.Direct>(singleValue(ok)).source.name)

        // Target no-nullable: KMX003 nombrando el segmento culpable; con estrategia resuelve:
        val bad = engine.resolve(
            firstnameSource, renamedTarget("city", from = "address.city"),
            ext("toDto"), resolvedPaths = paths,
        )
        assertFalse(bad.valid)
        val diag = bad.diagnostics.single()
        assertEquals(DiagnosticCode.KMX003, diag.code)
        assertTrue(diag.message.contains("segment 'address' is nullable"), diag.message)

        val strategyTargetClass = dev.kmapx.core.model.MClass(
            type = mtype("fx.Dto"),
            constructors = listOf(
                dev.kmapx.core.model.MConstructor(
                    params = listOf(
                        dev.kmapx.core.model.MConstructorParam(
                            "city", mtype("kotlin.String"), mappedFrom = "address.city",
                            strategies = listOf(dev.kmapx.core.model.MNullStrategy.WithDefault("N/A")),
                        ),
                    ),
                ),
            ),
        )
        val conEstrategia = engine.resolve(firstnameSource, strategyTargetClass, ext("toDto"), resolvedPaths = paths)
        assertTrue(conEstrategia.valid, conEstrategia.diagnostics.joinToString { it.render() })
        val fb = assertIs<ValueSource.NullFallbackToValue>(singleValue(conEstrategia))
        assertEquals("address?.city", fb.source.name)
    }

    @Test
    fun `segmento inexistente produce KMX011 con did-you-mean del tipo del segmento`() {
        val paths = mapOf(
            "address.cty" to dev.kmapx.core.model.MPath.Missing(
                failedSegment = "cty", ownerSimpleName = "Address", candidates = listOf("city", "zip"),
            ),
        )
        val plan = engine.resolve(
            firstnameSource, renamedTarget("city", from = "address.cty"),
            ext("toDto"), resolvedPaths = paths,
        )
        assertFalse(plan.valid)
        val diag = plan.diagnostics.single()
        assertEquals(DiagnosticCode.KMX011, diag.code)
        assertTrue(diag.message.contains("'cty' does not exist on Address"), diag.message)
        assertTrue(diag.message.contains("Did you mean 'city'?"), diag.message)
    }

    @Test
    fun `fan-out con dos params desde el mismo from`() {
        val target = dev.kmapx.core.model.MClass(
            type = mtype("fx.Dto"),
            constructors = listOf(
                dev.kmapx.core.model.MConstructor(
                    params = listOf(
                        dev.kmapx.core.model.MConstructorParam("display", mtype("kotlin.String"), mappedFrom = "firstname"),
                        dev.kmapx.core.model.MConstructorParam("sortKey", mtype("kotlin.String"), mappedFrom = "firstname"),
                    ),
                ),
            ),
        )
        val plan = engine.resolve(firstnameSource, target, ext("toDto"))
        assertTrue(plan.valid, plan.diagnostics.joinToString { it.render() })
        val ctor = assertIs<dev.kmapx.core.plan.Construction.ConstructorCall>(plan.construction)
        assertEquals(listOf("display", "sortKey"), ctor.arguments.map { it.paramName })
        ctor.arguments.forEach { assertEquals("firstname", assertIs<ValueSource.Direct>(it.value).source.name) }
    }

    @Test
    fun `el renombre pisa el match homonimo sin warning`() {
        val source = dev.kmapx.core.model.MClass(
            type = mtype("fx.Src"),
            properties = listOf(
                dev.kmapx.core.model.MProperty("name", mtype("kotlin.String")),
                dev.kmapx.core.model.MProperty("firstname", mtype("kotlin.String")),
            ),
        )
        val plan = engine.resolve(source, renamedTarget("name", from = "firstname"), ext("toDto"))
        assertTrue(plan.valid)
        assertTrue(plan.diagnostics.isEmpty(), "explícito sobre implícito, sin ruido")
        assertEquals("firstname", assertIs<ValueSource.Direct>(singleValue(plan)).source.name)
    }

    // ── Politica TARGET_DEFAULT ──────────────────────

    private fun defaultedTarget(vararg params: Pair<String, Boolean>) = dev.kmapx.core.model.MClass(
        type = mtype("fx.Dto"),
        constructors = listOf(
            dev.kmapx.core.model.MConstructor(
                params = params.map { (name, hasDefault) ->
                    dev.kmapx.core.model.MConstructorParam(name, mtype("kotlin.String"), hasDefault = hasDefault)
                },
            ),
        ),
    )

    private fun nullableStringSource(vararg names: String) = dev.kmapx.core.model.MClass(
        type = mtype("fx.Src"),
        properties = names.map {
            dev.kmapx.core.model.MProperty(it, mtype("kotlin.String").asNullable())
        },
    )

    @Test
    fun `con opt-in el default del target cierra la violacion de nulabilidad`() {
        val plan = engine.resolve(
            nullableStringSource("nickname"),
            defaultedTarget("nickname" to true),
            ext("toDto"),
            nullPolicies = listOf(NullPolicy.TARGET_DEFAULT),
        )
        assertIs<ValueSource.NullFallbackToDefault>(singleValue(plan))
    }

    @Test
    fun `sin opt-in sigue siendo KMX003 y el fix menciona TARGET_DEFAULT`() {
        val plan = engine.resolve(
            nullableStringSource("nickname"),
            defaultedTarget("nickname" to true),
            ext("toDto"),
        )
        assertFalse(plan.valid)
        val diag = plan.diagnostics.single()
        assertEquals(DiagnosticCode.KMX003, diag.code)
        assertTrue(diag.fix.contains("set onNull = TARGET_DEFAULT"), diag.fix)
    }

    @Test
    fun `fuente ausente con default produce KMX021 warning y omision`() {
        val plan = engine.resolve(
            nullableStringSource("name"),
            defaultedTarget("name" to false, "tags" to true),
            ext("toDto"),
        )
        // "name" es String? -> String sin estrategia: aparte. Usamos fuente no-nullable:
        val cleanPlan = engine.resolve(
            dev.kmapx.core.model.MClass(
                type = mtype("fx.Src"),
                properties = listOf(dev.kmapx.core.model.MProperty("name", mtype("kotlin.String"))),
            ),
            defaultedTarget("name" to false, "tags" to true),
            ext("toDto"),
        )
        assertTrue(cleanPlan.valid, cleanPlan.diagnostics.joinToString { it.render() })
        val diag = cleanPlan.diagnostics.single()
        assertEquals(DiagnosticCode.KMX021, diag.code)
        assertEquals(dev.kmapx.core.diagnostics.Severity.WARNING, diag.severity)
        assertEquals("tags", diag.location.member)
        val ctor = assertIs<dev.kmapx.core.plan.Construction.ConstructorCall>(cleanPlan.construction)
        assertEquals(listOf("name"), ctor.arguments.map { it.paramName })
        assertFalse(plan.valid) // el caso sucio sí falla por KMX003, no en silencio
    }

    @Test
    fun `tres campos omisibles superan K=2 y producen KMX022`() {
        val plan = engine.resolve(
            nullableStringSource("a", "b", "c"),
            defaultedTarget("a" to true, "b" to true, "c" to true),
            ext("toDto"),
            nullPolicies = listOf(NullPolicy.TARGET_DEFAULT),
        )
        assertFalse(plan.valid)
        val diag = plan.diagnostics.single()
        assertEquals(DiagnosticCode.KMX022, diag.code)
        assertTrue(diag.message.contains("a, b, c"), diag.message)
        assertTrue(diag.message.contains("limit is 2"), diag.message)
    }

    @Test
    fun `aplica igual con MapFactory (defaults de la factory)`() {
        val target = dev.kmapx.core.model.MClass(
            type = mtype("fx.Dto"),
            factories = listOf(
                dev.kmapx.core.model.MFactory(
                    qualifiedName = "fx.Dto.of",
                    params = listOf(
                        dev.kmapx.core.model.MConstructorParam("nickname", mtype("kotlin.String"), hasDefault = true),
                    ),
                    companionOf = "fx.Dto",
                ),
            ),
        )
        val plan = engine.resolve(
            nullableStringSource("nickname"), target, ext("toDto"),
            nullPolicies = listOf(NullPolicy.TARGET_DEFAULT),
        )
        assertTrue(plan.valid, plan.diagnostics.joinToString { it.render() })
        val factory = assertIs<dev.kmapx.core.plan.Construction.FactoryCall>(plan.construction)
        assertIs<ValueSource.NullFallbackToDefault>(factory.arguments.single().value)
    }

    // ── Jerarquías sealed paralelas ────────────────────────────────────

    @Test
    fun `jerarquias paralelas producen SealedDispatch con sub-plan y referencia object`() {
        val plan = engine.resolve(mclassOf<Payment>(), mclassOf<PaymentDto>(), ext("toPaymentDto"))
        assertTrue(plan.valid, plan.diagnostics.joinToString { it.render() })
        val dispatch = assertIs<dev.kmapx.core.plan.Construction.SealedDispatch>(plan.construction)
        assertEquals(2, dispatch.branches.size)

        val approved = dispatch.branches.first { it.sourceSubtype.endsWith("Approved") }
        assertIs<dev.kmapx.core.plan.Construction.ConstructorCall>(approved.plan.construction)
        assertEquals("toApproved", (approved.plan.emission as Emission.ExtensionFunction).name)

        val pending = dispatch.branches.first { it.sourceSubtype.endsWith("Pending") }
        val objectRef = assertIs<dev.kmapx.core.plan.Construction.ObjectReference>(pending.plan.construction)
        assertTrue(objectRef.qualifiedName.endsWith("PaymentDto.Pending"), objectRef.qualifiedName)
    }

    @Test
    fun `subtipo del source sin par produce KMX010`() {
        val plan = engine.resolve(mclassOf<RefundedPayment>(), mclassOf<PaymentDto>(), ext("toDto"))
        assertFalse(plan.valid)
        val diag = plan.diagnostics.single { it.code == DiagnosticCode.KMX010 }
        assertTrue(diag.location.qualifiedClassName.endsWith("Refunded"), diag.location.qualifiedClassName)
        assertTrue(diag.fix.contains("@MapSubtype"), diag.fix)
    }

    @Test
    fun `subtipo extra del target produce warning KMX023 y el plan sigue valido`() {
        val plan = engine.resolve(mclassOf<Payment>(), mclassOf<PaymentDtoExtra>(), ext("toDto"))
        assertTrue(plan.valid, "un warning no invalida el dispatch")
        val diag = plan.diagnostics.single()
        assertEquals(DiagnosticCode.KMX023, diag.code)
        assertEquals(dev.kmapx.core.diagnostics.Severity.WARNING, diag.severity)
        assertTrue(diag.location.qualifiedClassName.endsWith("Cancelled"), diag.location.qualifiedClassName)
    }

    @Test
    fun `anidamiento sealed profundo produce KMX024 (un nivel en v1)`() {
        val plan = engine.resolve(mclassOf<DeepEvent>(), mclassOf<PaymentDto>(), ext("toDto"))
        assertFalse(plan.valid)
        assertTrue(plan.diagnostics.any { it.code == DiagnosticCode.KMX024 })
    }

    @Test
    fun `MapSubtype redirige el emparejamiento (modelo a mano - retencion SOURCE)`() {
        val sourceSub = dev.kmapx.core.model.MClass(
            type = dev.kmapx.core.model.MType("fx.Event.Approved", false, TypeKind.OBJECT, packageName = "fx"),
            subtypeTargetOverride = "fx.EventDto.Accepted",
        )
        val source = dev.kmapx.core.model.MClass(
            type = dev.kmapx.core.model.MType("fx.Event", false, TypeKind.SEALED_INTERFACE, packageName = "fx"),
            sealedSubtypes = listOf(sourceSub),
        )
        val target = dev.kmapx.core.model.MClass(
            type = dev.kmapx.core.model.MType("fx.EventDto", false, TypeKind.SEALED_INTERFACE, packageName = "fx"),
            sealedSubtypes = listOf(
                dev.kmapx.core.model.MClass(
                    type = dev.kmapx.core.model.MType("fx.EventDto.Accepted", false, TypeKind.OBJECT, packageName = "fx"),
                ),
                dev.kmapx.core.model.MClass(
                    type = dev.kmapx.core.model.MType("fx.EventDto.Approved", false, TypeKind.OBJECT, packageName = "fx"),
                ),
            ),
        )
        val plan = engine.resolve(source, target, ext("toEventDto"))
        val dispatch = assertIs<dev.kmapx.core.plan.Construction.SealedDispatch>(plan.construction)
        val branch = dispatch.branches.single()
        val objectRef = assertIs<dev.kmapx.core.plan.Construction.ObjectReference>(branch.plan.construction)
        assertEquals("fx.EventDto.Accepted", objectRef.qualifiedName)
        // El homónimo Approved quedó sin par → KMX023 (explícito gana, y lo no emparejado se reporta):
        assertTrue(plan.diagnostics.any { it.code == DiagnosticCode.KMX023 })
    }

    @Test
    fun `las reglas normales aplican dentro de cada rama (KMX003 propagado)`() {
        val plan = engine.resolve(mclassOf<StrictSrcEvent>(), mclassOf<StrictDtoEvent>(), ext("toDto"))
        assertFalse(plan.valid)
        assertTrue(plan.diagnostics.any { it.code == DiagnosticCode.KMX003 })
    }

    // ── El plan es puro dato ──────────────────────────────────────────

    @Test
    fun `modelo - el MappingPlan completo es construible a mano y comparable con assertEquals`() {
        val actual = engine.resolve(mclassOf<Person>(), mclassOf<PersonDto>(), ext("toPersonDto"))
        val expected = dev.kmapx.core.plan.MappingPlan(
            source = mclassOf<Person>().type,
            target = mclassOf<PersonDto>().type,
            emission = ext("toPersonDto"),
            construction = dev.kmapx.core.plan.Construction.ConstructorCall(
                arguments = listOf(
                    dev.kmapx.core.plan.Argument("name", ValueSource.Direct(dev.kmapx.core.plan.ref("name"))),
                    dev.kmapx.core.plan.Argument("age", ValueSource.Direct(dev.kmapx.core.plan.ref("age"))),
                ),
            ),
        )
        assertEquals(expected, actual)
    }

    // ── PATCH vía copy() ───────────────────────────────────────────────

    @Test
    fun `patch con dos nullables produce fields con fallback (null = no tocar)`() {
        data class Customer(val name: String, val nickname: String)
        data class CustomerPatch(val name: String?, val nickname: String?)
        val resolution = engine.resolvePatch(mclassOf<Customer>(), mclassOf<CustomerPatch>())
        assertTrue(resolution.diagnostics.isEmpty(), resolution.diagnostics.joinToString { it.render() })
        assertEquals(listOf("name", "nickname"), resolution.fields.map { it.name })
        resolution.fields.forEach {
            assertTrue(it.fallbackToTarget)
            assertIs<ValueSource.Direct>(it.value)
        }
    }

    @Test
    fun `target que no es data class produce KMX012`() {
        class Regular(val name: String)
        data class Patch(val name: String?)
        val resolution = engine.resolvePatch(mclassOf<Regular>(), mclassOf<Patch>())
        assertEquals(DiagnosticCode.KMX012, resolution.diagnostics.single().code)
        assertTrue(resolution.fields.isEmpty())
    }

    @Test
    fun `campo no-nullable del patch se asigna incondicionalmente`() {
        data class Customer(val name: String)
        data class Patch(val name: String)
        val resolution = engine.resolvePatch(mclassOf<Customer>(), mclassOf<Patch>())
        assertFalse(resolution.fields.single().fallbackToTarget)
    }

    @Test
    fun `campo del patch sin par en el target produce KMX002 con did-you-mean`() {
        data class Customer(val email: String)
        data class Patch(val emial: String?)
        val resolution = engine.resolvePatch(mclassOf<Customer>(), mclassOf<Patch>())
        val diag = resolution.diagnostics.single()
        assertEquals(DiagnosticCode.KMX002, diag.code)
        assertTrue(diag.message.contains("Did you mean 'email'?"), diag.message)
    }

    @Test
    fun `wrap de value class dentro del fallback`() {
        data class Holder(val id: UserId)
        data class Patch(val id: String?)
        val resolution = engine.resolvePatch(mclassOf<Holder>(), mclassOf<Patch>())
        val field = resolution.fields.single()
        assertTrue(field.fallbackToTarget)
        assertTrue(assertIs<ValueSource.WrapValueClass>(field.value).safeCall)
    }

    @Test
    fun `converter dentro del fallback`() {
        data class Holder(val at: String)
        val patch = dev.kmapx.core.model.MClass(
            type = mtype("fx.Patch"),
            properties = listOf(dev.kmapx.core.model.MProperty("at", instantType.asNullable())),
        )
        val resolution = engine.resolvePatch(mclassOf<Holder>(), patch, converters = isoConverter)
        val field = resolution.fields.single()
        assertTrue(field.fallbackToTarget)
        assertTrue(assertIs<ValueSource.ViaConverter>(field.value).safeCall)
    }

    // ── Enums paralelos ────────────────────────────────────────────────

    @Test
    fun `enums paralelos producen EnumDispatch con rama por entry`() {
        val plan = engine.resolve(mclassOf<Color>(), mclassOf<ColorDto>(), ext("toColorDto"))
        assertTrue(plan.valid, plan.diagnostics.joinToString { it.render() })
        val dispatch = assertIs<dev.kmapx.core.plan.Construction.EnumDispatch>(plan.construction)
        assertEquals(
            listOf("RED" to "RED", "GREEN" to "GREEN"),
            dispatch.entries.map { it.sourceEntry to it.targetEntry },
        )
    }

    @Test
    fun `entry del source sin par produce KMX026`() {
        val plan = engine.resolve(mclassOf<ColorExtra>(), mclassOf<ColorDto>(), ext("toColorDto"))
        assertFalse(plan.valid)
        val diag = plan.diagnostics.single { it.code == DiagnosticCode.KMX026 }
        assertTrue(diag.location.qualifiedClassName.endsWith("BLUE"), diag.location.qualifiedClassName)
        assertTrue(diag.fix.contains("@MapEntry"), diag.fix)
    }

    @Test
    fun `entry extra del target produce warning KMX023 y compila`() {
        val plan = engine.resolve(mclassOf<Color>(), mclassOf<ColorDtoExtra>(), ext("toColorDto"))
        assertTrue(plan.valid)
        val diag = plan.diagnostics.single()
        assertEquals(DiagnosticCode.KMX023, diag.code)
        assertEquals(dev.kmapx.core.diagnostics.Severity.WARNING, diag.severity)
    }

    @Test
    fun `MapEntry redirige y con destino inexistente da KMX026 con did-you-mean`() {
        // Overrides son SOURCE: modelo a mano.
        fun enumClass(name: String, vararg entries: dev.kmapx.core.model.MEnumEntry) =
            dev.kmapx.core.model.MClass(
                type = dev.kmapx.core.model.MType(name, false, TypeKind.ENUM),
                enumEntries = entries.toList(),
            )
        val target = enumClass(
            "fx.ColorDto",
            dev.kmapx.core.model.MEnumEntry("CRIMSON"),
            dev.kmapx.core.model.MEnumEntry("RED"),
        )
        val ok = engine.resolve(
            enumClass("fx.Color", dev.kmapx.core.model.MEnumEntry("RED", targetOverride = "CRIMSON")),
            target, ext("toColorDto"),
        )
        val dispatch = assertIs<dev.kmapx.core.plan.Construction.EnumDispatch>(ok.construction)
        assertEquals("CRIMSON", dispatch.entries.single().targetEntry)
        // El homónimo RED del target quedó sin par → KMX023:
        assertTrue(ok.diagnostics.single().code == DiagnosticCode.KMX023)

        val bad = engine.resolve(
            enumClass("fx.Color", dev.kmapx.core.model.MEnumEntry("RED", targetOverride = "CRIMSN")),
            target, ext("toColorDto"),
        )
        assertFalse(bad.valid)
        val diag = bad.diagnostics.single { it.code == DiagnosticCode.KMX026 }
        assertTrue(diag.message.contains("Did you mean 'CRIMSON'?"), diag.message)
    }

    @Test
    fun `el fallback de sede de clase mapea los entries sin par, sin else y sin KMX026`() {
        fun enumClass(name: String, fallback: String? = null, vararg entries: String) =
            dev.kmapx.core.model.MClass(
                type = dev.kmapx.core.model.MType(name, false, TypeKind.ENUM),
                enumEntries = entries.map { dev.kmapx.core.model.MEnumEntry(it) },
                enumFallback = fallback,
            )
        val plan = engine.resolve(
            enumClass("fx.Legacy", "UNKNOWN", "RED", "ARCHIVED_V1", "ARCHIVED_V2"),
            enumClass("fx.Status", null, "RED", "UNKNOWN"),
            ext("toStatus"),
        )
        assertTrue(plan.valid, plan.diagnostics.joinToString { it.render() })
        // El UNKNOWN del target quedó emparejado por el fallback: tampoco hay KMX023.
        assertTrue(plan.diagnostics.isEmpty(), plan.diagnostics.joinToString { it.render() })
        val dispatch = assertIs<dev.kmapx.core.plan.Construction.EnumDispatch>(plan.construction)
        assertEquals(
            listOf("RED" to "RED", "ARCHIVED_V1" to "UNKNOWN", "ARCHIVED_V2" to "UNKNOWN"),
            dispatch.entries.map { it.sourceEntry to it.targetEntry },
        )
    }

    @Test
    fun `fallback inexistente produce KMX047 con did-you-mean y KMX026 sigue`() {
        fun enumClass(name: String, fallback: String? = null, vararg entries: String) =
            dev.kmapx.core.model.MClass(
                type = dev.kmapx.core.model.MType(name, false, TypeKind.ENUM),
                enumEntries = entries.map { dev.kmapx.core.model.MEnumEntry(it) },
                enumFallback = fallback,
            )
        val plan = engine.resolve(
            enumClass("fx.Legacy", "UNKNWN", "RED", "ARCHIVED_V1"),
            enumClass("fx.Status", null, "RED", "UNKNOWN"),
            ext("toStatus"),
        )
        assertFalse(plan.valid)
        val kmx047 = plan.diagnostics.single { it.code == DiagnosticCode.KMX047 }
        assertTrue(kmx047.message.contains("Did you mean 'UNKNOWN'?"), kmx047.message)
        // Todos los errores en una pasada: el entry sin par también se reporta.
        assertTrue(plan.diagnostics.any { it.code == DiagnosticCode.KMX026 })
    }

    @Test
    fun `un override explicito roto sigue siendo KMX026 aunque haya fallback valido`() {
        val source = dev.kmapx.core.model.MClass(
            type = dev.kmapx.core.model.MType("fx.Legacy", false, TypeKind.ENUM),
            enumEntries = listOf(dev.kmapx.core.model.MEnumEntry("RED", targetOverride = "CRIMSN")),
            enumFallback = "UNKNOWN",
        )
        val target = dev.kmapx.core.model.MClass(
            type = dev.kmapx.core.model.MType("fx.Status", false, TypeKind.ENUM),
            enumEntries = listOf(
                dev.kmapx.core.model.MEnumEntry("CRIMSON"),
                dev.kmapx.core.model.MEnumEntry("UNKNOWN"),
            ),
        )
        val plan = engine.resolve(source, target, ext("toStatus"))
        assertFalse(plan.valid)
        val diag = plan.diagnostics.single { it.code == DiagnosticCode.KMX026 }
        assertTrue(diag.message.contains("Did you mean 'CRIMSON'?"), diag.message)
    }

    // ── Enums bidireccionales (gap @MapEntry+@BiMapTo cerrado) ────

    private fun enumSide(name: String, fallback: String? = null, vararg entries: dev.kmapx.core.model.MEnumEntry) =
        dev.kmapx.core.model.MClass(
            type = dev.kmapx.core.model.MType(name, false, TypeKind.ENUM),
            enumEntries = entries.toList(),
            enumFallback = fallback,
        )

    @Test
    fun `los overrides MapEntry de la ida se invierten en la vuelta`() {
        val resolution = engine.resolveBidirectional(
            a = enumSide(
                "fx.Color", null,
                dev.kmapx.core.model.MEnumEntry("RED", targetOverride = "CRIMSON"),
                dev.kmapx.core.model.MEnumEntry("GREEN"),
            ),
            b = enumSide(
                "fx.ColorDto", null,
                dev.kmapx.core.model.MEnumEntry("CRIMSON"),
                dev.kmapx.core.model.MEnumEntry("GREEN"),
            ),
            forwardEmission = ext("toColorDto"),
            reverseEmission = ext("toColor"),
        )
        assertTrue(resolution.valid, resolution.diagnostics.joinToString { it.render() })
        val forward = assertIs<dev.kmapx.core.plan.Construction.EnumDispatch>(resolution.forward.construction)
        assertTrue(forward.entries.any { it.sourceEntry == "RED" && it.targetEntry == "CRIMSON" })
        val reverse = assertIs<dev.kmapx.core.plan.Construction.EnumDispatch>(resolution.reverse.construction)
        assertTrue(
            reverse.entries.any { it.sourceEntry == "CRIMSON" && it.targetEntry == "RED" },
            "la vuelta debía invertir el override: ${reverse.entries}",
        )
    }

    @Test
    fun `el fallback de clase es fan-in y no es invertible - KMX028`() {
        val resolution = engine.resolveBidirectional(
            a = enumSide(
                "fx.Legacy", "UNKNOWN",
                dev.kmapx.core.model.MEnumEntry("RED"),
                dev.kmapx.core.model.MEnumEntry("ARCHIVED_V1"),
            ),
            b = enumSide(
                "fx.Status", null,
                dev.kmapx.core.model.MEnumEntry("RED"),
                dev.kmapx.core.model.MEnumEntry("UNKNOWN"),
            ),
            forwardEmission = ext("toStatus"),
            reverseEmission = ext("toLegacy"),
        )
        assertFalse(resolution.valid)
        val diag = resolution.diagnostics.first { it.code == DiagnosticCode.KMX028 }
        assertTrue(diag.message.contains("fan-in"), diag.message)
    }

    @Test
    fun `dos entries hacia el mismo destino son fan-in - KMX028`() {
        val resolution = engine.resolveBidirectional(
            a = enumSide(
                "fx.Color", null,
                dev.kmapx.core.model.MEnumEntry("RED", targetOverride = "CRIMSON"),
                dev.kmapx.core.model.MEnumEntry("CARMINE", targetOverride = "CRIMSON"),
            ),
            b = enumSide("fx.ColorDto", null, dev.kmapx.core.model.MEnumEntry("CRIMSON")),
            forwardEmission = ext("toColorDto"),
            reverseEmission = ext("toColor"),
        )
        assertFalse(resolution.valid)
        val diag = resolution.diagnostics.first { it.code == DiagnosticCode.KMX028 }
        assertTrue(diag.message.contains("fan-in") && diag.message.contains("CARMINE"), diag.message)
    }

    // ── Política unmapped ───────────────────────────────────

    @Test
    fun `la politica unmapped gradua el KMX021 - WARN historico, ERROR bloquea, IGNORE acalla`() {
        val string = mtype("kotlin.String")
        val source = dev.kmapx.core.model.MClass(
            type = mtype("fx.Src"),
            properties = listOf(dev.kmapx.core.model.MProperty("name", string)),
        )
        val target = dev.kmapx.core.model.MClass(
            type = mtype("fx.Dto"),
            constructors = listOf(
                dev.kmapx.core.model.MConstructor(
                    params = listOf(
                        dev.kmapx.core.model.MConstructorParam("name", string),
                        dev.kmapx.core.model.MConstructorParam("audit", string, hasDefault = true),
                    ),
                ),
            ),
        )

        val warn = engine.resolve(source, target, ext("toDto"))
        assertTrue(warn.valid)
        assertEquals(dev.kmapx.core.diagnostics.Severity.WARNING, warn.diagnostics.single().severity)
        assertEquals(DiagnosticCode.KMX021, warn.diagnostics.single().code)

        val error = engine.resolve(source, target, ext("toDto"), unmapped = UnmappedPolicy.ERROR)
        assertFalse(error.valid)
        assertEquals(dev.kmapx.core.diagnostics.Severity.ERROR, error.diagnostics.single().severity)
        assertEquals(DiagnosticCode.KMX021, error.diagnostics.single().code)

        val silent = engine.resolve(source, target, ext("toDto"), unmapped = UnmappedPolicy.IGNORE)
        assertTrue(silent.valid)
        assertTrue(silent.diagnostics.isEmpty(), silent.diagnostics.joinToString { it.render() })
    }

    // ── Conversiones implícitas ─────────────────────────────

    @Test
    fun `widening Int a Long resuelve con toLong, sin config`() {
        val plan = engine.resolve(srcOf(mtype("kotlin.Int")), targetOf(mtype("kotlin.Long")), ext("toDto"))
        val widening = assertIs<ValueSource.NumericWidening>(singleValue(plan))
        assertEquals("toLong", widening.toFunction)
        assertFalse(widening.safeCall)
    }

    @Test
    fun `narrowing Long a Int sigue siendo KMX004`() {
        val plan = engine.resolve(srcOf(mtype("kotlin.Long")), targetOf(mtype("kotlin.Int")), ext("toDto"))
        assertFalse(plan.valid)
        assertEquals(DiagnosticCode.KMX004, plan.diagnostics.single().code)
    }

    @Test
    fun `Int nullable a Long nullable compone con safeCall`() {
        val plan = engine.resolve(
            srcOf(dev.kmapx.core.model.MType("kotlin.Int", nullable = true)),
            targetOf(dev.kmapx.core.model.MType("kotlin.Long", nullable = true)),
            ext("toDto"),
        )
        assertTrue(assertIs<ValueSource.NumericWidening>(singleValue(plan)).safeCall)
    }

    @Test
    fun `Int nullable a Long con OR_THROW aplica la estrategia SOBRE el widening`() {
        val plan = engine.resolve(
            srcOf(dev.kmapx.core.model.MType("kotlin.Int", nullable = true)),
            targetOf(mtype("kotlin.Long")),
            ext("toDto"),
            nullPolicies = listOf(NullPolicy.OR_THROW),
        )
        val over = assertIs<ValueSource.NullStrategyOver>(singleValue(plan))
        assertTrue(assertIs<ValueSource.NumericWidening>(over.inner).safeCall)
        assertIs<dev.kmapx.core.plan.StrategyOutcome.Throw>(over.outcome)
    }

    @Test
    fun `el elemento de la lista hereda el widening`() {
        val plan = engine.resolve(
            srcOf(listOfType(mtype("kotlin.Int"))),
            targetOf(listOfType(mtype("kotlin.Long"))),
            ext("toDto"),
        )
        val elements = assertIs<ValueSource.MapElements>(singleValue(plan))
        assertIs<ValueSource.NumericWidening>(elements.element)
    }

    @Test
    fun `String a UUID solo resuelve con stdConverters`() {
        val off = engine.resolve(srcOf(mtype("kotlin.String")), targetOf(mtype("java.util.UUID")), ext("toDto"))
        assertFalse(off.valid)
        assertEquals(DiagnosticCode.KMX004, off.diagnostics.single().code)

        val on = engine.resolve(
            srcOf(mtype("kotlin.String")), targetOf(mtype("java.util.UUID")), ext("toDto"),
            stdConverters = true,
        )
        val builtin = assertIs<ValueSource.BuiltinConversion>(singleValue(on))
        assertTrue(builtin.template.contains("UUID.fromString"), builtin.template)
    }

    @Test
    fun `un Converter del usuario GANA sobre el widening`() {
        val plan = engine.resolve(
            srcOf(mtype("kotlin.Int")), targetOf(mtype("kotlin.Long")), ext("toDto"),
            converters = mapOf(("kotlin.Int" to "kotlin.Long") to listOf("fx.intToLong")),
        )
        assertIs<ValueSource.ViaConverter>(singleValue(plan))
    }

    // ── Anidados automáticos ───────────────────────────────────────────

    @Test
    fun `anidado top-level resuelve con el mapper declarado, por referencia`() {
        val plan = engine.resolve(
            srcOf(addressType), targetOf(addressDtoType), ext("toDto"),
            declaredMappings = declared,
        )
        assertTrue(plan.valid, plan.diagnostics.joinToString { it.render() })
        val via = assertIs<ValueSource.ViaMapper>(singleValue(plan))
        assertEquals(
            "fx.toAddressDto",
            (via.mapper as dev.kmapx.core.plan.MapperRef.GeneratedExtension).qualifiedFunction,
        )
        assertFalse(via.safeCall)
    }

    @Test
    fun `par mapeable sin declaracion produce KMX007, no KMX004`() {
        val plan = engine.resolve(srcOf(addressType), targetOf(addressDtoType), ext("toDto"))
        assertFalse(plan.valid)
        val diag = plan.diagnostics.single()
        assertEquals(DiagnosticCode.KMX007, diag.code)
        assertTrue(diag.message.contains("no mapping found for Address -> AddressDto"), diag.message)
        assertTrue(diag.fix.contains("@MapTo(AddressDto::class)"), diag.fix)
    }

    @Test
    fun `anidado nullable compone con safeCall`() {
        val plan = engine.resolve(
            srcOf(addressType.asNullable()), targetOf(addressDtoType.asNullable()), ext("toDto"),
            declaredMappings = declared,
        )
        assertTrue(assertIs<ValueSource.ViaMapper>(singleValue(plan)).safeCall)
    }

    @Test
    fun `anidado nullable a no-nullable exige estrategia y esta envuelve el resultado`() {
        val sin = engine.resolve(
            srcOf(addressType.asNullable()), targetOf(addressDtoType), ext("toDto"),
            declaredMappings = declared,
        )
        assertFalse(sin.valid)
        assertEquals(DiagnosticCode.KMX003, sin.diagnostics.single().code)

        val target = dev.kmapx.core.model.MClass(
            type = mtype("fx.Dto"),
            constructors = listOf(
                dev.kmapx.core.model.MConstructor(
                    params = listOf(
                        dev.kmapx.core.model.MConstructorParam(
                            "items", addressDtoType,
                            strategies = listOf(dev.kmapx.core.model.MNullStrategy.OrThrow),
                        ),
                    ),
                ),
            ),
        )
        val con = engine.resolve(
            srcOf(addressType.asNullable()), target, ext("toDto"),
            declaredMappings = declared,
        )
        assertTrue(con.valid, con.diagnostics.joinToString { it.render() })
        val over = assertIs<ValueSource.NullStrategyOver>(singleValue(con))
        assertTrue(assertIs<ValueSource.ViaMapper>(over.inner).safeCall)
    }

    // ── Map, arrays, Result ────────────────────────────────────────────

    private fun mapOfTypes(k: dev.kmapx.core.model.MType, v: dev.kmapx.core.model.MType) =
        dev.kmapx.core.model.MType(
            "kotlin.collections.Map", nullable = false, kind = TypeKind.COLLECTION_MAP,
            typeArgs = listOf(k, v),
        )

    private fun arrayOfType(e: dev.kmapx.core.model.MType) = dev.kmapx.core.model.MType(
        "kotlin.Array", nullable = false, kind = TypeKind.COLLECTION_ARRAY, typeArgs = listOf(e),
    )

    private fun resultOfType(e: dev.kmapx.core.model.MType) = dev.kmapx.core.model.MType(
        "kotlin.Result", nullable = false, kind = TypeKind.RESULT, typeArgs = listOf(e),
    )

    @Test
    fun `Map identico y covarianza del valor son referencia directa`() {
        val stringT = mtype("kotlin.String")
        assertIs<ValueSource.Direct>(
            singleValue(engine.resolve(srcOf(mapOfTypes(stringT, stringT)), targetOf(mapOfTypes(stringT, stringT)), ext("toDto"))),
        )
        // V es covariante: Map<String,String> → Map<String,String?> asigna directo.
        assertIs<ValueSource.Direct>(
            singleValue(
                engine.resolve(
                    srcOf(mapOfTypes(stringT, stringT)),
                    targetOf(mapOfTypes(stringT, stringT.asNullable())), ext("toDto"),
                ),
            ),
        )
    }

    @Test
    fun `K es invariante - ensanchar la clave exige copia via mapKeys`() {
        val stringT = mtype("kotlin.String")
        val plan = engine.resolve(
            srcOf(mapOfTypes(stringT, stringT)),
            targetOf(mapOfTypes(stringT.asNullable(), stringT)), ext("toDto"),
        )
        val entries = assertIs<ValueSource.MapEntries>(singleValue(plan))
        assertIs<ValueSource.Direct>(entries.key!!)
        assertEquals(null, entries.value)
    }

    @Test
    fun `Map con valor mapeado por el mapper declarado`() {
        val plan = engine.resolve(
            srcOf(mapOfTypes(mtype("kotlin.String"), addressType)),
            targetOf(mapOfTypes(mtype("kotlin.String"), addressDtoType)),
            ext("toDto"), declaredMappings = declared,
        )
        val entries = assertIs<ValueSource.MapEntries>(singleValue(plan))
        assertEquals(null, entries.key)
        assertEquals("v", assertIs<ValueSource.ViaMapper>(entries.value!!).source.name)
    }

    @Test
    fun `Map con clave y valor mapeados`() {
        val userIdT = dev.kmapx.core.model.MType(
            "fx.UserId", nullable = false, kind = TypeKind.VALUE_CLASS, underlying = mtype("kotlin.String"),
        )
        val plan = engine.resolve(
            srcOf(mapOfTypes(userIdT, addressType)),
            targetOf(mapOfTypes(mtype("kotlin.String"), addressDtoType)),
            ext("toDto"), declaredMappings = declared,
        )
        val entries = assertIs<ValueSource.MapEntries>(singleValue(plan))
        assertEquals("k", assertIs<ValueSource.UnwrapValueClass>(entries.key!!).source.name)
        assertIs<ValueSource.ViaMapper>(entries.value!!)
    }

    @Test
    fun `estrategia del parametro aplica al VALOR del Map`() {
        val stringT = mtype("kotlin.String")
        val target = dev.kmapx.core.model.MClass(
            type = mtype("fx.Dto"),
            constructors = listOf(
                dev.kmapx.core.model.MConstructor(
                    params = listOf(
                        dev.kmapx.core.model.MConstructorParam(
                            "items", mapOfTypes(stringT, stringT),
                            strategies = listOf(dev.kmapx.core.model.MNullStrategy.WithDefault("N/A")),
                        ),
                    ),
                ),
            ),
        )
        val plan = engine.resolve(srcOf(mapOfTypes(stringT, stringT.asNullable())), target, ext("toDto"))
        val entries = assertIs<ValueSource.MapEntries>(singleValue(plan))
        assertEquals("\"N/A\"", assertIs<ValueSource.NullFallbackToValue>(entries.value!!).default)
    }

    @Test
    fun `Map anidado - el valor es a su vez una lista mapeada`() {
        val plan = engine.resolve(
            srcOf(mapOfTypes(mtype("kotlin.String"), listOfType(addressType))),
            targetOf(mapOfTypes(mtype("kotlin.String"), listOfType(addressDtoType))),
            ext("toDto"), declaredMappings = declared,
        )
        val entries = assertIs<ValueSource.MapEntries>(singleValue(plan))
        val inner = assertIs<ValueSource.MapElements>(entries.value!!)
        assertIs<ValueSource.ViaMapper>(inner.element)
    }

    @Test
    fun `Array con mapper declarado y Array identico`() {
        val mapped = engine.resolve(
            srcOf(arrayOfType(addressType)), targetOf(arrayOfType(addressDtoType)),
            ext("toDto"), declaredMappings = declared,
        )
        val me = assertIs<ValueSource.MapElements>(singleValue(mapped))
        assertEquals(TypeKind.COLLECTION_ARRAY, me.into)

        val direct = engine.resolve(
            srcOf(arrayOfType(mtype("kotlin.String"))), targetOf(arrayOfType(mtype("kotlin.String"))), ext("toDto"),
        )
        assertIs<ValueSource.Direct>(singleValue(direct))
    }

    @Test
    fun `Array es invariante - ensanchar el elemento NO es directo`() {
        val plan = engine.resolve(
            srcOf(arrayOfType(mtype("kotlin.String"))),
            targetOf(arrayOfType(mtype("kotlin.String").asNullable())), ext("toDto"),
        )
        val me = assertIs<ValueSource.MapElements>(singleValue(plan))
        assertEquals(TypeKind.COLLECTION_ARRAY, me.into)
    }

    @Test
    fun `Result con converter de elemento`() {
        val plan = engine.resolve(
            srcOf(resultOfType(instantType)), targetOf(resultOfType(mtype("kotlin.String"))),
            ext("toDto"), converters = isoConverter,
        )
        val me = assertIs<ValueSource.MapElements>(singleValue(plan))
        assertEquals(TypeKind.RESULT, me.into)
        assertIs<ValueSource.ViaConverter>(me.element)
    }

    @Test
    fun `cruces de contenedor NO son implicitos - Map a List es KMX004`() {
        val stringT = mtype("kotlin.String")
        val plan = engine.resolve(
            srcOf(mapOfTypes(stringT, stringT)), targetOf(listOfType(stringT)), ext("toDto"),
        )
        assertFalse(plan.valid)
        assertEquals(DiagnosticCode.KMX004, plan.diagnostics.single().code)
    }

    // ── Bidireccional ──────────────────────────────────────────────────

    @Test
    fun `base valida y el renombre de la ida define el matching de vuelta`() {
        data class Person(val firstname: String, val age: Int)
        val a = mclassOf<Person>()
        // B a mano: param `name` con mappedFrom=firstname (el @Map es SOURCE).
        val b = dev.kmapx.core.model.MClass(
            type = mtype("fx.PersonDto"),
            properties = listOf(
                dev.kmapx.core.model.MProperty("name", mtype("kotlin.String")),
                dev.kmapx.core.model.MProperty("age", mtype("kotlin.Int")),
            ),
            constructors = listOf(
                dev.kmapx.core.model.MConstructor(
                    params = listOf(
                        dev.kmapx.core.model.MConstructorParam("name", mtype("kotlin.String"), mappedFrom = "firstname"),
                        dev.kmapx.core.model.MConstructorParam("age", mtype("kotlin.Int")),
                    ),
                ),
            ),
        )
        val bi = engine.resolveBidirectional(a, b, ext("toPersonDto"), ext("toPerson"))
        assertTrue(bi.valid, bi.diagnostics.joinToString { it.render() })
        // Ida: name = firstname
        val fwd = assertIs<dev.kmapx.core.plan.Construction.ConstructorCall>(bi.forward.construction)
        assertEquals("firstname", assertIs<ValueSource.Direct>(fwd.arguments[0].value).source.name)
        // Vuelta (invertido automáticamente): firstname = name
        val rev = assertIs<dev.kmapx.core.plan.Construction.ConstructorCall>(bi.reverse.construction)
        val firstnameArg = rev.arguments.first { it.paramName == "firstname" }
        assertEquals("name", assertIs<ValueSource.Direct>(firstnameArg.value).source.name)
    }

    @Test
    fun `converter sin inverso produce KMX028 con el par faltante`() {
        data class Event(val at: java.time.Instant)
        data class EventDto(val at: String)
        val bi = engine.resolveBidirectional(
            mclassOf<Event>(), mclassOf<EventDto>(), ext("toEventDto"), ext("toEvent"),
            converters = isoConverter, // solo Instant -> String
        )
        assertFalse(bi.valid)
        val diag = bi.diagnostics.single { it.code == DiagnosticCode.KMX028 }
        assertTrue(diag.message.contains("missing converter kotlin.String to java.time.Instant") ||
            diag.message.contains("missing converter kotlin.String"), diag.message)
        assertTrue(diag.fix.contains("register the inverse @Converter"), diag.fix)
        assertTrue(diag.fix.contains("two one-way @MapTo"), diag.fix)
    }

    @Test
    fun `converter con inverso registrado - ambas direcciones lo usan`() {
        data class Event(val at: java.time.Instant)
        data class EventDto(val at: String)
        val both = isoConverter + mapOf(("kotlin.String" to "java.time.Instant") to listOf("fx.isoToInstant"))
        val bi = engine.resolveBidirectional(
            mclassOf<Event>(), mclassOf<EventDto>(), ext("toEventDto"), ext("toEvent"),
            converters = both,
        )
        assertTrue(bi.valid, bi.diagnostics.joinToString { it.render() })
        assertIs<ValueSource.ViaConverter>(
            assertIs<dev.kmapx.core.plan.Construction.ConstructorCall>(bi.forward.construction).arguments.single().value,
        )
        assertIs<ValueSource.ViaConverter>(
            assertIs<dev.kmapx.core.plan.Construction.ConstructorCall>(bi.reverse.construction).arguments.single().value,
        )
    }

    @Test
    fun `ensanchamiento de ida sin estrategia de vuelta produce KMX028`() {
        data class Person(val nickname: String)
        data class PersonDto(val nickname: String?)
        val bi = engine.resolveBidirectional(
            mclassOf<Person>(), mclassOf<PersonDto>(), ext("toDto"), ext("toPerson"),
        )
        assertFalse(bi.valid)
        val diag = bi.diagnostics.single { it.code == DiagnosticCode.KMX028 }
        assertTrue(diag.message.contains("widens to nullable"), diag.message)
        assertTrue(diag.fix.contains("THROW"), diag.fix)
    }

    @Test
    fun `la politica de nivel satisface la estrategia de vuelta`() {
        // El MISMO par del test anterior: con la cascada declarando THROW, la vuelta resuelve
        // (una política de nivel ES una estrategia declarada para la vuelta).
        data class Person(val nickname: String)
        data class PersonDto(val nickname: String?)
        val bi = engine.resolveBidirectional(
            mclassOf<Person>(), mclassOf<PersonDto>(), ext("toDto"), ext("toPerson"),
            nullPolicies = listOf(NullPolicy.OR_THROW),
        )
        assertTrue(bi.valid, bi.diagnostics.joinToString { it.render() })
        assertTrue(bi.diagnostics.none { it.code == DiagnosticCode.KMX028 })
    }

    @Test
    fun `con estrategia declarada en el lado A la vuelta resuelve`() {
        val a = dev.kmapx.core.model.MClass(
            type = mtype("fx.Person"),
            properties = listOf(dev.kmapx.core.model.MProperty("nickname", mtype("kotlin.String"))),
            constructors = listOf(
                dev.kmapx.core.model.MConstructor(
                    params = listOf(
                        dev.kmapx.core.model.MConstructorParam(
                            "nickname", mtype("kotlin.String"),
                            strategies = listOf(dev.kmapx.core.model.MNullStrategy.OrThrow),
                        ),
                    ),
                ),
            ),
        )
        val b = dev.kmapx.core.model.MClass(
            type = mtype("fx.PersonDto"),
            properties = listOf(dev.kmapx.core.model.MProperty("nickname", mtype("kotlin.String").asNullable())),
            constructors = listOf(
                dev.kmapx.core.model.MConstructor(
                    params = listOf(
                        dev.kmapx.core.model.MConstructorParam("nickname", mtype("kotlin.String").asNullable()),
                    ),
                ),
            ),
        )
        val bi = engine.resolveBidirectional(a, b, ext("toDto"), ext("toPerson"))
        assertTrue(bi.valid, bi.diagnostics.joinToString { it.render() })
        assertIs<ValueSource.NullOrThrow>(
            assertIs<dev.kmapx.core.plan.Construction.ConstructorCall>(bi.reverse.construction).arguments.single().value,
        )
    }

    @Test
    fun `fan-out produce KMX028`() {
        data class Person(val firstname: String)
        val b = dev.kmapx.core.model.MClass(
            type = mtype("fx.PersonDto"),
            properties = listOf(
                dev.kmapx.core.model.MProperty("display", mtype("kotlin.String")),
                dev.kmapx.core.model.MProperty("sortKey", mtype("kotlin.String")),
            ),
            constructors = listOf(
                dev.kmapx.core.model.MConstructor(
                    params = listOf(
                        dev.kmapx.core.model.MConstructorParam("display", mtype("kotlin.String"), mappedFrom = "firstname"),
                        dev.kmapx.core.model.MConstructorParam("sortKey", mtype("kotlin.String"), mappedFrom = "firstname"),
                    ),
                ),
            ),
        )
        val bi = engine.resolveBidirectional(mclassOf<Person>(), b, ext("toDto"), ext("toPerson"))
        assertFalse(bi.valid)
        assertTrue(bi.diagnostics.any { it.code == DiagnosticCode.KMX028 && it.message.contains("fan-out") })
    }

    @Test
    fun `campo solo en A produce KMX028 (el round-trip no reconstruye)`() {
        data class Person(val name: String, val internalFlag: Boolean)
        data class PersonDto(val name: String)
        val bi = engine.resolveBidirectional(
            mclassOf<Person>(), mclassOf<PersonDto>(), ext("toDto"), ext("toPerson"),
        )
        assertFalse(bi.valid)
        val diag = bi.diagnostics.single { it.code == DiagnosticCode.KMX028 }
        assertTrue(diag.message.contains("exists only on"), diag.message)
        assertTrue(diag.message.contains("round-trip cannot reconstruct"), diag.message)
    }

    @Test
    fun `MType es estable - equals y hashCode por valor`() {
        val a = mclassOf<Person>().type
        val b = mclassOf<Person>().type
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }
}
