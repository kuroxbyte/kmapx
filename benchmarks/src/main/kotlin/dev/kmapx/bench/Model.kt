package dev.kmapx.bench

import dev.kmapx.annotations.Converter
import dev.kmapx.annotations.MapField
import dev.kmapx.annotations.embedded.MapTo
import dev.kmapx.runtime.Converts
import java.time.Instant

// ── Dominio: un caso representativo (value class, anidado, colección, enum, converter) ──

@JvmInline value class OrderId(val value: String)

@MapTo(StatusDto::class)
enum class Status { NEW, PAID, SHIPPED, CANCELLED }

@MapTo(AddressDto::class)
data class Address(val street: String, val city: String, val zip: String)

@MapTo(CustomerDto::class)
data class Customer(val name: String, val email: String, val address: Address)

@MapTo(ItemDto::class)
data class Item(val sku: String, val name: String, val qty: Int, val priceCents: Long)

@MapTo(OrderDto::class)
data class Order(
    val id: OrderId,
    val customer: Customer,
    val items: List<Item>,
    val status: Status,
    val createdAt: Instant,
)

// ── DTOs (targets) + config por campo ──

@Converter fun instantToIso(v: Instant): String = v.toString()

object PriceUsd : Converts<Long, String> {
    override fun convert(value: Long): String = "$" + (value / 100) + "." + "%02d".format(value % 100)
}

enum class StatusDto { NEW, PAID, SHIPPED, CANCELLED }

data class AddressDto(val street: String, val city: String, val zip: String)
data class CustomerDto(val name: String, val email: String, val address: AddressDto)
data class ItemDto(
    val sku: String,
    val name: String,
    val qty: Int,
    @MapField(from = "priceCents", converter = PriceUsd::class) val price: String,
)
data class OrderDto(
    val id: String,
    val customer: CustomerDto,
    val items: List<ItemDto>,
    val status: StatusDto,
    val createdAt: String,
)
