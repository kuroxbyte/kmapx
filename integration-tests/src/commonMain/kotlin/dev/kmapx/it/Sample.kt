package dev.kmapx.it

import dev.kmapx.annotations.embedded.BiMapTo
import dev.kmapx.annotations.Converter
import dev.kmapx.annotations.MapField
import dev.kmapx.annotations.embedded.MapTo
import dev.kmapx.annotations.contract.Mapper
import dev.kmapx.annotations.OnNull
import dev.kmapx.runtime.Converts
import kotlin.jvm.JvmInline

data class PersonDto(val name: String, val age: Int)

data class PersonSummary(val name: String)

@MapTo(PersonDto::class)
@MapTo(PersonSummary::class, name = "asSummary")
data class Person(val name: String, val age: Int)

/** Clase regular con `var` de cuerpo — se asigna post-construcción vía `.also`. */
class TaskEntity(val id: String) {
    var status: String = "NEW"
}

@MapTo(TaskEntity::class)
data class TaskSrc(val id: String, val status: String)

/** Modo interfaz — `PersonMapperImpl` (object) delega en `Person.toPersonDto()`. */
@Mapper
interface PersonMapper {
    fun toDto(p: Person): PersonDto
}

/** Estrategias `T? -> T` en runtime. */
data class ProfileDto(
    @MapField(onNull = OnNull.LITERAL, default = "N/A") val nickname: String,
    @MapField(onNull = OnNull.THROW) val email: String,
)

@MapTo(ProfileDto::class)
data class Profile(val nickname: String?, val email: String?)

/** Colecciones elemento a elemento, una pasada. */
data class AddressDto(val city: String)

@MapTo(AddressDto::class)
data class Address(val city: String)

data class DirectoryDto(val addresses: List<AddressDto>)

@MapTo(DirectoryDto::class)
data class Directory(val addresses: List<Address>)

/** Converter del usuario — función Kotlin normal, refactor-safe (KMP-friendly: sin java.time). */
@Converter
fun centsToAmount(cents: Long): String = "${cents / 100}." + (cents % 100).toString().padStart(2, '0')

data class ReceiptDto(val amount: String)

@MapTo(ReceiptDto::class)
data class Receipt(val amount: Long)

/** Renombrado plano — el caso cotidiano número uno. */
data class UserDto(@MapField(from = "firstname") val name: String)

@MapTo(UserDto::class)
data class User(val firstname: String)

/** Default del target como estrategia — política TARGET_DEFAULT de mapeo. */
data class HandleDto(val name: String, val nickname: String = "N/A")

@MapTo(HandleDto::class, onNull = OnNull.TARGET_DEFAULT)
data class Handle(val name: String, val nickname: String?)

/** Jerarquías sealed paralelas — when exhaustivo sin else, round por subtipo. */
sealed interface OrderEventDto {
    data class Placed(val total: Long) : OrderEventDto
    data object Cancelled : OrderEventDto
}

@MapTo(OrderEventDto::class)
sealed interface OrderEvent {
    data class Placed(val total: Long) : OrderEvent
    data object Cancelled : OrderEvent
}

/** Value classes transparentes — el init validador SE EJECUTA en el wrap (documentado). */
@JvmInline
value class Email(val value: String) {
    init { require("@" in value) { "invalid email: $value" } }
}

data class ContactDto(val email: Email, val alias: String?)

@MapTo(ContactDto::class)
data class Contact(val email: String, val alias: Alias?)

@JvmInline
value class Alias(val value: String)

/** PATCH inmutable — null = no tocar; colecciones = reemplazo completo. */
data class Article(val title: String, val tags: List<String>, val views: Int)
data class ArticlePatch(val title: String?, val tags: List<String>?)

@Mapper
interface ArticlePatcher {
    fun apply(target: Article, patch: ArticlePatch): Article
}

/** La estrategia se aplica en la post-asignación de una var de cuerpo (pipeline real). */
class Ticket(val id: String) {
    @MapField(onNull = OnNull.LITERAL, default = "OPEN") var state: String = ""
}

@MapTo(Ticket::class)
data class TicketSrc(val id: String, val state: String?)

/** Enums paralelos — when exhaustivo por igualdad, round por entry. */
enum class LevelDto { LOW, HIGH }

@MapTo(LevelDto::class)
enum class Level { LOW, HIGH }

/** Anidado top-level por referencia al mapper declarado. */
data class ShipmentDto(val code: String, val destination: AddressDto)

@MapTo(ShipmentDto::class)
data class Shipment(val code: String, val destination: Address)

/** Map/Array/Result — una pasada, por target. */
data class InventoryDto(
    val stock: Map<String, AddressDto>,
    val checkpoints: Array<AddressDto>,
    val lastSync: Result<AddressDto>,
)

@MapTo(InventoryDto::class)
data class Inventory(
    val stock: Map<String, Address>,
    val checkpoints: Array<Address>,
    val lastSync: Result<Address>,
)

/** Bidireccional — round-trip validado en compile-time, verificado en runtime. */
@JvmInline
value class PlateId(val value: String)

data class VehicleDto(@MapField(from = "plate") val plateNumber: String, val year: Int)

@BiMapTo(VehicleDto::class)
data class Vehicle(val plate: PlateId, val year: Int)

/** Rutas anidadas — aplanar con nulabilidad por segmento. */
data class Branch(val name: String, val address: Address?)

data class BranchDto(
    @MapField(from = "address.city") val city: String?,
    val name: String,
)

@MapTo(BranchDto::class)
data class BranchSrc(val name: String, val address: Address?)

/** Converters calificados — elección POR CAMPO (modo A, anotación sobre la propiedad). */
object Hex : Converts<Int, String> { override fun convert(value: Int): String = value.toString(16) }
object Dec : Converts<Int, String> { override fun convert(value: Int): String = value.toString() }

data class CodeDto(
    @MapField(converter = Hex::class) val primary: String,
    @MapField(converter = Dec::class) val secondary: String,
)

@MapTo(CodeDto::class)
data class Code(val primary: Int, val secondary: Int)

/**
 * Dominio 100% limpio — Money y MoneyView SIN una sola anotación kmapx;
 * el renombre y el converter calificado viven en el método del @Mapper (modo B).
 */
data class Money(val cents: Int)
data class MoneyView(val display: String)

@Mapper
interface MoneyMapper {
    @MapField(target = "display", from = "cents", converter = Dec::class)
    fun toView(m: Money): MoneyView
}
