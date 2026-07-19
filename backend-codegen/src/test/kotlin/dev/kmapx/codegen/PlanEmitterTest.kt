package dev.kmapx.codegen

import dev.kmapx.core.engine.MappingEngine
import dev.kmapx.core.plan.Emission
import dev.kmapx.reflect.mclassOf
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Snapshot testing casero, sin dependencias.
 * Los snapshots viven en src/test/snapshots y se revisan en PR (el output es determinista).
 * Para regenerar: ejecutar con -Dkmapx.updateSnapshots=true y revisar el diff.
 */
object Snapshots {
    private val dir: Path = Path.of("src/test/snapshots")
    private val update = System.getProperty("kmapx.updateSnapshots") == "true"

    fun assertMatches(name: String, actual: String) {
        val file = dir.resolve("$name.snap.kt")
        if (update || !Files.exists(file)) {
            Files.createDirectories(dir)
            Files.writeString(file, actual)
            if (!update) error("Snapshot '$name' no existía; se creó. Revisa el contenido y re-ejecuta.")
            return
        }
        assertEquals(Files.readString(file), actual, "Snapshot '$name' difiere del código generado")
    }
}

// ── Fixtures ────────────────────────────────────────────────────────────────

@JvmInline value class OrderId(val value: String)
data class Order(val id: OrderId, val total: Long)
data class OrderDto(val id: String, val total: Long)

/** Mismo par de campos en orden inverso: el orden de argumentos sigue al constructor TARGET. */
data class OrderDtoReversed(val total: Long, val id: String)
data class OrderSummary(val total: Long)

/** `var` de cuerpo asignada post-construcción. */
class TaskTarget(val id: String) { var status: String = "NEW" }
data class TaskSrc(val id: String, val status: String)

/** Jerarquías sealed paralelas. */
sealed interface Payment {
    data class Approved(val amount: Long) : Payment
    data object Pending : Payment
}
sealed interface PaymentDto {
    data class Approved(val amount: Long) : PaymentDto
    data object Pending : PaymentDto
}

/** Enums paralelos. */
enum class Priority { LOW, HIGH }
enum class PriorityDto { LOW, HIGH }

/** PATCH vía copy(). */
data class PatchCustomer(val name: String, val nickname: String)
data class PatchCustomerDto(val name: String?, val nickname: String?)

/** Omisión condicional de defaults. */
data class ProfileDto(val name: String, val nickname: String = "N/A")
data class ProfileSrc(val name: String, val nickname: String?)
data class CardDto(val id: String, val nickname: String = "N/A", val bio: String = "-")
data class CardSrc(val id: String, val nickname: String?, val bio: String?)

class PlanEmitterTest {

    private val engine = MappingEngine()
    private val emitter = PlanEmitter()

    @Test
    fun `plan valido de Order a OrderDto emite extension con unwrap - snapshot`() {
        val plan = engine.resolve(
            mclassOf<Order>(), mclassOf<OrderDto>(),
            Emission.ExtensionFunction("toOrderDto"),
        )
        assertTrue(plan.valid, plan.diagnostics.joinToString { it.render() })

        val file = emitter.emit(plan)

        // El contrato es GeneratedFile, no un tipo de KotlinPoet:
        assertEquals("dev.kmapx.codegen", file.packageName)
        assertEquals("OrderMappings", file.fileName)

        // El código generado es exactamente el que se escribiría a mano:
        assertTrue(file.content.contains("public fun Order.toOrderDto(): OrderDto"), file.content)
        assertTrue(file.content.contains("id = id.value"), file.content)
        assertTrue(file.content.contains("total = total"), file.content)

        Snapshots.assertMatches("Order_toOrderDto", file.content)
    }

    @Test
    fun `varios planes del mismo source emiten un solo archivo con N funciones`() {
        val toDto = engine.resolve(mclassOf<Order>(), mclassOf<OrderDto>(), Emission.ExtensionFunction("toOrderDto"))
        val toSummary = engine.resolve(mclassOf<Order>(), mclassOf<OrderSummary>(), Emission.ExtensionFunction("toOrderSummary"))
        assertTrue(toDto.valid && toSummary.valid)

        val file = emitter.emit(listOf(toDto, toSummary))

        assertEquals("OrderMappings", file.fileName)
        assertTrue(file.content.contains("public fun Order.toOrderDto(): OrderDto"), file.content)
        assertTrue(file.content.contains("public fun Order.toOrderSummary(): OrderSummary"), file.content)
        Snapshots.assertMatches("Order_multiTarget", file.content)
    }

    @Test
    fun `visibilidad internal del source se replica en la funcion`() {
        val plan = engine.resolve(
            mclassOf<Order>(), mclassOf<OrderDto>(),
            Emission.ExtensionFunction("toOrderDto", isInternal = true),
        )
        val file = emitter.emit(plan)
        assertTrue(file.content.contains("internal fun Order.toOrderDto(): OrderDto"), file.content)
    }

    @Test
    fun `orden de argumentos = orden del constructor target - determinismo - snapshot`() {
        val plan = engine.resolve(
            mclassOf<Order>(), mclassOf<OrderDtoReversed>(),
            Emission.ExtensionFunction("toOrderDtoReversed"),
        )
        assertTrue(plan.valid, plan.diagnostics.joinToString { it.render() })
        val file = emitter.emit(plan)

        val totalIndex = file.content.indexOf("total = total")
        val idIndex = file.content.indexOf("id = id.value")
        assertTrue(totalIndex in 0 until idIndex, "el orden debe seguir al constructor target:\n${file.content}")
        Snapshots.assertMatches("Order_toOrderDtoReversed", file.content)
    }

    @Test
    fun `var de cuerpo se asigna post-construccion via also - snapshot`() {
        val plan = engine.resolve(
            mclassOf<TaskSrc>(), mclassOf<TaskTarget>(),
            Emission.ExtensionFunction("toTaskTarget"),
        )
        assertTrue(plan.valid, plan.diagnostics.joinToString { it.render() })
        val file = emitter.emit(plan)

        assertTrue(file.content.contains(".also {"), file.content)
        assertTrue(file.content.contains("it.status = status"), file.content)
        Snapshots.assertMatches("TaskSrc_toTaskTarget", file.content)
    }

    @Test
    fun `factory de companion se emite como Target-funcion - snapshot`() {
        val target = dev.kmapx.core.model.MType("dev.kmapx.codegen.Temperature", nullable = false)
        val plan = dev.kmapx.core.plan.MappingPlan(
            source = dev.kmapx.core.model.MType("dev.kmapx.codegen.Reading", nullable = false),
            target = target,
            emission = Emission.ExtensionFunction("toTemperature"),
            construction = dev.kmapx.core.plan.Construction.FactoryCall(
                qualifiedFunction = "dev.kmapx.codegen.Temperature.fromKelvin",
                companionOf = "dev.kmapx.codegen.Temperature",
                arguments = listOf(
                    dev.kmapx.core.plan.Argument("kelvin", dev.kmapx.core.plan.ValueSource.Direct(dev.kmapx.core.plan.ref("kelvin"))),
                ),
            ),
        )
        val file = emitter.emit(plan)
        assertTrue(file.content.contains("Temperature.fromKelvin("), file.content)
        assertTrue(file.content.contains("kelvin = kelvin"), file.content)
        Snapshots.assertMatches("Reading_toTemperature", file.content)
    }

    @Test
    fun `factory top-level se referencia por nombre e importa`() {
        val plan = dev.kmapx.core.plan.MappingPlan(
            source = dev.kmapx.core.model.MType("dev.kmapx.codegen.Reading", nullable = false),
            target = dev.kmapx.core.model.MType("dev.kmapx.other.Temperature", nullable = false),
            emission = Emission.ExtensionFunction("toTemperature"),
            construction = dev.kmapx.core.plan.Construction.FactoryCall(
                qualifiedFunction = "dev.kmapx.other.temperatureOf",
                companionOf = null,
                arguments = listOf(
                    dev.kmapx.core.plan.Argument("kelvin", dev.kmapx.core.plan.ValueSource.Direct(dev.kmapx.core.plan.ref("kelvin"))),
                ),
            ),
        )
        val file = emitter.emit(plan)
        assertTrue(file.content.contains("import dev.kmapx.other.temperatureOf"), file.content)
        assertTrue(file.content.contains("temperatureOf("), file.content)
        Snapshots.assertMatches("Reading_topLevelFactory", file.content)
    }

    @Test
    fun `mapper object con delegacion a la extension - snapshot`() {
        val plan = dev.kmapx.core.plan.MapperImplPlan(
            interfaceQualifiedName = "dev.kmapx.codegen.PersonMapper",
            componentModel = Emission.Component.NONE,
            methods = listOf(
                dev.kmapx.core.plan.MapperMethod(
                    name = "toDto",
                    parameters = listOf(
                        dev.kmapx.core.plan.MParam("p", dev.kmapx.core.model.MType("dev.kmapx.codegen.Person", nullable = false)),
                    ),
                    returns = dev.kmapx.core.model.MType("dev.kmapx.codegen.PersonDto", nullable = false),
                    body = dev.kmapx.core.plan.MethodBody.DelegateToExtension("p", "dev.kmapx.codegen.toPersonDto"),
                ),
            ),
        )
        val file = emitter.emitMapper(plan)

        assertEquals("PersonMapperImpl", file.fileName)
        assertTrue(file.content.contains("public object PersonMapperImpl : PersonMapper"), file.content)
        assertTrue(file.content.contains("override fun toDto(p: Person): PersonDto"), file.content)
        assertTrue(file.content.contains("p.toPersonDto()"), file.content)
        Snapshots.assertMatches("PersonMapperImpl_delegacion", file.content)
    }

    @Test
    fun `mapper con componentModel SPRING emite class, no object`() {
        val plan = dev.kmapx.core.plan.MapperImplPlan(
            interfaceQualifiedName = "dev.kmapx.codegen.PersonMapper",
            componentModel = Emission.Component.SPRING,
            methods = emptyList(),
        )
        val file = emitter.emitMapper(plan)
        assertTrue(file.content.contains("public class PersonMapperImpl : PersonMapper"), file.content)
    }

    // ── Estrategias ────────────────────────────────────────────────────

    private fun strategyPlan(value: dev.kmapx.core.plan.ValueSource, name: String) =
        dev.kmapx.core.plan.MappingPlan(
            source = dev.kmapx.core.model.MType("dev.kmapx.codegen.NickSrc", nullable = false),
            target = dev.kmapx.core.model.MType("dev.kmapx.codegen.NickDto", nullable = false),
            emission = Emission.ExtensionFunction(name),
            construction = dev.kmapx.core.plan.Construction.ConstructorCall(
                arguments = listOf(dev.kmapx.core.plan.Argument("nickname", value)),
            ),
        )

    @Test
    fun `estrategia de nulabilidad - WithDefault y OrThrow - snapshot`() {
        val withDefault = emitter.emit(
            strategyPlan(
                dev.kmapx.core.plan.ValueSource.NullFallbackToValue(dev.kmapx.core.plan.ref("nickname"), "\"N/A\""),
                "toNickDtoWithDefault",
            ),
        )
        assertTrue(withDefault.content.contains("""nickname = nickname ?: "N/A""""), withDefault.content)
        Snapshots.assertMatches("NickSrc_withDefault", withDefault.content)

        val orThrow = emitter.emit(
            strategyPlan(
                dev.kmapx.core.plan.ValueSource.NullOrThrow(
                    dev.kmapx.core.plan.ref("nickname"),
                    "nickname must not be null mapping NickSrc -> NickDto",
                ),
                "toNickDtoOrThrow",
            ),
        )
        assertTrue(orThrow.content.contains("throw IllegalArgumentException"), orThrow.content)
        Snapshots.assertMatches("NickSrc_orThrow", orThrow.content)
    }

    @Test
    fun `estrategia de nulabilidad - AllowUnsafe - snapshot (unico consentimiento de doble bang)`() {
        val unsafe = emitter.emit(
            strategyPlan(
                dev.kmapx.core.plan.ValueSource.NullUnsafe(dev.kmapx.core.plan.ref("nickname")),
                "toNickDtoUnsafe",
            ),
        )
        assertTrue(unsafe.content.contains("nickname = nickname!!"), unsafe.content)
        Snapshots.assertMatches("NickSrc_allowUnsafe", unsafe.content)
    }

    // ── Colecciones ────────────────────────────────────────────────────

    private fun collectionPlan(value: dev.kmapx.core.plan.ValueSource, name: String) =
        dev.kmapx.core.plan.MappingPlan(
            source = dev.kmapx.core.model.MType("dev.kmapx.codegen.BoxSrc", nullable = false),
            target = dev.kmapx.core.model.MType("dev.kmapx.codegen.BoxDto", nullable = false),
            emission = Emission.ExtensionFunction(name),
            construction = dev.kmapx.core.plan.Construction.ConstructorCall(
                arguments = listOf(dev.kmapx.core.plan.Argument("items", value)),
            ),
        )

    @Test
    fun `MapElements emite un solo map con el elemento como it - snapshot`() {
        val plan = collectionPlan(
            dev.kmapx.core.plan.ValueSource.MapElements(
                source = dev.kmapx.core.plan.ref("items"),
                element = dev.kmapx.core.plan.ValueSource.ViaMapper(
                    dev.kmapx.core.plan.ref("it"),
                    dev.kmapx.core.plan.MapperRef.GeneratedExtension("dev.kmapx.codegen.toAddressDto"),
                ),
            ),
            "toBoxDto",
        )
        val file = emitter.emit(plan)
        assertTrue(file.content.contains("items = items.map { it.toAddressDto() }"), file.content)
        Snapshots.assertMatches("BoxSrc_mapElements", file.content)
    }

    @Test
    fun `MapElements hacia Set materializa en una pasada con mapTo`() {
        val plan = collectionPlan(
            dev.kmapx.core.plan.ValueSource.MapElements(
                source = dev.kmapx.core.plan.ref("items"),
                element = dev.kmapx.core.plan.ValueSource.ViaMapper(
                    dev.kmapx.core.plan.ref("it"),
                    dev.kmapx.core.plan.MapperRef.GeneratedExtension("dev.kmapx.codegen.toAddressDto"),
                ),
                into = dev.kmapx.core.model.TypeKind.COLLECTION_SET,
            ),
            "toBoxDtoSet",
        )
        val file = emitter.emit(plan)
        assertTrue(file.content.contains("items.mapTo(mutableSetOf()) { it.toAddressDto() }"), file.content)
    }

    @Test
    fun `MapElements anidado emite una lambda por nivel`() {
        val plan = collectionPlan(
            dev.kmapx.core.plan.ValueSource.MapElements(
                source = dev.kmapx.core.plan.ref("items"),
                element = dev.kmapx.core.plan.ValueSource.MapElements(
                    source = dev.kmapx.core.plan.ref("it"),
                    element = dev.kmapx.core.plan.ValueSource.NullFallbackToValue(
                        dev.kmapx.core.plan.ref("it"), "\"N/A\"",
                    ),
                ),
            ),
            "toBoxDtoNested",
        )
        val file = emitter.emit(plan)
        assertTrue(file.content.contains("""items.map { it.map { it ?: "N/A" } }"""), file.content)
    }

    // ── Converters ─────────────────────────────────────────────────────

    private fun converterPlan(value: dev.kmapx.core.plan.ValueSource) = dev.kmapx.core.plan.MappingPlan(
        source = dev.kmapx.core.model.MType("dev.kmapx.codegen.EventSrc", nullable = false),
        target = dev.kmapx.core.model.MType("dev.kmapx.codegen.EventDto", nullable = false),
        emission = Emission.ExtensionFunction("toEventDto"),
        construction = dev.kmapx.core.plan.Construction.ConstructorCall(
            arguments = listOf(dev.kmapx.core.plan.Argument("createdAt", value)),
        ),
    )

    @Test
    fun `converter mismo paquete - nombre corto sin import - snapshot`() {
        val file = emitter.emit(
            converterPlan(
                dev.kmapx.core.plan.ValueSource.ViaConverter(
                    dev.kmapx.core.plan.ref("createdAt"),
                    dev.kmapx.core.plan.ConverterRef("dev.kmapx.codegen.instantToIso"),
                ),
            ),
        )
        assertTrue(file.content.contains("createdAt = instantToIso(createdAt)"), file.content)
        Snapshots.assertMatches("EventSrc_viaConverter", file.content)
    }

    @Test
    fun `converter de otro paquete - nombre corto CON import (refactor-safe)`() {
        val file = emitter.emit(
            converterPlan(
                dev.kmapx.core.plan.ValueSource.ViaConverter(
                    dev.kmapx.core.plan.ref("createdAt"),
                    dev.kmapx.core.plan.ConverterRef("dev.kmapx.other.instantToIso"),
                ),
            ),
        )
        assertTrue(file.content.contains("import dev.kmapx.other.instantToIso"), file.content)
        assertTrue(file.content.contains("createdAt = instantToIso(createdAt)"), file.content)
    }

    @Test
    fun `converter con fuente nullable se envuelve con safeCall`() {
        val file = emitter.emit(
            converterPlan(
                dev.kmapx.core.plan.ValueSource.ViaConverter(
                    dev.kmapx.core.plan.ref("createdAt"),
                    dev.kmapx.core.plan.ConverterRef("dev.kmapx.codegen.instantToIso"),
                    safeCall = true,
                ),
            ),
        )
        assertTrue(file.content.contains("createdAt = createdAt?.let { instantToIso(it) }"), file.content)
    }

    // ── Converters calificados @UseConverter ──────────────────────────

    @Test
    fun `qualified converter emite Object convert - snapshot`() {
        val file = emitter.emit(
            converterPlan(
                dev.kmapx.core.plan.ValueSource.ViaQualifiedConverter(
                    dev.kmapx.core.plan.ref("createdAt"),
                    converterObject = "dev.kmapx.codegen.ShortDate",
                ),
            ),
        )
        assertTrue(file.content.contains("createdAt = ShortDate.convert(createdAt)"), file.content)
        Snapshots.assertMatches("EventSrc_viaQualifiedConverter", file.content)
    }

    @Test
    fun `qualified converter de otro paquete importa el object (refactor-safe)`() {
        val file = emitter.emit(
            converterPlan(
                dev.kmapx.core.plan.ValueSource.ViaQualifiedConverter(
                    dev.kmapx.core.plan.ref("createdAt"),
                    converterObject = "dev.kmapx.formats.ShortDate",
                ),
            ),
        )
        assertTrue(file.content.contains("import dev.kmapx.formats.ShortDate"), file.content)
        assertTrue(file.content.contains("createdAt = ShortDate.convert(createdAt)"), file.content)
    }

    @Test
    fun `qualified converter con fuente nullable usa referencia de metodo`() {
        val file = emitter.emit(
            converterPlan(
                dev.kmapx.core.plan.ValueSource.ViaQualifiedConverter(
                    dev.kmapx.core.plan.ref("createdAt"),
                    converterObject = "dev.kmapx.codegen.ShortDate",
                    safeCall = true,
                ),
            ),
        )
        assertTrue(file.content.contains("createdAt = createdAt?.let(ShortDate::convert)"), file.content)
    }

    // ── Omisión condicional ──────────────────────────────────

    @Test
    fun `un campo omisible bifurca con if (N=1) - snapshot`() {
        val plan = engine.resolve(
            mclassOf<ProfileSrc>(), mclassOf<ProfileDto>(),
            Emission.ExtensionFunction("toProfileDto"),
            nullPolicies = listOf(dev.kmapx.core.engine.NullPolicy.TARGET_DEFAULT),
        )
        assertTrue(plan.valid, plan.diagnostics.joinToString { it.render() })
        val file = emitter.emit(plan)
        assertTrue(file.content.contains("if (nickname != null)"), file.content)
        assertTrue(file.content.contains("else"), file.content)
        Snapshots.assertMatches("ProfileSrc_targetDefault_N1", file.content)
    }

    @Test
    fun `dos campos omisibles bifurcan con when de 4 ramas (N=2) - snapshot`() {
        val plan = engine.resolve(
            mclassOf<CardSrc>(), mclassOf<CardDto>(),
            Emission.ExtensionFunction("toCardDto"),
            nullPolicies = listOf(dev.kmapx.core.engine.NullPolicy.TARGET_DEFAULT),
        )
        assertTrue(plan.valid, plan.diagnostics.joinToString { it.render() })
        val file = emitter.emit(plan)
        assertTrue(file.content.contains("when {"), file.content)
        assertTrue(file.content.contains("nickname != null && bio != null ->"), file.content)
        assertTrue(file.content.contains("else ->"), file.content)
        Snapshots.assertMatches("CardSrc_targetDefault_N2", file.content)
    }

    // ── Sealed paralelas ───────────────────────────────────────────────

    @Test
    fun `sealed dispatch emite when sin else y sub-funciones nombradas - snapshot`() {
        val plan = engine.resolve(
            mclassOf<Payment>(), mclassOf<PaymentDto>(),
            Emission.ExtensionFunction("toPaymentDto"),
        )
        assertTrue(plan.valid, plan.diagnostics.joinToString { it.render() })
        val file = emitter.emit(plan)

        // El pitch: exhaustivo por el compilador — la palabra `else` NO aparece.
        assertTrue(!file.content.contains("else"), file.content)
        assertTrue(file.content.contains("when (this)"), file.content)
        // Rama con sub-plan → función nombrada propia; object ↔ object → referencia directa:
        assertTrue(file.content.contains("is Payment.Approved -> toApproved()"), file.content)
        assertTrue(file.content.contains("is Payment.Pending -> PaymentDto.Pending"), file.content)
        assertTrue(file.content.contains("public fun Payment.Approved.toApproved(): PaymentDto.Approved"), file.content)
        Snapshots.assertMatches("Payment_sealedDispatch", file.content)
    }

    // ── PATCH vía copy() — cuerpo PatchApplication de un @Mapper ──

    private fun patchPlan(afterFunction: String? = null): dev.kmapx.core.plan.MapperImplPlan {
        val resolution = engine.resolvePatch(mclassOf<PatchCustomer>(), mclassOf<PatchCustomerDto>())
        assertTrue(resolution.diagnostics.isEmpty())
        val targetParam = dev.kmapx.core.plan.MParam("target", mclassOf<PatchCustomer>().type)
        val patchParam = dev.kmapx.core.plan.MParam("patch", mclassOf<PatchCustomerDto>().type)
        return dev.kmapx.core.plan.MapperImplPlan(
            interfaceQualifiedName = "dev.kmapx.codegen.CustomerPatcher",
            componentModel = Emission.Component.NONE,
            methods = listOf(
                dev.kmapx.core.plan.MapperMethod(
                    name = "apply",
                    parameters = listOf(targetParam, patchParam),
                    returns = mclassOf<PatchCustomer>().type,
                    body = dev.kmapx.core.plan.MethodBody.PatchApplication(
                        targetParam = targetParam,
                        patchParam = patchParam,
                        fields = resolution.fields,
                    ),
                    afterFunction = afterFunction,
                ),
            ),
        )
    }

    @Test
    fun `patch emite copy con fallback al target - Plan 4 - snapshot`() {
        val file = emitter.emitMapper(patchPlan())
        assertTrue(file.content.contains("public object CustomerPatcherImpl : CustomerPatcher"), file.content)
        assertTrue(file.content.contains("name = patch.name ?: target.name"), file.content)
        assertTrue(file.content.contains("nickname = patch.nickname ?: target.nickname"), file.content)
        Snapshots.assertMatches("CustomerPatcher_plan4", file.content)
    }

    @Test
    fun `patch con afterApply envuelve el copy y usa su retorno`() {
        val file = emitter.emitMapper(patchPlan(afterFunction = "afterApply"))
        assertTrue(file.content.contains("afterApply(target, patch, target.copy("), file.content)
    }

    // ── Enums ──────────────────────────────────────────────────────────

    @Test
    fun `enum dispatch emite when por igualdad sin else - snapshot`() {
        val plan = engine.resolve(
            mclassOf<Priority>(), mclassOf<PriorityDto>(),
            Emission.ExtensionFunction("toPriorityDto"),
        )
        assertTrue(plan.valid, plan.diagnostics.joinToString { it.render() })
        val file = emitter.emit(plan)
        assertTrue(!file.content.contains("else"), file.content)
        assertTrue(file.content.contains("Priority.LOW -> PriorityDto.LOW"), file.content)
        Snapshots.assertMatches("Priority_enumDispatch", file.content)
    }

    // ── Map, arrays, Result ────────────────────────────────────────────

    @Test
    fun `MapEntries emite el idiomatico exacto segun que lado cambia - snapshot`() {
        fun plan(value: dev.kmapx.core.plan.ValueSource) = collectionPlan(value, "toBoxDtoMap")
        val soloValor = emitter.emit(
            plan(
                dev.kmapx.core.plan.ValueSource.MapEntries(
                    dev.kmapx.core.plan.ref("items"),
                    key = null,
                    value = dev.kmapx.core.plan.ValueSource.ViaMapper(
                        dev.kmapx.core.plan.ref("v"),
                        dev.kmapx.core.plan.MapperRef.GeneratedExtension("dev.kmapx.codegen.toAddressDto"),
                    ),
                ),
            ),
        )
        assertTrue(soloValor.content.contains("items.mapValues { (_, v) -> v.toAddressDto() }"), soloValor.content)
        Snapshots.assertMatches("BoxSrc_mapValues", soloValor.content)

        val ambos = emitter.emit(
            plan(
                dev.kmapx.core.plan.ValueSource.MapEntries(
                    dev.kmapx.core.plan.ref("items"),
                    key = dev.kmapx.core.plan.ValueSource.UnwrapValueClass(dev.kmapx.core.plan.ref("k")),
                    value = dev.kmapx.core.plan.ValueSource.ViaMapper(
                        dev.kmapx.core.plan.ref("v"),
                        dev.kmapx.core.plan.MapperRef.GeneratedExtension("dev.kmapx.codegen.toAddressDto"),
                    ),
                ),
            ),
        )
        assertTrue(
            ambos.content.contains("buildMap { for ((k, v) in items) put(k.value, v.toAddressDto()) }"),
            ambos.content,
        )
    }

    @Test
    fun `Array emite map-toTypedArray y Result emite map`() {
        val viaMapper = dev.kmapx.core.plan.ValueSource.ViaMapper(
            dev.kmapx.core.plan.ref("it"),
            dev.kmapx.core.plan.MapperRef.GeneratedExtension("dev.kmapx.codegen.toAddressDto"),
        )
        val array = emitter.emit(
            collectionPlan(
                dev.kmapx.core.plan.ValueSource.MapElements(
                    dev.kmapx.core.plan.ref("items"), viaMapper,
                    into = dev.kmapx.core.model.TypeKind.COLLECTION_ARRAY,
                ),
                "toBoxDtoArray",
            ),
        )
        assertTrue(array.content.contains("items.map { it.toAddressDto() }.toTypedArray()"), array.content)

        val result = emitter.emit(
            collectionPlan(
                dev.kmapx.core.plan.ValueSource.MapElements(
                    dev.kmapx.core.plan.ref("items"), viaMapper,
                    into = dev.kmapx.core.model.TypeKind.RESULT,
                ),
                "toBoxDtoResult",
            ),
        )
        assertTrue(result.content.contains("items.map { it.toAddressDto() }"), result.content)
        assertTrue(!result.content.contains("toTypedArray"), result.content)
    }

    @Test
    fun `emitir un plan invalido es un error de programacion, no un output corrupto`() {
        data class Bad(val nonexistent: String)
        val plan = engine.resolve(mclassOf<Order>(), mclassOf<Bad>(), Emission.ExtensionFunction("toBad"))
        assertTrue(!plan.valid)
        val ex = kotlin.runCatching { emitter.emit(plan) }.exceptionOrNull()
        assertTrue(ex is IllegalArgumentException)
    }

    @Test
    fun `emitir planes de sources distintos en un archivo es un error de programacion`() {
        val a = engine.resolve(mclassOf<Order>(), mclassOf<OrderDto>(), Emission.ExtensionFunction("toOrderDto"))
        val b = engine.resolve(mclassOf<OrderDto>(), mclassOf<OrderDtoReversed>(), Emission.ExtensionFunction("toReversed"))
        assertTrue(a.valid && b.valid)
        val ex = kotlin.runCatching { emitter.emit(listOf(a, b)) }.exceptionOrNull()
        assertTrue(ex is IllegalArgumentException)
    }
}
