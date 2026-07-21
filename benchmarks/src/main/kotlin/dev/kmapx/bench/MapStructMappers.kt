package dev.kmapx.bench

import org.mapstruct.Mapper
import org.mapstruct.Mapping
import org.mapstruct.factory.Mappers

/**
 * El MISMO mapeo con MapStruct (processor Java vía kapt). Los casos que MapStruct no resuelve
 * nativo en Kotlin se cubren con métodos helper: value class OrderId, el converter priceCents,
 * e Instant → String. Enums y anidados los empareja MapStruct solo.
 */
@Mapper
interface MapStructOrderMapper {

    @Mapping(target = "id", expression = "java(dev.kmapx.bench.MapStructMappersKt.orderIdOf(order))")
    @Mapping(target = "createdAt", expression = "java(dev.kmapx.bench.MapStructMappersKt.instantIso(order.getCreatedAt()))")
    fun toDto(order: Order): OrderDto

    fun toDto(customer: Customer): CustomerDto
    fun toDto(address: Address): AddressDto

    @Mapping(target = "price", expression = "java(dev.kmapx.bench.MapStructMappersKt.priceUsd(item.getPriceCents()))")
    fun toDto(item: Item): ItemDto

    companion object {
        val INSTANCE: MapStructOrderMapper = Mappers.getMapper(MapStructOrderMapper::class.java)
    }
}

fun orderIdOf(o: Order): String = o.id.value  // el value class OrderId, accedido desde Kotlin
fun instantIso(v: java.time.Instant): String = v.toString()
fun priceUsd(cents: Long): String = "$" + (cents / 100) + "." + "%02d".format(cents % 100)
