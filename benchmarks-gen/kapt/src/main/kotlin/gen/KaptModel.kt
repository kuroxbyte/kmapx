package gen
import org.mapstruct.Mapper
import org.mapstruct.Mapping
import java.time.Instant


data class Address0(val street: String, val city: String, val zip: String)
data class AddressDto0(val street: String, val city: String, val zip: String)
data class Customer0(val name: String, val email: String, val address: Address0)
data class CustomerDto0(val name: String, val email: String, val address: AddressDto0)
data class Item0(val sku: String, val name: String, val qty: Int, val priceCents: Long)
data class ItemDto0(val sku: String, val name: String, val qty: Int, val price: String)
data class Order0(val id: String, val customer: Customer0, val items: List<Item0>, val createdAt: Instant)
data class OrderDto0(val id: String, val customer: CustomerDto0, val items: List<ItemDto0>, val createdAt: String)

@Mapper
interface OrderMapper0 {
    @Mapping(target = "createdAt", expression = "java(gen.KaptModelKt.iso0(order.getCreatedAt()))")
    fun toDto(order: Order0): OrderDto0
    fun toDto(c: Customer0): CustomerDto0
    fun toDto(a: Address0): AddressDto0
    @Mapping(target = "price", expression = "java(gen.KaptModelKt.price0(item.getPriceCents()))")
    fun toDto(item: Item0): ItemDto0
}
fun iso0(v: Instant): String = v.toString()
fun price0(c: Long): String = c.toString()


data class Address1(val street: String, val city: String, val zip: String)
data class AddressDto1(val street: String, val city: String, val zip: String)
data class Customer1(val name: String, val email: String, val address: Address1)
data class CustomerDto1(val name: String, val email: String, val address: AddressDto1)
data class Item1(val sku: String, val name: String, val qty: Int, val priceCents: Long)
data class ItemDto1(val sku: String, val name: String, val qty: Int, val price: String)
data class Order1(val id: String, val customer: Customer1, val items: List<Item1>, val createdAt: Instant)
data class OrderDto1(val id: String, val customer: CustomerDto1, val items: List<ItemDto1>, val createdAt: String)

@Mapper
interface OrderMapper1 {
    @Mapping(target = "createdAt", expression = "java(gen.KaptModelKt.iso1(order.getCreatedAt()))")
    fun toDto(order: Order1): OrderDto1
    fun toDto(c: Customer1): CustomerDto1
    fun toDto(a: Address1): AddressDto1
    @Mapping(target = "price", expression = "java(gen.KaptModelKt.price1(item.getPriceCents()))")
    fun toDto(item: Item1): ItemDto1
}
fun iso1(v: Instant): String = v.toString()
fun price1(c: Long): String = c.toString()


data class Address2(val street: String, val city: String, val zip: String)
data class AddressDto2(val street: String, val city: String, val zip: String)
data class Customer2(val name: String, val email: String, val address: Address2)
data class CustomerDto2(val name: String, val email: String, val address: AddressDto2)
data class Item2(val sku: String, val name: String, val qty: Int, val priceCents: Long)
data class ItemDto2(val sku: String, val name: String, val qty: Int, val price: String)
data class Order2(val id: String, val customer: Customer2, val items: List<Item2>, val createdAt: Instant)
data class OrderDto2(val id: String, val customer: CustomerDto2, val items: List<ItemDto2>, val createdAt: String)

@Mapper
interface OrderMapper2 {
    @Mapping(target = "createdAt", expression = "java(gen.KaptModelKt.iso2(order.getCreatedAt()))")
    fun toDto(order: Order2): OrderDto2
    fun toDto(c: Customer2): CustomerDto2
    fun toDto(a: Address2): AddressDto2
    @Mapping(target = "price", expression = "java(gen.KaptModelKt.price2(item.getPriceCents()))")
    fun toDto(item: Item2): ItemDto2
}
fun iso2(v: Instant): String = v.toString()
fun price2(c: Long): String = c.toString()


data class Address3(val street: String, val city: String, val zip: String)
data class AddressDto3(val street: String, val city: String, val zip: String)
data class Customer3(val name: String, val email: String, val address: Address3)
data class CustomerDto3(val name: String, val email: String, val address: AddressDto3)
data class Item3(val sku: String, val name: String, val qty: Int, val priceCents: Long)
data class ItemDto3(val sku: String, val name: String, val qty: Int, val price: String)
data class Order3(val id: String, val customer: Customer3, val items: List<Item3>, val createdAt: Instant)
data class OrderDto3(val id: String, val customer: CustomerDto3, val items: List<ItemDto3>, val createdAt: String)

@Mapper
interface OrderMapper3 {
    @Mapping(target = "createdAt", expression = "java(gen.KaptModelKt.iso3(order.getCreatedAt()))")
    fun toDto(order: Order3): OrderDto3
    fun toDto(c: Customer3): CustomerDto3
    fun toDto(a: Address3): AddressDto3
    @Mapping(target = "price", expression = "java(gen.KaptModelKt.price3(item.getPriceCents()))")
    fun toDto(item: Item3): ItemDto3
}
fun iso3(v: Instant): String = v.toString()
fun price3(c: Long): String = c.toString()


data class Address4(val street: String, val city: String, val zip: String)
data class AddressDto4(val street: String, val city: String, val zip: String)
data class Customer4(val name: String, val email: String, val address: Address4)
data class CustomerDto4(val name: String, val email: String, val address: AddressDto4)
data class Item4(val sku: String, val name: String, val qty: Int, val priceCents: Long)
data class ItemDto4(val sku: String, val name: String, val qty: Int, val price: String)
data class Order4(val id: String, val customer: Customer4, val items: List<Item4>, val createdAt: Instant)
data class OrderDto4(val id: String, val customer: CustomerDto4, val items: List<ItemDto4>, val createdAt: String)

@Mapper
interface OrderMapper4 {
    @Mapping(target = "createdAt", expression = "java(gen.KaptModelKt.iso4(order.getCreatedAt()))")
    fun toDto(order: Order4): OrderDto4
    fun toDto(c: Customer4): CustomerDto4
    fun toDto(a: Address4): AddressDto4
    @Mapping(target = "price", expression = "java(gen.KaptModelKt.price4(item.getPriceCents()))")
    fun toDto(item: Item4): ItemDto4
}
fun iso4(v: Instant): String = v.toString()
fun price4(c: Long): String = c.toString()


data class Address5(val street: String, val city: String, val zip: String)
data class AddressDto5(val street: String, val city: String, val zip: String)
data class Customer5(val name: String, val email: String, val address: Address5)
data class CustomerDto5(val name: String, val email: String, val address: AddressDto5)
data class Item5(val sku: String, val name: String, val qty: Int, val priceCents: Long)
data class ItemDto5(val sku: String, val name: String, val qty: Int, val price: String)
data class Order5(val id: String, val customer: Customer5, val items: List<Item5>, val createdAt: Instant)
data class OrderDto5(val id: String, val customer: CustomerDto5, val items: List<ItemDto5>, val createdAt: String)

@Mapper
interface OrderMapper5 {
    @Mapping(target = "createdAt", expression = "java(gen.KaptModelKt.iso5(order.getCreatedAt()))")
    fun toDto(order: Order5): OrderDto5
    fun toDto(c: Customer5): CustomerDto5
    fun toDto(a: Address5): AddressDto5
    @Mapping(target = "price", expression = "java(gen.KaptModelKt.price5(item.getPriceCents()))")
    fun toDto(item: Item5): ItemDto5
}
fun iso5(v: Instant): String = v.toString()
fun price5(c: Long): String = c.toString()


data class Address6(val street: String, val city: String, val zip: String)
data class AddressDto6(val street: String, val city: String, val zip: String)
data class Customer6(val name: String, val email: String, val address: Address6)
data class CustomerDto6(val name: String, val email: String, val address: AddressDto6)
data class Item6(val sku: String, val name: String, val qty: Int, val priceCents: Long)
data class ItemDto6(val sku: String, val name: String, val qty: Int, val price: String)
data class Order6(val id: String, val customer: Customer6, val items: List<Item6>, val createdAt: Instant)
data class OrderDto6(val id: String, val customer: CustomerDto6, val items: List<ItemDto6>, val createdAt: String)

@Mapper
interface OrderMapper6 {
    @Mapping(target = "createdAt", expression = "java(gen.KaptModelKt.iso6(order.getCreatedAt()))")
    fun toDto(order: Order6): OrderDto6
    fun toDto(c: Customer6): CustomerDto6
    fun toDto(a: Address6): AddressDto6
    @Mapping(target = "price", expression = "java(gen.KaptModelKt.price6(item.getPriceCents()))")
    fun toDto(item: Item6): ItemDto6
}
fun iso6(v: Instant): String = v.toString()
fun price6(c: Long): String = c.toString()


data class Address7(val street: String, val city: String, val zip: String)
data class AddressDto7(val street: String, val city: String, val zip: String)
data class Customer7(val name: String, val email: String, val address: Address7)
data class CustomerDto7(val name: String, val email: String, val address: AddressDto7)
data class Item7(val sku: String, val name: String, val qty: Int, val priceCents: Long)
data class ItemDto7(val sku: String, val name: String, val qty: Int, val price: String)
data class Order7(val id: String, val customer: Customer7, val items: List<Item7>, val createdAt: Instant)
data class OrderDto7(val id: String, val customer: CustomerDto7, val items: List<ItemDto7>, val createdAt: String)

@Mapper
interface OrderMapper7 {
    @Mapping(target = "createdAt", expression = "java(gen.KaptModelKt.iso7(order.getCreatedAt()))")
    fun toDto(order: Order7): OrderDto7
    fun toDto(c: Customer7): CustomerDto7
    fun toDto(a: Address7): AddressDto7
    @Mapping(target = "price", expression = "java(gen.KaptModelKt.price7(item.getPriceCents()))")
    fun toDto(item: Item7): ItemDto7
}
fun iso7(v: Instant): String = v.toString()
fun price7(c: Long): String = c.toString()


data class Address8(val street: String, val city: String, val zip: String)
data class AddressDto8(val street: String, val city: String, val zip: String)
data class Customer8(val name: String, val email: String, val address: Address8)
data class CustomerDto8(val name: String, val email: String, val address: AddressDto8)
data class Item8(val sku: String, val name: String, val qty: Int, val priceCents: Long)
data class ItemDto8(val sku: String, val name: String, val qty: Int, val price: String)
data class Order8(val id: String, val customer: Customer8, val items: List<Item8>, val createdAt: Instant)
data class OrderDto8(val id: String, val customer: CustomerDto8, val items: List<ItemDto8>, val createdAt: String)

@Mapper
interface OrderMapper8 {
    @Mapping(target = "createdAt", expression = "java(gen.KaptModelKt.iso8(order.getCreatedAt()))")
    fun toDto(order: Order8): OrderDto8
    fun toDto(c: Customer8): CustomerDto8
    fun toDto(a: Address8): AddressDto8
    @Mapping(target = "price", expression = "java(gen.KaptModelKt.price8(item.getPriceCents()))")
    fun toDto(item: Item8): ItemDto8
}
fun iso8(v: Instant): String = v.toString()
fun price8(c: Long): String = c.toString()


data class Address9(val street: String, val city: String, val zip: String)
data class AddressDto9(val street: String, val city: String, val zip: String)
data class Customer9(val name: String, val email: String, val address: Address9)
data class CustomerDto9(val name: String, val email: String, val address: AddressDto9)
data class Item9(val sku: String, val name: String, val qty: Int, val priceCents: Long)
data class ItemDto9(val sku: String, val name: String, val qty: Int, val price: String)
data class Order9(val id: String, val customer: Customer9, val items: List<Item9>, val createdAt: Instant)
data class OrderDto9(val id: String, val customer: CustomerDto9, val items: List<ItemDto9>, val createdAt: String)

@Mapper
interface OrderMapper9 {
    @Mapping(target = "createdAt", expression = "java(gen.KaptModelKt.iso9(order.getCreatedAt()))")
    fun toDto(order: Order9): OrderDto9
    fun toDto(c: Customer9): CustomerDto9
    fun toDto(a: Address9): AddressDto9
    @Mapping(target = "price", expression = "java(gen.KaptModelKt.price9(item.getPriceCents()))")
    fun toDto(item: Item9): ItemDto9
}
fun iso9(v: Instant): String = v.toString()
fun price9(c: Long): String = c.toString()


data class Address10(val street: String, val city: String, val zip: String)
data class AddressDto10(val street: String, val city: String, val zip: String)
data class Customer10(val name: String, val email: String, val address: Address10)
data class CustomerDto10(val name: String, val email: String, val address: AddressDto10)
data class Item10(val sku: String, val name: String, val qty: Int, val priceCents: Long)
data class ItemDto10(val sku: String, val name: String, val qty: Int, val price: String)
data class Order10(val id: String, val customer: Customer10, val items: List<Item10>, val createdAt: Instant)
data class OrderDto10(val id: String, val customer: CustomerDto10, val items: List<ItemDto10>, val createdAt: String)

@Mapper
interface OrderMapper10 {
    @Mapping(target = "createdAt", expression = "java(gen.KaptModelKt.iso10(order.getCreatedAt()))")
    fun toDto(order: Order10): OrderDto10
    fun toDto(c: Customer10): CustomerDto10
    fun toDto(a: Address10): AddressDto10
    @Mapping(target = "price", expression = "java(gen.KaptModelKt.price10(item.getPriceCents()))")
    fun toDto(item: Item10): ItemDto10
}
fun iso10(v: Instant): String = v.toString()
fun price10(c: Long): String = c.toString()


data class Address11(val street: String, val city: String, val zip: String)
data class AddressDto11(val street: String, val city: String, val zip: String)
data class Customer11(val name: String, val email: String, val address: Address11)
data class CustomerDto11(val name: String, val email: String, val address: AddressDto11)
data class Item11(val sku: String, val name: String, val qty: Int, val priceCents: Long)
data class ItemDto11(val sku: String, val name: String, val qty: Int, val price: String)
data class Order11(val id: String, val customer: Customer11, val items: List<Item11>, val createdAt: Instant)
data class OrderDto11(val id: String, val customer: CustomerDto11, val items: List<ItemDto11>, val createdAt: String)

@Mapper
interface OrderMapper11 {
    @Mapping(target = "createdAt", expression = "java(gen.KaptModelKt.iso11(order.getCreatedAt()))")
    fun toDto(order: Order11): OrderDto11
    fun toDto(c: Customer11): CustomerDto11
    fun toDto(a: Address11): AddressDto11
    @Mapping(target = "price", expression = "java(gen.KaptModelKt.price11(item.getPriceCents()))")
    fun toDto(item: Item11): ItemDto11
}
fun iso11(v: Instant): String = v.toString()
fun price11(c: Long): String = c.toString()


data class Address12(val street: String, val city: String, val zip: String)
data class AddressDto12(val street: String, val city: String, val zip: String)
data class Customer12(val name: String, val email: String, val address: Address12)
data class CustomerDto12(val name: String, val email: String, val address: AddressDto12)
data class Item12(val sku: String, val name: String, val qty: Int, val priceCents: Long)
data class ItemDto12(val sku: String, val name: String, val qty: Int, val price: String)
data class Order12(val id: String, val customer: Customer12, val items: List<Item12>, val createdAt: Instant)
data class OrderDto12(val id: String, val customer: CustomerDto12, val items: List<ItemDto12>, val createdAt: String)

@Mapper
interface OrderMapper12 {
    @Mapping(target = "createdAt", expression = "java(gen.KaptModelKt.iso12(order.getCreatedAt()))")
    fun toDto(order: Order12): OrderDto12
    fun toDto(c: Customer12): CustomerDto12
    fun toDto(a: Address12): AddressDto12
    @Mapping(target = "price", expression = "java(gen.KaptModelKt.price12(item.getPriceCents()))")
    fun toDto(item: Item12): ItemDto12
}
fun iso12(v: Instant): String = v.toString()
fun price12(c: Long): String = c.toString()


data class Address13(val street: String, val city: String, val zip: String)
data class AddressDto13(val street: String, val city: String, val zip: String)
data class Customer13(val name: String, val email: String, val address: Address13)
data class CustomerDto13(val name: String, val email: String, val address: AddressDto13)
data class Item13(val sku: String, val name: String, val qty: Int, val priceCents: Long)
data class ItemDto13(val sku: String, val name: String, val qty: Int, val price: String)
data class Order13(val id: String, val customer: Customer13, val items: List<Item13>, val createdAt: Instant)
data class OrderDto13(val id: String, val customer: CustomerDto13, val items: List<ItemDto13>, val createdAt: String)

@Mapper
interface OrderMapper13 {
    @Mapping(target = "createdAt", expression = "java(gen.KaptModelKt.iso13(order.getCreatedAt()))")
    fun toDto(order: Order13): OrderDto13
    fun toDto(c: Customer13): CustomerDto13
    fun toDto(a: Address13): AddressDto13
    @Mapping(target = "price", expression = "java(gen.KaptModelKt.price13(item.getPriceCents()))")
    fun toDto(item: Item13): ItemDto13
}
fun iso13(v: Instant): String = v.toString()
fun price13(c: Long): String = c.toString()


data class Address14(val street: String, val city: String, val zip: String)
data class AddressDto14(val street: String, val city: String, val zip: String)
data class Customer14(val name: String, val email: String, val address: Address14)
data class CustomerDto14(val name: String, val email: String, val address: AddressDto14)
data class Item14(val sku: String, val name: String, val qty: Int, val priceCents: Long)
data class ItemDto14(val sku: String, val name: String, val qty: Int, val price: String)
data class Order14(val id: String, val customer: Customer14, val items: List<Item14>, val createdAt: Instant)
data class OrderDto14(val id: String, val customer: CustomerDto14, val items: List<ItemDto14>, val createdAt: String)

@Mapper
interface OrderMapper14 {
    @Mapping(target = "createdAt", expression = "java(gen.KaptModelKt.iso14(order.getCreatedAt()))")
    fun toDto(order: Order14): OrderDto14
    fun toDto(c: Customer14): CustomerDto14
    fun toDto(a: Address14): AddressDto14
    @Mapping(target = "price", expression = "java(gen.KaptModelKt.price14(item.getPriceCents()))")
    fun toDto(item: Item14): ItemDto14
}
fun iso14(v: Instant): String = v.toString()
fun price14(c: Long): String = c.toString()


data class Address15(val street: String, val city: String, val zip: String)
data class AddressDto15(val street: String, val city: String, val zip: String)
data class Customer15(val name: String, val email: String, val address: Address15)
data class CustomerDto15(val name: String, val email: String, val address: AddressDto15)
data class Item15(val sku: String, val name: String, val qty: Int, val priceCents: Long)
data class ItemDto15(val sku: String, val name: String, val qty: Int, val price: String)
data class Order15(val id: String, val customer: Customer15, val items: List<Item15>, val createdAt: Instant)
data class OrderDto15(val id: String, val customer: CustomerDto15, val items: List<ItemDto15>, val createdAt: String)

@Mapper
interface OrderMapper15 {
    @Mapping(target = "createdAt", expression = "java(gen.KaptModelKt.iso15(order.getCreatedAt()))")
    fun toDto(order: Order15): OrderDto15
    fun toDto(c: Customer15): CustomerDto15
    fun toDto(a: Address15): AddressDto15
    @Mapping(target = "price", expression = "java(gen.KaptModelKt.price15(item.getPriceCents()))")
    fun toDto(item: Item15): ItemDto15
}
fun iso15(v: Instant): String = v.toString()
fun price15(c: Long): String = c.toString()


data class Address16(val street: String, val city: String, val zip: String)
data class AddressDto16(val street: String, val city: String, val zip: String)
data class Customer16(val name: String, val email: String, val address: Address16)
data class CustomerDto16(val name: String, val email: String, val address: AddressDto16)
data class Item16(val sku: String, val name: String, val qty: Int, val priceCents: Long)
data class ItemDto16(val sku: String, val name: String, val qty: Int, val price: String)
data class Order16(val id: String, val customer: Customer16, val items: List<Item16>, val createdAt: Instant)
data class OrderDto16(val id: String, val customer: CustomerDto16, val items: List<ItemDto16>, val createdAt: String)

@Mapper
interface OrderMapper16 {
    @Mapping(target = "createdAt", expression = "java(gen.KaptModelKt.iso16(order.getCreatedAt()))")
    fun toDto(order: Order16): OrderDto16
    fun toDto(c: Customer16): CustomerDto16
    fun toDto(a: Address16): AddressDto16
    @Mapping(target = "price", expression = "java(gen.KaptModelKt.price16(item.getPriceCents()))")
    fun toDto(item: Item16): ItemDto16
}
fun iso16(v: Instant): String = v.toString()
fun price16(c: Long): String = c.toString()


data class Address17(val street: String, val city: String, val zip: String)
data class AddressDto17(val street: String, val city: String, val zip: String)
data class Customer17(val name: String, val email: String, val address: Address17)
data class CustomerDto17(val name: String, val email: String, val address: AddressDto17)
data class Item17(val sku: String, val name: String, val qty: Int, val priceCents: Long)
data class ItemDto17(val sku: String, val name: String, val qty: Int, val price: String)
data class Order17(val id: String, val customer: Customer17, val items: List<Item17>, val createdAt: Instant)
data class OrderDto17(val id: String, val customer: CustomerDto17, val items: List<ItemDto17>, val createdAt: String)

@Mapper
interface OrderMapper17 {
    @Mapping(target = "createdAt", expression = "java(gen.KaptModelKt.iso17(order.getCreatedAt()))")
    fun toDto(order: Order17): OrderDto17
    fun toDto(c: Customer17): CustomerDto17
    fun toDto(a: Address17): AddressDto17
    @Mapping(target = "price", expression = "java(gen.KaptModelKt.price17(item.getPriceCents()))")
    fun toDto(item: Item17): ItemDto17
}
fun iso17(v: Instant): String = v.toString()
fun price17(c: Long): String = c.toString()


data class Address18(val street: String, val city: String, val zip: String)
data class AddressDto18(val street: String, val city: String, val zip: String)
data class Customer18(val name: String, val email: String, val address: Address18)
data class CustomerDto18(val name: String, val email: String, val address: AddressDto18)
data class Item18(val sku: String, val name: String, val qty: Int, val priceCents: Long)
data class ItemDto18(val sku: String, val name: String, val qty: Int, val price: String)
data class Order18(val id: String, val customer: Customer18, val items: List<Item18>, val createdAt: Instant)
data class OrderDto18(val id: String, val customer: CustomerDto18, val items: List<ItemDto18>, val createdAt: String)

@Mapper
interface OrderMapper18 {
    @Mapping(target = "createdAt", expression = "java(gen.KaptModelKt.iso18(order.getCreatedAt()))")
    fun toDto(order: Order18): OrderDto18
    fun toDto(c: Customer18): CustomerDto18
    fun toDto(a: Address18): AddressDto18
    @Mapping(target = "price", expression = "java(gen.KaptModelKt.price18(item.getPriceCents()))")
    fun toDto(item: Item18): ItemDto18
}
fun iso18(v: Instant): String = v.toString()
fun price18(c: Long): String = c.toString()


data class Address19(val street: String, val city: String, val zip: String)
data class AddressDto19(val street: String, val city: String, val zip: String)
data class Customer19(val name: String, val email: String, val address: Address19)
data class CustomerDto19(val name: String, val email: String, val address: AddressDto19)
data class Item19(val sku: String, val name: String, val qty: Int, val priceCents: Long)
data class ItemDto19(val sku: String, val name: String, val qty: Int, val price: String)
data class Order19(val id: String, val customer: Customer19, val items: List<Item19>, val createdAt: Instant)
data class OrderDto19(val id: String, val customer: CustomerDto19, val items: List<ItemDto19>, val createdAt: String)

@Mapper
interface OrderMapper19 {
    @Mapping(target = "createdAt", expression = "java(gen.KaptModelKt.iso19(order.getCreatedAt()))")
    fun toDto(order: Order19): OrderDto19
    fun toDto(c: Customer19): CustomerDto19
    fun toDto(a: Address19): AddressDto19
    @Mapping(target = "price", expression = "java(gen.KaptModelKt.price19(item.getPriceCents()))")
    fun toDto(item: Item19): ItemDto19
}
fun iso19(v: Instant): String = v.toString()
fun price19(c: Long): String = c.toString()


data class Address20(val street: String, val city: String, val zip: String)
data class AddressDto20(val street: String, val city: String, val zip: String)
data class Customer20(val name: String, val email: String, val address: Address20)
data class CustomerDto20(val name: String, val email: String, val address: AddressDto20)
data class Item20(val sku: String, val name: String, val qty: Int, val priceCents: Long)
data class ItemDto20(val sku: String, val name: String, val qty: Int, val price: String)
data class Order20(val id: String, val customer: Customer20, val items: List<Item20>, val createdAt: Instant)
data class OrderDto20(val id: String, val customer: CustomerDto20, val items: List<ItemDto20>, val createdAt: String)

@Mapper
interface OrderMapper20 {
    @Mapping(target = "createdAt", expression = "java(gen.KaptModelKt.iso20(order.getCreatedAt()))")
    fun toDto(order: Order20): OrderDto20
    fun toDto(c: Customer20): CustomerDto20
    fun toDto(a: Address20): AddressDto20
    @Mapping(target = "price", expression = "java(gen.KaptModelKt.price20(item.getPriceCents()))")
    fun toDto(item: Item20): ItemDto20
}
fun iso20(v: Instant): String = v.toString()
fun price20(c: Long): String = c.toString()


data class Address21(val street: String, val city: String, val zip: String)
data class AddressDto21(val street: String, val city: String, val zip: String)
data class Customer21(val name: String, val email: String, val address: Address21)
data class CustomerDto21(val name: String, val email: String, val address: AddressDto21)
data class Item21(val sku: String, val name: String, val qty: Int, val priceCents: Long)
data class ItemDto21(val sku: String, val name: String, val qty: Int, val price: String)
data class Order21(val id: String, val customer: Customer21, val items: List<Item21>, val createdAt: Instant)
data class OrderDto21(val id: String, val customer: CustomerDto21, val items: List<ItemDto21>, val createdAt: String)

@Mapper
interface OrderMapper21 {
    @Mapping(target = "createdAt", expression = "java(gen.KaptModelKt.iso21(order.getCreatedAt()))")
    fun toDto(order: Order21): OrderDto21
    fun toDto(c: Customer21): CustomerDto21
    fun toDto(a: Address21): AddressDto21
    @Mapping(target = "price", expression = "java(gen.KaptModelKt.price21(item.getPriceCents()))")
    fun toDto(item: Item21): ItemDto21
}
fun iso21(v: Instant): String = v.toString()
fun price21(c: Long): String = c.toString()


data class Address22(val street: String, val city: String, val zip: String)
data class AddressDto22(val street: String, val city: String, val zip: String)
data class Customer22(val name: String, val email: String, val address: Address22)
data class CustomerDto22(val name: String, val email: String, val address: AddressDto22)
data class Item22(val sku: String, val name: String, val qty: Int, val priceCents: Long)
data class ItemDto22(val sku: String, val name: String, val qty: Int, val price: String)
data class Order22(val id: String, val customer: Customer22, val items: List<Item22>, val createdAt: Instant)
data class OrderDto22(val id: String, val customer: CustomerDto22, val items: List<ItemDto22>, val createdAt: String)

@Mapper
interface OrderMapper22 {
    @Mapping(target = "createdAt", expression = "java(gen.KaptModelKt.iso22(order.getCreatedAt()))")
    fun toDto(order: Order22): OrderDto22
    fun toDto(c: Customer22): CustomerDto22
    fun toDto(a: Address22): AddressDto22
    @Mapping(target = "price", expression = "java(gen.KaptModelKt.price22(item.getPriceCents()))")
    fun toDto(item: Item22): ItemDto22
}
fun iso22(v: Instant): String = v.toString()
fun price22(c: Long): String = c.toString()


data class Address23(val street: String, val city: String, val zip: String)
data class AddressDto23(val street: String, val city: String, val zip: String)
data class Customer23(val name: String, val email: String, val address: Address23)
data class CustomerDto23(val name: String, val email: String, val address: AddressDto23)
data class Item23(val sku: String, val name: String, val qty: Int, val priceCents: Long)
data class ItemDto23(val sku: String, val name: String, val qty: Int, val price: String)
data class Order23(val id: String, val customer: Customer23, val items: List<Item23>, val createdAt: Instant)
data class OrderDto23(val id: String, val customer: CustomerDto23, val items: List<ItemDto23>, val createdAt: String)

@Mapper
interface OrderMapper23 {
    @Mapping(target = "createdAt", expression = "java(gen.KaptModelKt.iso23(order.getCreatedAt()))")
    fun toDto(order: Order23): OrderDto23
    fun toDto(c: Customer23): CustomerDto23
    fun toDto(a: Address23): AddressDto23
    @Mapping(target = "price", expression = "java(gen.KaptModelKt.price23(item.getPriceCents()))")
    fun toDto(item: Item23): ItemDto23
}
fun iso23(v: Instant): String = v.toString()
fun price23(c: Long): String = c.toString()


data class Address24(val street: String, val city: String, val zip: String)
data class AddressDto24(val street: String, val city: String, val zip: String)
data class Customer24(val name: String, val email: String, val address: Address24)
data class CustomerDto24(val name: String, val email: String, val address: AddressDto24)
data class Item24(val sku: String, val name: String, val qty: Int, val priceCents: Long)
data class ItemDto24(val sku: String, val name: String, val qty: Int, val price: String)
data class Order24(val id: String, val customer: Customer24, val items: List<Item24>, val createdAt: Instant)
data class OrderDto24(val id: String, val customer: CustomerDto24, val items: List<ItemDto24>, val createdAt: String)

@Mapper
interface OrderMapper24 {
    @Mapping(target = "createdAt", expression = "java(gen.KaptModelKt.iso24(order.getCreatedAt()))")
    fun toDto(order: Order24): OrderDto24
    fun toDto(c: Customer24): CustomerDto24
    fun toDto(a: Address24): AddressDto24
    @Mapping(target = "price", expression = "java(gen.KaptModelKt.price24(item.getPriceCents()))")
    fun toDto(item: Item24): ItemDto24
}
fun iso24(v: Instant): String = v.toString()
fun price24(c: Long): String = c.toString()


data class Address25(val street: String, val city: String, val zip: String)
data class AddressDto25(val street: String, val city: String, val zip: String)
data class Customer25(val name: String, val email: String, val address: Address25)
data class CustomerDto25(val name: String, val email: String, val address: AddressDto25)
data class Item25(val sku: String, val name: String, val qty: Int, val priceCents: Long)
data class ItemDto25(val sku: String, val name: String, val qty: Int, val price: String)
data class Order25(val id: String, val customer: Customer25, val items: List<Item25>, val createdAt: Instant)
data class OrderDto25(val id: String, val customer: CustomerDto25, val items: List<ItemDto25>, val createdAt: String)

@Mapper
interface OrderMapper25 {
    @Mapping(target = "createdAt", expression = "java(gen.KaptModelKt.iso25(order.getCreatedAt()))")
    fun toDto(order: Order25): OrderDto25
    fun toDto(c: Customer25): CustomerDto25
    fun toDto(a: Address25): AddressDto25
    @Mapping(target = "price", expression = "java(gen.KaptModelKt.price25(item.getPriceCents()))")
    fun toDto(item: Item25): ItemDto25
}
fun iso25(v: Instant): String = v.toString()
fun price25(c: Long): String = c.toString()


data class Address26(val street: String, val city: String, val zip: String)
data class AddressDto26(val street: String, val city: String, val zip: String)
data class Customer26(val name: String, val email: String, val address: Address26)
data class CustomerDto26(val name: String, val email: String, val address: AddressDto26)
data class Item26(val sku: String, val name: String, val qty: Int, val priceCents: Long)
data class ItemDto26(val sku: String, val name: String, val qty: Int, val price: String)
data class Order26(val id: String, val customer: Customer26, val items: List<Item26>, val createdAt: Instant)
data class OrderDto26(val id: String, val customer: CustomerDto26, val items: List<ItemDto26>, val createdAt: String)

@Mapper
interface OrderMapper26 {
    @Mapping(target = "createdAt", expression = "java(gen.KaptModelKt.iso26(order.getCreatedAt()))")
    fun toDto(order: Order26): OrderDto26
    fun toDto(c: Customer26): CustomerDto26
    fun toDto(a: Address26): AddressDto26
    @Mapping(target = "price", expression = "java(gen.KaptModelKt.price26(item.getPriceCents()))")
    fun toDto(item: Item26): ItemDto26
}
fun iso26(v: Instant): String = v.toString()
fun price26(c: Long): String = c.toString()


data class Address27(val street: String, val city: String, val zip: String)
data class AddressDto27(val street: String, val city: String, val zip: String)
data class Customer27(val name: String, val email: String, val address: Address27)
data class CustomerDto27(val name: String, val email: String, val address: AddressDto27)
data class Item27(val sku: String, val name: String, val qty: Int, val priceCents: Long)
data class ItemDto27(val sku: String, val name: String, val qty: Int, val price: String)
data class Order27(val id: String, val customer: Customer27, val items: List<Item27>, val createdAt: Instant)
data class OrderDto27(val id: String, val customer: CustomerDto27, val items: List<ItemDto27>, val createdAt: String)

@Mapper
interface OrderMapper27 {
    @Mapping(target = "createdAt", expression = "java(gen.KaptModelKt.iso27(order.getCreatedAt()))")
    fun toDto(order: Order27): OrderDto27
    fun toDto(c: Customer27): CustomerDto27
    fun toDto(a: Address27): AddressDto27
    @Mapping(target = "price", expression = "java(gen.KaptModelKt.price27(item.getPriceCents()))")
    fun toDto(item: Item27): ItemDto27
}
fun iso27(v: Instant): String = v.toString()
fun price27(c: Long): String = c.toString()


data class Address28(val street: String, val city: String, val zip: String)
data class AddressDto28(val street: String, val city: String, val zip: String)
data class Customer28(val name: String, val email: String, val address: Address28)
data class CustomerDto28(val name: String, val email: String, val address: AddressDto28)
data class Item28(val sku: String, val name: String, val qty: Int, val priceCents: Long)
data class ItemDto28(val sku: String, val name: String, val qty: Int, val price: String)
data class Order28(val id: String, val customer: Customer28, val items: List<Item28>, val createdAt: Instant)
data class OrderDto28(val id: String, val customer: CustomerDto28, val items: List<ItemDto28>, val createdAt: String)

@Mapper
interface OrderMapper28 {
    @Mapping(target = "createdAt", expression = "java(gen.KaptModelKt.iso28(order.getCreatedAt()))")
    fun toDto(order: Order28): OrderDto28
    fun toDto(c: Customer28): CustomerDto28
    fun toDto(a: Address28): AddressDto28
    @Mapping(target = "price", expression = "java(gen.KaptModelKt.price28(item.getPriceCents()))")
    fun toDto(item: Item28): ItemDto28
}
fun iso28(v: Instant): String = v.toString()
fun price28(c: Long): String = c.toString()


data class Address29(val street: String, val city: String, val zip: String)
data class AddressDto29(val street: String, val city: String, val zip: String)
data class Customer29(val name: String, val email: String, val address: Address29)
data class CustomerDto29(val name: String, val email: String, val address: AddressDto29)
data class Item29(val sku: String, val name: String, val qty: Int, val priceCents: Long)
data class ItemDto29(val sku: String, val name: String, val qty: Int, val price: String)
data class Order29(val id: String, val customer: Customer29, val items: List<Item29>, val createdAt: Instant)
data class OrderDto29(val id: String, val customer: CustomerDto29, val items: List<ItemDto29>, val createdAt: String)

@Mapper
interface OrderMapper29 {
    @Mapping(target = "createdAt", expression = "java(gen.KaptModelKt.iso29(order.getCreatedAt()))")
    fun toDto(order: Order29): OrderDto29
    fun toDto(c: Customer29): CustomerDto29
    fun toDto(a: Address29): AddressDto29
    @Mapping(target = "price", expression = "java(gen.KaptModelKt.price29(item.getPriceCents()))")
    fun toDto(item: Item29): ItemDto29
}
fun iso29(v: Instant): String = v.toString()
fun price29(c: Long): String = c.toString()


data class Address30(val street: String, val city: String, val zip: String)
data class AddressDto30(val street: String, val city: String, val zip: String)
data class Customer30(val name: String, val email: String, val address: Address30)
data class CustomerDto30(val name: String, val email: String, val address: AddressDto30)
data class Item30(val sku: String, val name: String, val qty: Int, val priceCents: Long)
data class ItemDto30(val sku: String, val name: String, val qty: Int, val price: String)
data class Order30(val id: String, val customer: Customer30, val items: List<Item30>, val createdAt: Instant)
data class OrderDto30(val id: String, val customer: CustomerDto30, val items: List<ItemDto30>, val createdAt: String)

@Mapper
interface OrderMapper30 {
    @Mapping(target = "createdAt", expression = "java(gen.KaptModelKt.iso30(order.getCreatedAt()))")
    fun toDto(order: Order30): OrderDto30
    fun toDto(c: Customer30): CustomerDto30
    fun toDto(a: Address30): AddressDto30
    @Mapping(target = "price", expression = "java(gen.KaptModelKt.price30(item.getPriceCents()))")
    fun toDto(item: Item30): ItemDto30
}
fun iso30(v: Instant): String = v.toString()
fun price30(c: Long): String = c.toString()


data class Address31(val street: String, val city: String, val zip: String)
data class AddressDto31(val street: String, val city: String, val zip: String)
data class Customer31(val name: String, val email: String, val address: Address31)
data class CustomerDto31(val name: String, val email: String, val address: AddressDto31)
data class Item31(val sku: String, val name: String, val qty: Int, val priceCents: Long)
data class ItemDto31(val sku: String, val name: String, val qty: Int, val price: String)
data class Order31(val id: String, val customer: Customer31, val items: List<Item31>, val createdAt: Instant)
data class OrderDto31(val id: String, val customer: CustomerDto31, val items: List<ItemDto31>, val createdAt: String)

@Mapper
interface OrderMapper31 {
    @Mapping(target = "createdAt", expression = "java(gen.KaptModelKt.iso31(order.getCreatedAt()))")
    fun toDto(order: Order31): OrderDto31
    fun toDto(c: Customer31): CustomerDto31
    fun toDto(a: Address31): AddressDto31
    @Mapping(target = "price", expression = "java(gen.KaptModelKt.price31(item.getPriceCents()))")
    fun toDto(item: Item31): ItemDto31
}
fun iso31(v: Instant): String = v.toString()
fun price31(c: Long): String = c.toString()


data class Address32(val street: String, val city: String, val zip: String)
data class AddressDto32(val street: String, val city: String, val zip: String)
data class Customer32(val name: String, val email: String, val address: Address32)
data class CustomerDto32(val name: String, val email: String, val address: AddressDto32)
data class Item32(val sku: String, val name: String, val qty: Int, val priceCents: Long)
data class ItemDto32(val sku: String, val name: String, val qty: Int, val price: String)
data class Order32(val id: String, val customer: Customer32, val items: List<Item32>, val createdAt: Instant)
data class OrderDto32(val id: String, val customer: CustomerDto32, val items: List<ItemDto32>, val createdAt: String)

@Mapper
interface OrderMapper32 {
    @Mapping(target = "createdAt", expression = "java(gen.KaptModelKt.iso32(order.getCreatedAt()))")
    fun toDto(order: Order32): OrderDto32
    fun toDto(c: Customer32): CustomerDto32
    fun toDto(a: Address32): AddressDto32
    @Mapping(target = "price", expression = "java(gen.KaptModelKt.price32(item.getPriceCents()))")
    fun toDto(item: Item32): ItemDto32
}
fun iso32(v: Instant): String = v.toString()
fun price32(c: Long): String = c.toString()


data class Address33(val street: String, val city: String, val zip: String)
data class AddressDto33(val street: String, val city: String, val zip: String)
data class Customer33(val name: String, val email: String, val address: Address33)
data class CustomerDto33(val name: String, val email: String, val address: AddressDto33)
data class Item33(val sku: String, val name: String, val qty: Int, val priceCents: Long)
data class ItemDto33(val sku: String, val name: String, val qty: Int, val price: String)
data class Order33(val id: String, val customer: Customer33, val items: List<Item33>, val createdAt: Instant)
data class OrderDto33(val id: String, val customer: CustomerDto33, val items: List<ItemDto33>, val createdAt: String)

@Mapper
interface OrderMapper33 {
    @Mapping(target = "createdAt", expression = "java(gen.KaptModelKt.iso33(order.getCreatedAt()))")
    fun toDto(order: Order33): OrderDto33
    fun toDto(c: Customer33): CustomerDto33
    fun toDto(a: Address33): AddressDto33
    @Mapping(target = "price", expression = "java(gen.KaptModelKt.price33(item.getPriceCents()))")
    fun toDto(item: Item33): ItemDto33
}
fun iso33(v: Instant): String = v.toString()
fun price33(c: Long): String = c.toString()


data class Address34(val street: String, val city: String, val zip: String)
data class AddressDto34(val street: String, val city: String, val zip: String)
data class Customer34(val name: String, val email: String, val address: Address34)
data class CustomerDto34(val name: String, val email: String, val address: AddressDto34)
data class Item34(val sku: String, val name: String, val qty: Int, val priceCents: Long)
data class ItemDto34(val sku: String, val name: String, val qty: Int, val price: String)
data class Order34(val id: String, val customer: Customer34, val items: List<Item34>, val createdAt: Instant)
data class OrderDto34(val id: String, val customer: CustomerDto34, val items: List<ItemDto34>, val createdAt: String)

@Mapper
interface OrderMapper34 {
    @Mapping(target = "createdAt", expression = "java(gen.KaptModelKt.iso34(order.getCreatedAt()))")
    fun toDto(order: Order34): OrderDto34
    fun toDto(c: Customer34): CustomerDto34
    fun toDto(a: Address34): AddressDto34
    @Mapping(target = "price", expression = "java(gen.KaptModelKt.price34(item.getPriceCents()))")
    fun toDto(item: Item34): ItemDto34
}
fun iso34(v: Instant): String = v.toString()
fun price34(c: Long): String = c.toString()


data class Address35(val street: String, val city: String, val zip: String)
data class AddressDto35(val street: String, val city: String, val zip: String)
data class Customer35(val name: String, val email: String, val address: Address35)
data class CustomerDto35(val name: String, val email: String, val address: AddressDto35)
data class Item35(val sku: String, val name: String, val qty: Int, val priceCents: Long)
data class ItemDto35(val sku: String, val name: String, val qty: Int, val price: String)
data class Order35(val id: String, val customer: Customer35, val items: List<Item35>, val createdAt: Instant)
data class OrderDto35(val id: String, val customer: CustomerDto35, val items: List<ItemDto35>, val createdAt: String)

@Mapper
interface OrderMapper35 {
    @Mapping(target = "createdAt", expression = "java(gen.KaptModelKt.iso35(order.getCreatedAt()))")
    fun toDto(order: Order35): OrderDto35
    fun toDto(c: Customer35): CustomerDto35
    fun toDto(a: Address35): AddressDto35
    @Mapping(target = "price", expression = "java(gen.KaptModelKt.price35(item.getPriceCents()))")
    fun toDto(item: Item35): ItemDto35
}
fun iso35(v: Instant): String = v.toString()
fun price35(c: Long): String = c.toString()


data class Address36(val street: String, val city: String, val zip: String)
data class AddressDto36(val street: String, val city: String, val zip: String)
data class Customer36(val name: String, val email: String, val address: Address36)
data class CustomerDto36(val name: String, val email: String, val address: AddressDto36)
data class Item36(val sku: String, val name: String, val qty: Int, val priceCents: Long)
data class ItemDto36(val sku: String, val name: String, val qty: Int, val price: String)
data class Order36(val id: String, val customer: Customer36, val items: List<Item36>, val createdAt: Instant)
data class OrderDto36(val id: String, val customer: CustomerDto36, val items: List<ItemDto36>, val createdAt: String)

@Mapper
interface OrderMapper36 {
    @Mapping(target = "createdAt", expression = "java(gen.KaptModelKt.iso36(order.getCreatedAt()))")
    fun toDto(order: Order36): OrderDto36
    fun toDto(c: Customer36): CustomerDto36
    fun toDto(a: Address36): AddressDto36
    @Mapping(target = "price", expression = "java(gen.KaptModelKt.price36(item.getPriceCents()))")
    fun toDto(item: Item36): ItemDto36
}
fun iso36(v: Instant): String = v.toString()
fun price36(c: Long): String = c.toString()


data class Address37(val street: String, val city: String, val zip: String)
data class AddressDto37(val street: String, val city: String, val zip: String)
data class Customer37(val name: String, val email: String, val address: Address37)
data class CustomerDto37(val name: String, val email: String, val address: AddressDto37)
data class Item37(val sku: String, val name: String, val qty: Int, val priceCents: Long)
data class ItemDto37(val sku: String, val name: String, val qty: Int, val price: String)
data class Order37(val id: String, val customer: Customer37, val items: List<Item37>, val createdAt: Instant)
data class OrderDto37(val id: String, val customer: CustomerDto37, val items: List<ItemDto37>, val createdAt: String)

@Mapper
interface OrderMapper37 {
    @Mapping(target = "createdAt", expression = "java(gen.KaptModelKt.iso37(order.getCreatedAt()))")
    fun toDto(order: Order37): OrderDto37
    fun toDto(c: Customer37): CustomerDto37
    fun toDto(a: Address37): AddressDto37
    @Mapping(target = "price", expression = "java(gen.KaptModelKt.price37(item.getPriceCents()))")
    fun toDto(item: Item37): ItemDto37
}
fun iso37(v: Instant): String = v.toString()
fun price37(c: Long): String = c.toString()


data class Address38(val street: String, val city: String, val zip: String)
data class AddressDto38(val street: String, val city: String, val zip: String)
data class Customer38(val name: String, val email: String, val address: Address38)
data class CustomerDto38(val name: String, val email: String, val address: AddressDto38)
data class Item38(val sku: String, val name: String, val qty: Int, val priceCents: Long)
data class ItemDto38(val sku: String, val name: String, val qty: Int, val price: String)
data class Order38(val id: String, val customer: Customer38, val items: List<Item38>, val createdAt: Instant)
data class OrderDto38(val id: String, val customer: CustomerDto38, val items: List<ItemDto38>, val createdAt: String)

@Mapper
interface OrderMapper38 {
    @Mapping(target = "createdAt", expression = "java(gen.KaptModelKt.iso38(order.getCreatedAt()))")
    fun toDto(order: Order38): OrderDto38
    fun toDto(c: Customer38): CustomerDto38
    fun toDto(a: Address38): AddressDto38
    @Mapping(target = "price", expression = "java(gen.KaptModelKt.price38(item.getPriceCents()))")
    fun toDto(item: Item38): ItemDto38
}
fun iso38(v: Instant): String = v.toString()
fun price38(c: Long): String = c.toString()


data class Address39(val street: String, val city: String, val zip: String)
data class AddressDto39(val street: String, val city: String, val zip: String)
data class Customer39(val name: String, val email: String, val address: Address39)
data class CustomerDto39(val name: String, val email: String, val address: AddressDto39)
data class Item39(val sku: String, val name: String, val qty: Int, val priceCents: Long)
data class ItemDto39(val sku: String, val name: String, val qty: Int, val price: String)
data class Order39(val id: String, val customer: Customer39, val items: List<Item39>, val createdAt: Instant)
data class OrderDto39(val id: String, val customer: CustomerDto39, val items: List<ItemDto39>, val createdAt: String)

@Mapper
interface OrderMapper39 {
    @Mapping(target = "createdAt", expression = "java(gen.KaptModelKt.iso39(order.getCreatedAt()))")
    fun toDto(order: Order39): OrderDto39
    fun toDto(c: Customer39): CustomerDto39
    fun toDto(a: Address39): AddressDto39
    @Mapping(target = "price", expression = "java(gen.KaptModelKt.price39(item.getPriceCents()))")
    fun toDto(item: Item39): ItemDto39
}
fun iso39(v: Instant): String = v.toString()
fun price39(c: Long): String = c.toString()
