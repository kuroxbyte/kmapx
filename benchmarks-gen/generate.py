#!/usr/bin/env python3
"""Genera N pares de modelo idénticos para el módulo KSP (kmapx) y el kapt (MapStruct),
para medir el TIEMPO DE GENERACIÓN de cada uno sobre el mismo trabajo."""
import pathlib, sys

N = int(sys.argv[1]) if len(sys.argv) > 1 else 40
ROOT = pathlib.Path(__file__).parent

def kmapx_pair(i):
    return f'''
@JvmInline value class OrderId{i}(val value: String)
@MapTo(AddressDto{i}::class)
data class Address{i}(val street: String, val city: String, val zip: String)
data class AddressDto{i}(val street: String, val city: String, val zip: String)

@MapTo(CustomerDto{i}::class)
data class Customer{i}(val name: String, val email: String, val address: Address{i})
data class CustomerDto{i}(val name: String, val email: String, val address: AddressDto{i})

@MapTo(ItemDto{i}::class)
data class Item{i}(val sku: String, val name: String, val qty: Int, val priceCents: Long)
data class ItemDto{i}(val sku: String, val name: String, val qty: Int,
    @MapField(from = "priceCents", converter = Price{i}::class) val price: String)
object Price{i} : Converts<Long, String> {{ override fun convert(value: Long) = value.toString() }}

@MapTo(OrderDto{i}::class)
data class Order{i}(val id: OrderId{i}, val customer: Customer{i}, val items: List<Item{i}>, val createdAt: Instant)
data class OrderDto{i}(val id: String, val customer: CustomerDto{i}, val items: List<ItemDto{i}>,
    @MapField(converter = Iso{i}::class) val createdAt: String)
object Iso{i} : Converts<Instant, String> {{ override fun convert(value: Instant) = value.toString() }}
'''

def kapt_pair(i):
    return f'''
data class Address{i}(val street: String, val city: String, val zip: String)
data class AddressDto{i}(val street: String, val city: String, val zip: String)
data class Customer{i}(val name: String, val email: String, val address: Address{i})
data class CustomerDto{i}(val name: String, val email: String, val address: AddressDto{i})
data class Item{i}(val sku: String, val name: String, val qty: Int, val priceCents: Long)
data class ItemDto{i}(val sku: String, val name: String, val qty: Int, val price: String)
data class Order{i}(val id: String, val customer: Customer{i}, val items: List<Item{i}>, val createdAt: Instant)
data class OrderDto{i}(val id: String, val customer: CustomerDto{i}, val items: List<ItemDto{i}>, val createdAt: String)

@Mapper
interface OrderMapper{i} {{
    @Mapping(target = "createdAt", expression = "java(gen.KaptModelKt.iso{i}(order.getCreatedAt()))")
    fun toDto(order: Order{i}): OrderDto{i}
    fun toDto(c: Customer{i}): CustomerDto{i}
    fun toDto(a: Address{i}): AddressDto{i}
    @Mapping(target = "price", expression = "java(gen.KaptModelKt.price{i}(item.getPriceCents()))")
    fun toDto(item: Item{i}): ItemDto{i}
}}
fun iso{i}(v: Instant): String = v.toString()
fun price{i}(c: Long): String = c.toString()
'''

ksp = ["package gen",
       "import dev.kmapx.annotations.MapField",
       "import dev.kmapx.annotations.embedded.MapTo",
       "import dev.kmapx.runtime.Converts",
       "import java.time.Instant", ""]
ksp += [kmapx_pair(i) for i in range(N)]
(ROOT / "ksp/src/main/kotlin/gen/Model.kt").write_text("\n".join(ksp))

kapt = ["package gen",
        "import org.mapstruct.Mapper",
        "import org.mapstruct.Mapping",
        "import java.time.Instant", ""]
kapt += [kapt_pair(i) for i in range(N)]
(ROOT / "kapt/src/main/kotlin/gen/KaptModel.kt").write_text("\n".join(kapt))
print(f"generado N={N} pares en ambos módulos")
