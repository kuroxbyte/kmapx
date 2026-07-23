package gen
import dev.kmapx.annotations.MapField
import dev.kmapx.annotations.embedded.MapTo
import dev.kmapx.runtime.Converts
import java.time.Instant


@JvmInline value class OrderId0(val value: String)
@MapTo(AddressDto0::class)
data class Address0(val street: String, val city: String, val zip: String)
data class AddressDto0(val street: String, val city: String, val zip: String)

@MapTo(CustomerDto0::class)
data class Customer0(val name: String, val email: String, val address: Address0)
data class CustomerDto0(val name: String, val email: String, val address: AddressDto0)

@MapTo(ItemDto0::class)
data class Item0(val sku: String, val name: String, val qty: Int, val priceCents: Long)
data class ItemDto0(val sku: String, val name: String, val qty: Int,
    @MapField(from = "priceCents", converter = Price0::class) val price: String)
object Price0 : Converts<Long, String> { override fun convert(value: Long) = value.toString() }

@MapTo(OrderDto0::class)
data class Order0(val id: OrderId0, val customer: Customer0, val items: List<Item0>, val createdAt: Instant)
data class OrderDto0(val id: String, val customer: CustomerDto0, val items: List<ItemDto0>,
    @MapField(converter = Iso0::class) val createdAt: String)
object Iso0 : Converts<Instant, String> { override fun convert(value: Instant) = value.toString() }


@JvmInline value class OrderId1(val value: String)
@MapTo(AddressDto1::class)
data class Address1(val street: String, val city: String, val zip: String)
data class AddressDto1(val street: String, val city: String, val zip: String)

@MapTo(CustomerDto1::class)
data class Customer1(val name: String, val email: String, val address: Address1)
data class CustomerDto1(val name: String, val email: String, val address: AddressDto1)

@MapTo(ItemDto1::class)
data class Item1(val sku: String, val name: String, val qty: Int, val priceCents: Long)
data class ItemDto1(val sku: String, val name: String, val qty: Int,
    @MapField(from = "priceCents", converter = Price1::class) val price: String)
object Price1 : Converts<Long, String> { override fun convert(value: Long) = value.toString() }

@MapTo(OrderDto1::class)
data class Order1(val id: OrderId1, val customer: Customer1, val items: List<Item1>, val createdAt: Instant)
data class OrderDto1(val id: String, val customer: CustomerDto1, val items: List<ItemDto1>,
    @MapField(converter = Iso1::class) val createdAt: String)
object Iso1 : Converts<Instant, String> { override fun convert(value: Instant) = value.toString() }


@JvmInline value class OrderId2(val value: String)
@MapTo(AddressDto2::class)
data class Address2(val street: String, val city: String, val zip: String)
data class AddressDto2(val street: String, val city: String, val zip: String)

@MapTo(CustomerDto2::class)
data class Customer2(val name: String, val email: String, val address: Address2)
data class CustomerDto2(val name: String, val email: String, val address: AddressDto2)

@MapTo(ItemDto2::class)
data class Item2(val sku: String, val name: String, val qty: Int, val priceCents: Long)
data class ItemDto2(val sku: String, val name: String, val qty: Int,
    @MapField(from = "priceCents", converter = Price2::class) val price: String)
object Price2 : Converts<Long, String> { override fun convert(value: Long) = value.toString() }

@MapTo(OrderDto2::class)
data class Order2(val id: OrderId2, val customer: Customer2, val items: List<Item2>, val createdAt: Instant)
data class OrderDto2(val id: String, val customer: CustomerDto2, val items: List<ItemDto2>,
    @MapField(converter = Iso2::class) val createdAt: String)
object Iso2 : Converts<Instant, String> { override fun convert(value: Instant) = value.toString() }


@JvmInline value class OrderId3(val value: String)
@MapTo(AddressDto3::class)
data class Address3(val street: String, val city: String, val zip: String)
data class AddressDto3(val street: String, val city: String, val zip: String)

@MapTo(CustomerDto3::class)
data class Customer3(val name: String, val email: String, val address: Address3)
data class CustomerDto3(val name: String, val email: String, val address: AddressDto3)

@MapTo(ItemDto3::class)
data class Item3(val sku: String, val name: String, val qty: Int, val priceCents: Long)
data class ItemDto3(val sku: String, val name: String, val qty: Int,
    @MapField(from = "priceCents", converter = Price3::class) val price: String)
object Price3 : Converts<Long, String> { override fun convert(value: Long) = value.toString() }

@MapTo(OrderDto3::class)
data class Order3(val id: OrderId3, val customer: Customer3, val items: List<Item3>, val createdAt: Instant)
data class OrderDto3(val id: String, val customer: CustomerDto3, val items: List<ItemDto3>,
    @MapField(converter = Iso3::class) val createdAt: String)
object Iso3 : Converts<Instant, String> { override fun convert(value: Instant) = value.toString() }


@JvmInline value class OrderId4(val value: String)
@MapTo(AddressDto4::class)
data class Address4(val street: String, val city: String, val zip: String)
data class AddressDto4(val street: String, val city: String, val zip: String)

@MapTo(CustomerDto4::class)
data class Customer4(val name: String, val email: String, val address: Address4)
data class CustomerDto4(val name: String, val email: String, val address: AddressDto4)

@MapTo(ItemDto4::class)
data class Item4(val sku: String, val name: String, val qty: Int, val priceCents: Long)
data class ItemDto4(val sku: String, val name: String, val qty: Int,
    @MapField(from = "priceCents", converter = Price4::class) val price: String)
object Price4 : Converts<Long, String> { override fun convert(value: Long) = value.toString() }

@MapTo(OrderDto4::class)
data class Order4(val id: OrderId4, val customer: Customer4, val items: List<Item4>, val createdAt: Instant)
data class OrderDto4(val id: String, val customer: CustomerDto4, val items: List<ItemDto4>,
    @MapField(converter = Iso4::class) val createdAt: String)
object Iso4 : Converts<Instant, String> { override fun convert(value: Instant) = value.toString() }


@JvmInline value class OrderId5(val value: String)
@MapTo(AddressDto5::class)
data class Address5(val street: String, val city: String, val zip: String)
data class AddressDto5(val street: String, val city: String, val zip: String)

@MapTo(CustomerDto5::class)
data class Customer5(val name: String, val email: String, val address: Address5)
data class CustomerDto5(val name: String, val email: String, val address: AddressDto5)

@MapTo(ItemDto5::class)
data class Item5(val sku: String, val name: String, val qty: Int, val priceCents: Long)
data class ItemDto5(val sku: String, val name: String, val qty: Int,
    @MapField(from = "priceCents", converter = Price5::class) val price: String)
object Price5 : Converts<Long, String> { override fun convert(value: Long) = value.toString() }

@MapTo(OrderDto5::class)
data class Order5(val id: OrderId5, val customer: Customer5, val items: List<Item5>, val createdAt: Instant)
data class OrderDto5(val id: String, val customer: CustomerDto5, val items: List<ItemDto5>,
    @MapField(converter = Iso5::class) val createdAt: String)
object Iso5 : Converts<Instant, String> { override fun convert(value: Instant) = value.toString() }


@JvmInline value class OrderId6(val value: String)
@MapTo(AddressDto6::class)
data class Address6(val street: String, val city: String, val zip: String)
data class AddressDto6(val street: String, val city: String, val zip: String)

@MapTo(CustomerDto6::class)
data class Customer6(val name: String, val email: String, val address: Address6)
data class CustomerDto6(val name: String, val email: String, val address: AddressDto6)

@MapTo(ItemDto6::class)
data class Item6(val sku: String, val name: String, val qty: Int, val priceCents: Long)
data class ItemDto6(val sku: String, val name: String, val qty: Int,
    @MapField(from = "priceCents", converter = Price6::class) val price: String)
object Price6 : Converts<Long, String> { override fun convert(value: Long) = value.toString() }

@MapTo(OrderDto6::class)
data class Order6(val id: OrderId6, val customer: Customer6, val items: List<Item6>, val createdAt: Instant)
data class OrderDto6(val id: String, val customer: CustomerDto6, val items: List<ItemDto6>,
    @MapField(converter = Iso6::class) val createdAt: String)
object Iso6 : Converts<Instant, String> { override fun convert(value: Instant) = value.toString() }


@JvmInline value class OrderId7(val value: String)
@MapTo(AddressDto7::class)
data class Address7(val street: String, val city: String, val zip: String)
data class AddressDto7(val street: String, val city: String, val zip: String)

@MapTo(CustomerDto7::class)
data class Customer7(val name: String, val email: String, val address: Address7)
data class CustomerDto7(val name: String, val email: String, val address: AddressDto7)

@MapTo(ItemDto7::class)
data class Item7(val sku: String, val name: String, val qty: Int, val priceCents: Long)
data class ItemDto7(val sku: String, val name: String, val qty: Int,
    @MapField(from = "priceCents", converter = Price7::class) val price: String)
object Price7 : Converts<Long, String> { override fun convert(value: Long) = value.toString() }

@MapTo(OrderDto7::class)
data class Order7(val id: OrderId7, val customer: Customer7, val items: List<Item7>, val createdAt: Instant)
data class OrderDto7(val id: String, val customer: CustomerDto7, val items: List<ItemDto7>,
    @MapField(converter = Iso7::class) val createdAt: String)
object Iso7 : Converts<Instant, String> { override fun convert(value: Instant) = value.toString() }


@JvmInline value class OrderId8(val value: String)
@MapTo(AddressDto8::class)
data class Address8(val street: String, val city: String, val zip: String)
data class AddressDto8(val street: String, val city: String, val zip: String)

@MapTo(CustomerDto8::class)
data class Customer8(val name: String, val email: String, val address: Address8)
data class CustomerDto8(val name: String, val email: String, val address: AddressDto8)

@MapTo(ItemDto8::class)
data class Item8(val sku: String, val name: String, val qty: Int, val priceCents: Long)
data class ItemDto8(val sku: String, val name: String, val qty: Int,
    @MapField(from = "priceCents", converter = Price8::class) val price: String)
object Price8 : Converts<Long, String> { override fun convert(value: Long) = value.toString() }

@MapTo(OrderDto8::class)
data class Order8(val id: OrderId8, val customer: Customer8, val items: List<Item8>, val createdAt: Instant)
data class OrderDto8(val id: String, val customer: CustomerDto8, val items: List<ItemDto8>,
    @MapField(converter = Iso8::class) val createdAt: String)
object Iso8 : Converts<Instant, String> { override fun convert(value: Instant) = value.toString() }


@JvmInline value class OrderId9(val value: String)
@MapTo(AddressDto9::class)
data class Address9(val street: String, val city: String, val zip: String)
data class AddressDto9(val street: String, val city: String, val zip: String)

@MapTo(CustomerDto9::class)
data class Customer9(val name: String, val email: String, val address: Address9)
data class CustomerDto9(val name: String, val email: String, val address: AddressDto9)

@MapTo(ItemDto9::class)
data class Item9(val sku: String, val name: String, val qty: Int, val priceCents: Long)
data class ItemDto9(val sku: String, val name: String, val qty: Int,
    @MapField(from = "priceCents", converter = Price9::class) val price: String)
object Price9 : Converts<Long, String> { override fun convert(value: Long) = value.toString() }

@MapTo(OrderDto9::class)
data class Order9(val id: OrderId9, val customer: Customer9, val items: List<Item9>, val createdAt: Instant)
data class OrderDto9(val id: String, val customer: CustomerDto9, val items: List<ItemDto9>,
    @MapField(converter = Iso9::class) val createdAt: String)
object Iso9 : Converts<Instant, String> { override fun convert(value: Instant) = value.toString() }


@JvmInline value class OrderId10(val value: String)
@MapTo(AddressDto10::class)
data class Address10(val street: String, val city: String, val zip: String)
data class AddressDto10(val street: String, val city: String, val zip: String)

@MapTo(CustomerDto10::class)
data class Customer10(val name: String, val email: String, val address: Address10)
data class CustomerDto10(val name: String, val email: String, val address: AddressDto10)

@MapTo(ItemDto10::class)
data class Item10(val sku: String, val name: String, val qty: Int, val priceCents: Long)
data class ItemDto10(val sku: String, val name: String, val qty: Int,
    @MapField(from = "priceCents", converter = Price10::class) val price: String)
object Price10 : Converts<Long, String> { override fun convert(value: Long) = value.toString() }

@MapTo(OrderDto10::class)
data class Order10(val id: OrderId10, val customer: Customer10, val items: List<Item10>, val createdAt: Instant)
data class OrderDto10(val id: String, val customer: CustomerDto10, val items: List<ItemDto10>,
    @MapField(converter = Iso10::class) val createdAt: String)
object Iso10 : Converts<Instant, String> { override fun convert(value: Instant) = value.toString() }


@JvmInline value class OrderId11(val value: String)
@MapTo(AddressDto11::class)
data class Address11(val street: String, val city: String, val zip: String)
data class AddressDto11(val street: String, val city: String, val zip: String)

@MapTo(CustomerDto11::class)
data class Customer11(val name: String, val email: String, val address: Address11)
data class CustomerDto11(val name: String, val email: String, val address: AddressDto11)

@MapTo(ItemDto11::class)
data class Item11(val sku: String, val name: String, val qty: Int, val priceCents: Long)
data class ItemDto11(val sku: String, val name: String, val qty: Int,
    @MapField(from = "priceCents", converter = Price11::class) val price: String)
object Price11 : Converts<Long, String> { override fun convert(value: Long) = value.toString() }

@MapTo(OrderDto11::class)
data class Order11(val id: OrderId11, val customer: Customer11, val items: List<Item11>, val createdAt: Instant)
data class OrderDto11(val id: String, val customer: CustomerDto11, val items: List<ItemDto11>,
    @MapField(converter = Iso11::class) val createdAt: String)
object Iso11 : Converts<Instant, String> { override fun convert(value: Instant) = value.toString() }


@JvmInline value class OrderId12(val value: String)
@MapTo(AddressDto12::class)
data class Address12(val street: String, val city: String, val zip: String)
data class AddressDto12(val street: String, val city: String, val zip: String)

@MapTo(CustomerDto12::class)
data class Customer12(val name: String, val email: String, val address: Address12)
data class CustomerDto12(val name: String, val email: String, val address: AddressDto12)

@MapTo(ItemDto12::class)
data class Item12(val sku: String, val name: String, val qty: Int, val priceCents: Long)
data class ItemDto12(val sku: String, val name: String, val qty: Int,
    @MapField(from = "priceCents", converter = Price12::class) val price: String)
object Price12 : Converts<Long, String> { override fun convert(value: Long) = value.toString() }

@MapTo(OrderDto12::class)
data class Order12(val id: OrderId12, val customer: Customer12, val items: List<Item12>, val createdAt: Instant)
data class OrderDto12(val id: String, val customer: CustomerDto12, val items: List<ItemDto12>,
    @MapField(converter = Iso12::class) val createdAt: String)
object Iso12 : Converts<Instant, String> { override fun convert(value: Instant) = value.toString() }


@JvmInline value class OrderId13(val value: String)
@MapTo(AddressDto13::class)
data class Address13(val street: String, val city: String, val zip: String)
data class AddressDto13(val street: String, val city: String, val zip: String)

@MapTo(CustomerDto13::class)
data class Customer13(val name: String, val email: String, val address: Address13)
data class CustomerDto13(val name: String, val email: String, val address: AddressDto13)

@MapTo(ItemDto13::class)
data class Item13(val sku: String, val name: String, val qty: Int, val priceCents: Long)
data class ItemDto13(val sku: String, val name: String, val qty: Int,
    @MapField(from = "priceCents", converter = Price13::class) val price: String)
object Price13 : Converts<Long, String> { override fun convert(value: Long) = value.toString() }

@MapTo(OrderDto13::class)
data class Order13(val id: OrderId13, val customer: Customer13, val items: List<Item13>, val createdAt: Instant)
data class OrderDto13(val id: String, val customer: CustomerDto13, val items: List<ItemDto13>,
    @MapField(converter = Iso13::class) val createdAt: String)
object Iso13 : Converts<Instant, String> { override fun convert(value: Instant) = value.toString() }


@JvmInline value class OrderId14(val value: String)
@MapTo(AddressDto14::class)
data class Address14(val street: String, val city: String, val zip: String)
data class AddressDto14(val street: String, val city: String, val zip: String)

@MapTo(CustomerDto14::class)
data class Customer14(val name: String, val email: String, val address: Address14)
data class CustomerDto14(val name: String, val email: String, val address: AddressDto14)

@MapTo(ItemDto14::class)
data class Item14(val sku: String, val name: String, val qty: Int, val priceCents: Long)
data class ItemDto14(val sku: String, val name: String, val qty: Int,
    @MapField(from = "priceCents", converter = Price14::class) val price: String)
object Price14 : Converts<Long, String> { override fun convert(value: Long) = value.toString() }

@MapTo(OrderDto14::class)
data class Order14(val id: OrderId14, val customer: Customer14, val items: List<Item14>, val createdAt: Instant)
data class OrderDto14(val id: String, val customer: CustomerDto14, val items: List<ItemDto14>,
    @MapField(converter = Iso14::class) val createdAt: String)
object Iso14 : Converts<Instant, String> { override fun convert(value: Instant) = value.toString() }


@JvmInline value class OrderId15(val value: String)
@MapTo(AddressDto15::class)
data class Address15(val street: String, val city: String, val zip: String)
data class AddressDto15(val street: String, val city: String, val zip: String)

@MapTo(CustomerDto15::class)
data class Customer15(val name: String, val email: String, val address: Address15)
data class CustomerDto15(val name: String, val email: String, val address: AddressDto15)

@MapTo(ItemDto15::class)
data class Item15(val sku: String, val name: String, val qty: Int, val priceCents: Long)
data class ItemDto15(val sku: String, val name: String, val qty: Int,
    @MapField(from = "priceCents", converter = Price15::class) val price: String)
object Price15 : Converts<Long, String> { override fun convert(value: Long) = value.toString() }

@MapTo(OrderDto15::class)
data class Order15(val id: OrderId15, val customer: Customer15, val items: List<Item15>, val createdAt: Instant)
data class OrderDto15(val id: String, val customer: CustomerDto15, val items: List<ItemDto15>,
    @MapField(converter = Iso15::class) val createdAt: String)
object Iso15 : Converts<Instant, String> { override fun convert(value: Instant) = value.toString() }


@JvmInline value class OrderId16(val value: String)
@MapTo(AddressDto16::class)
data class Address16(val street: String, val city: String, val zip: String)
data class AddressDto16(val street: String, val city: String, val zip: String)

@MapTo(CustomerDto16::class)
data class Customer16(val name: String, val email: String, val address: Address16)
data class CustomerDto16(val name: String, val email: String, val address: AddressDto16)

@MapTo(ItemDto16::class)
data class Item16(val sku: String, val name: String, val qty: Int, val priceCents: Long)
data class ItemDto16(val sku: String, val name: String, val qty: Int,
    @MapField(from = "priceCents", converter = Price16::class) val price: String)
object Price16 : Converts<Long, String> { override fun convert(value: Long) = value.toString() }

@MapTo(OrderDto16::class)
data class Order16(val id: OrderId16, val customer: Customer16, val items: List<Item16>, val createdAt: Instant)
data class OrderDto16(val id: String, val customer: CustomerDto16, val items: List<ItemDto16>,
    @MapField(converter = Iso16::class) val createdAt: String)
object Iso16 : Converts<Instant, String> { override fun convert(value: Instant) = value.toString() }


@JvmInline value class OrderId17(val value: String)
@MapTo(AddressDto17::class)
data class Address17(val street: String, val city: String, val zip: String)
data class AddressDto17(val street: String, val city: String, val zip: String)

@MapTo(CustomerDto17::class)
data class Customer17(val name: String, val email: String, val address: Address17)
data class CustomerDto17(val name: String, val email: String, val address: AddressDto17)

@MapTo(ItemDto17::class)
data class Item17(val sku: String, val name: String, val qty: Int, val priceCents: Long)
data class ItemDto17(val sku: String, val name: String, val qty: Int,
    @MapField(from = "priceCents", converter = Price17::class) val price: String)
object Price17 : Converts<Long, String> { override fun convert(value: Long) = value.toString() }

@MapTo(OrderDto17::class)
data class Order17(val id: OrderId17, val customer: Customer17, val items: List<Item17>, val createdAt: Instant)
data class OrderDto17(val id: String, val customer: CustomerDto17, val items: List<ItemDto17>,
    @MapField(converter = Iso17::class) val createdAt: String)
object Iso17 : Converts<Instant, String> { override fun convert(value: Instant) = value.toString() }


@JvmInline value class OrderId18(val value: String)
@MapTo(AddressDto18::class)
data class Address18(val street: String, val city: String, val zip: String)
data class AddressDto18(val street: String, val city: String, val zip: String)

@MapTo(CustomerDto18::class)
data class Customer18(val name: String, val email: String, val address: Address18)
data class CustomerDto18(val name: String, val email: String, val address: AddressDto18)

@MapTo(ItemDto18::class)
data class Item18(val sku: String, val name: String, val qty: Int, val priceCents: Long)
data class ItemDto18(val sku: String, val name: String, val qty: Int,
    @MapField(from = "priceCents", converter = Price18::class) val price: String)
object Price18 : Converts<Long, String> { override fun convert(value: Long) = value.toString() }

@MapTo(OrderDto18::class)
data class Order18(val id: OrderId18, val customer: Customer18, val items: List<Item18>, val createdAt: Instant)
data class OrderDto18(val id: String, val customer: CustomerDto18, val items: List<ItemDto18>,
    @MapField(converter = Iso18::class) val createdAt: String)
object Iso18 : Converts<Instant, String> { override fun convert(value: Instant) = value.toString() }


@JvmInline value class OrderId19(val value: String)
@MapTo(AddressDto19::class)
data class Address19(val street: String, val city: String, val zip: String)
data class AddressDto19(val street: String, val city: String, val zip: String)

@MapTo(CustomerDto19::class)
data class Customer19(val name: String, val email: String, val address: Address19)
data class CustomerDto19(val name: String, val email: String, val address: AddressDto19)

@MapTo(ItemDto19::class)
data class Item19(val sku: String, val name: String, val qty: Int, val priceCents: Long)
data class ItemDto19(val sku: String, val name: String, val qty: Int,
    @MapField(from = "priceCents", converter = Price19::class) val price: String)
object Price19 : Converts<Long, String> { override fun convert(value: Long) = value.toString() }

@MapTo(OrderDto19::class)
data class Order19(val id: OrderId19, val customer: Customer19, val items: List<Item19>, val createdAt: Instant)
data class OrderDto19(val id: String, val customer: CustomerDto19, val items: List<ItemDto19>,
    @MapField(converter = Iso19::class) val createdAt: String)
object Iso19 : Converts<Instant, String> { override fun convert(value: Instant) = value.toString() }


@JvmInline value class OrderId20(val value: String)
@MapTo(AddressDto20::class)
data class Address20(val street: String, val city: String, val zip: String)
data class AddressDto20(val street: String, val city: String, val zip: String)

@MapTo(CustomerDto20::class)
data class Customer20(val name: String, val email: String, val address: Address20)
data class CustomerDto20(val name: String, val email: String, val address: AddressDto20)

@MapTo(ItemDto20::class)
data class Item20(val sku: String, val name: String, val qty: Int, val priceCents: Long)
data class ItemDto20(val sku: String, val name: String, val qty: Int,
    @MapField(from = "priceCents", converter = Price20::class) val price: String)
object Price20 : Converts<Long, String> { override fun convert(value: Long) = value.toString() }

@MapTo(OrderDto20::class)
data class Order20(val id: OrderId20, val customer: Customer20, val items: List<Item20>, val createdAt: Instant)
data class OrderDto20(val id: String, val customer: CustomerDto20, val items: List<ItemDto20>,
    @MapField(converter = Iso20::class) val createdAt: String)
object Iso20 : Converts<Instant, String> { override fun convert(value: Instant) = value.toString() }


@JvmInline value class OrderId21(val value: String)
@MapTo(AddressDto21::class)
data class Address21(val street: String, val city: String, val zip: String)
data class AddressDto21(val street: String, val city: String, val zip: String)

@MapTo(CustomerDto21::class)
data class Customer21(val name: String, val email: String, val address: Address21)
data class CustomerDto21(val name: String, val email: String, val address: AddressDto21)

@MapTo(ItemDto21::class)
data class Item21(val sku: String, val name: String, val qty: Int, val priceCents: Long)
data class ItemDto21(val sku: String, val name: String, val qty: Int,
    @MapField(from = "priceCents", converter = Price21::class) val price: String)
object Price21 : Converts<Long, String> { override fun convert(value: Long) = value.toString() }

@MapTo(OrderDto21::class)
data class Order21(val id: OrderId21, val customer: Customer21, val items: List<Item21>, val createdAt: Instant)
data class OrderDto21(val id: String, val customer: CustomerDto21, val items: List<ItemDto21>,
    @MapField(converter = Iso21::class) val createdAt: String)
object Iso21 : Converts<Instant, String> { override fun convert(value: Instant) = value.toString() }


@JvmInline value class OrderId22(val value: String)
@MapTo(AddressDto22::class)
data class Address22(val street: String, val city: String, val zip: String)
data class AddressDto22(val street: String, val city: String, val zip: String)

@MapTo(CustomerDto22::class)
data class Customer22(val name: String, val email: String, val address: Address22)
data class CustomerDto22(val name: String, val email: String, val address: AddressDto22)

@MapTo(ItemDto22::class)
data class Item22(val sku: String, val name: String, val qty: Int, val priceCents: Long)
data class ItemDto22(val sku: String, val name: String, val qty: Int,
    @MapField(from = "priceCents", converter = Price22::class) val price: String)
object Price22 : Converts<Long, String> { override fun convert(value: Long) = value.toString() }

@MapTo(OrderDto22::class)
data class Order22(val id: OrderId22, val customer: Customer22, val items: List<Item22>, val createdAt: Instant)
data class OrderDto22(val id: String, val customer: CustomerDto22, val items: List<ItemDto22>,
    @MapField(converter = Iso22::class) val createdAt: String)
object Iso22 : Converts<Instant, String> { override fun convert(value: Instant) = value.toString() }


@JvmInline value class OrderId23(val value: String)
@MapTo(AddressDto23::class)
data class Address23(val street: String, val city: String, val zip: String)
data class AddressDto23(val street: String, val city: String, val zip: String)

@MapTo(CustomerDto23::class)
data class Customer23(val name: String, val email: String, val address: Address23)
data class CustomerDto23(val name: String, val email: String, val address: AddressDto23)

@MapTo(ItemDto23::class)
data class Item23(val sku: String, val name: String, val qty: Int, val priceCents: Long)
data class ItemDto23(val sku: String, val name: String, val qty: Int,
    @MapField(from = "priceCents", converter = Price23::class) val price: String)
object Price23 : Converts<Long, String> { override fun convert(value: Long) = value.toString() }

@MapTo(OrderDto23::class)
data class Order23(val id: OrderId23, val customer: Customer23, val items: List<Item23>, val createdAt: Instant)
data class OrderDto23(val id: String, val customer: CustomerDto23, val items: List<ItemDto23>,
    @MapField(converter = Iso23::class) val createdAt: String)
object Iso23 : Converts<Instant, String> { override fun convert(value: Instant) = value.toString() }


@JvmInline value class OrderId24(val value: String)
@MapTo(AddressDto24::class)
data class Address24(val street: String, val city: String, val zip: String)
data class AddressDto24(val street: String, val city: String, val zip: String)

@MapTo(CustomerDto24::class)
data class Customer24(val name: String, val email: String, val address: Address24)
data class CustomerDto24(val name: String, val email: String, val address: AddressDto24)

@MapTo(ItemDto24::class)
data class Item24(val sku: String, val name: String, val qty: Int, val priceCents: Long)
data class ItemDto24(val sku: String, val name: String, val qty: Int,
    @MapField(from = "priceCents", converter = Price24::class) val price: String)
object Price24 : Converts<Long, String> { override fun convert(value: Long) = value.toString() }

@MapTo(OrderDto24::class)
data class Order24(val id: OrderId24, val customer: Customer24, val items: List<Item24>, val createdAt: Instant)
data class OrderDto24(val id: String, val customer: CustomerDto24, val items: List<ItemDto24>,
    @MapField(converter = Iso24::class) val createdAt: String)
object Iso24 : Converts<Instant, String> { override fun convert(value: Instant) = value.toString() }


@JvmInline value class OrderId25(val value: String)
@MapTo(AddressDto25::class)
data class Address25(val street: String, val city: String, val zip: String)
data class AddressDto25(val street: String, val city: String, val zip: String)

@MapTo(CustomerDto25::class)
data class Customer25(val name: String, val email: String, val address: Address25)
data class CustomerDto25(val name: String, val email: String, val address: AddressDto25)

@MapTo(ItemDto25::class)
data class Item25(val sku: String, val name: String, val qty: Int, val priceCents: Long)
data class ItemDto25(val sku: String, val name: String, val qty: Int,
    @MapField(from = "priceCents", converter = Price25::class) val price: String)
object Price25 : Converts<Long, String> { override fun convert(value: Long) = value.toString() }

@MapTo(OrderDto25::class)
data class Order25(val id: OrderId25, val customer: Customer25, val items: List<Item25>, val createdAt: Instant)
data class OrderDto25(val id: String, val customer: CustomerDto25, val items: List<ItemDto25>,
    @MapField(converter = Iso25::class) val createdAt: String)
object Iso25 : Converts<Instant, String> { override fun convert(value: Instant) = value.toString() }


@JvmInline value class OrderId26(val value: String)
@MapTo(AddressDto26::class)
data class Address26(val street: String, val city: String, val zip: String)
data class AddressDto26(val street: String, val city: String, val zip: String)

@MapTo(CustomerDto26::class)
data class Customer26(val name: String, val email: String, val address: Address26)
data class CustomerDto26(val name: String, val email: String, val address: AddressDto26)

@MapTo(ItemDto26::class)
data class Item26(val sku: String, val name: String, val qty: Int, val priceCents: Long)
data class ItemDto26(val sku: String, val name: String, val qty: Int,
    @MapField(from = "priceCents", converter = Price26::class) val price: String)
object Price26 : Converts<Long, String> { override fun convert(value: Long) = value.toString() }

@MapTo(OrderDto26::class)
data class Order26(val id: OrderId26, val customer: Customer26, val items: List<Item26>, val createdAt: Instant)
data class OrderDto26(val id: String, val customer: CustomerDto26, val items: List<ItemDto26>,
    @MapField(converter = Iso26::class) val createdAt: String)
object Iso26 : Converts<Instant, String> { override fun convert(value: Instant) = value.toString() }


@JvmInline value class OrderId27(val value: String)
@MapTo(AddressDto27::class)
data class Address27(val street: String, val city: String, val zip: String)
data class AddressDto27(val street: String, val city: String, val zip: String)

@MapTo(CustomerDto27::class)
data class Customer27(val name: String, val email: String, val address: Address27)
data class CustomerDto27(val name: String, val email: String, val address: AddressDto27)

@MapTo(ItemDto27::class)
data class Item27(val sku: String, val name: String, val qty: Int, val priceCents: Long)
data class ItemDto27(val sku: String, val name: String, val qty: Int,
    @MapField(from = "priceCents", converter = Price27::class) val price: String)
object Price27 : Converts<Long, String> { override fun convert(value: Long) = value.toString() }

@MapTo(OrderDto27::class)
data class Order27(val id: OrderId27, val customer: Customer27, val items: List<Item27>, val createdAt: Instant)
data class OrderDto27(val id: String, val customer: CustomerDto27, val items: List<ItemDto27>,
    @MapField(converter = Iso27::class) val createdAt: String)
object Iso27 : Converts<Instant, String> { override fun convert(value: Instant) = value.toString() }


@JvmInline value class OrderId28(val value: String)
@MapTo(AddressDto28::class)
data class Address28(val street: String, val city: String, val zip: String)
data class AddressDto28(val street: String, val city: String, val zip: String)

@MapTo(CustomerDto28::class)
data class Customer28(val name: String, val email: String, val address: Address28)
data class CustomerDto28(val name: String, val email: String, val address: AddressDto28)

@MapTo(ItemDto28::class)
data class Item28(val sku: String, val name: String, val qty: Int, val priceCents: Long)
data class ItemDto28(val sku: String, val name: String, val qty: Int,
    @MapField(from = "priceCents", converter = Price28::class) val price: String)
object Price28 : Converts<Long, String> { override fun convert(value: Long) = value.toString() }

@MapTo(OrderDto28::class)
data class Order28(val id: OrderId28, val customer: Customer28, val items: List<Item28>, val createdAt: Instant)
data class OrderDto28(val id: String, val customer: CustomerDto28, val items: List<ItemDto28>,
    @MapField(converter = Iso28::class) val createdAt: String)
object Iso28 : Converts<Instant, String> { override fun convert(value: Instant) = value.toString() }


@JvmInline value class OrderId29(val value: String)
@MapTo(AddressDto29::class)
data class Address29(val street: String, val city: String, val zip: String)
data class AddressDto29(val street: String, val city: String, val zip: String)

@MapTo(CustomerDto29::class)
data class Customer29(val name: String, val email: String, val address: Address29)
data class CustomerDto29(val name: String, val email: String, val address: AddressDto29)

@MapTo(ItemDto29::class)
data class Item29(val sku: String, val name: String, val qty: Int, val priceCents: Long)
data class ItemDto29(val sku: String, val name: String, val qty: Int,
    @MapField(from = "priceCents", converter = Price29::class) val price: String)
object Price29 : Converts<Long, String> { override fun convert(value: Long) = value.toString() }

@MapTo(OrderDto29::class)
data class Order29(val id: OrderId29, val customer: Customer29, val items: List<Item29>, val createdAt: Instant)
data class OrderDto29(val id: String, val customer: CustomerDto29, val items: List<ItemDto29>,
    @MapField(converter = Iso29::class) val createdAt: String)
object Iso29 : Converts<Instant, String> { override fun convert(value: Instant) = value.toString() }


@JvmInline value class OrderId30(val value: String)
@MapTo(AddressDto30::class)
data class Address30(val street: String, val city: String, val zip: String)
data class AddressDto30(val street: String, val city: String, val zip: String)

@MapTo(CustomerDto30::class)
data class Customer30(val name: String, val email: String, val address: Address30)
data class CustomerDto30(val name: String, val email: String, val address: AddressDto30)

@MapTo(ItemDto30::class)
data class Item30(val sku: String, val name: String, val qty: Int, val priceCents: Long)
data class ItemDto30(val sku: String, val name: String, val qty: Int,
    @MapField(from = "priceCents", converter = Price30::class) val price: String)
object Price30 : Converts<Long, String> { override fun convert(value: Long) = value.toString() }

@MapTo(OrderDto30::class)
data class Order30(val id: OrderId30, val customer: Customer30, val items: List<Item30>, val createdAt: Instant)
data class OrderDto30(val id: String, val customer: CustomerDto30, val items: List<ItemDto30>,
    @MapField(converter = Iso30::class) val createdAt: String)
object Iso30 : Converts<Instant, String> { override fun convert(value: Instant) = value.toString() }


@JvmInline value class OrderId31(val value: String)
@MapTo(AddressDto31::class)
data class Address31(val street: String, val city: String, val zip: String)
data class AddressDto31(val street: String, val city: String, val zip: String)

@MapTo(CustomerDto31::class)
data class Customer31(val name: String, val email: String, val address: Address31)
data class CustomerDto31(val name: String, val email: String, val address: AddressDto31)

@MapTo(ItemDto31::class)
data class Item31(val sku: String, val name: String, val qty: Int, val priceCents: Long)
data class ItemDto31(val sku: String, val name: String, val qty: Int,
    @MapField(from = "priceCents", converter = Price31::class) val price: String)
object Price31 : Converts<Long, String> { override fun convert(value: Long) = value.toString() }

@MapTo(OrderDto31::class)
data class Order31(val id: OrderId31, val customer: Customer31, val items: List<Item31>, val createdAt: Instant)
data class OrderDto31(val id: String, val customer: CustomerDto31, val items: List<ItemDto31>,
    @MapField(converter = Iso31::class) val createdAt: String)
object Iso31 : Converts<Instant, String> { override fun convert(value: Instant) = value.toString() }


@JvmInline value class OrderId32(val value: String)
@MapTo(AddressDto32::class)
data class Address32(val street: String, val city: String, val zip: String)
data class AddressDto32(val street: String, val city: String, val zip: String)

@MapTo(CustomerDto32::class)
data class Customer32(val name: String, val email: String, val address: Address32)
data class CustomerDto32(val name: String, val email: String, val address: AddressDto32)

@MapTo(ItemDto32::class)
data class Item32(val sku: String, val name: String, val qty: Int, val priceCents: Long)
data class ItemDto32(val sku: String, val name: String, val qty: Int,
    @MapField(from = "priceCents", converter = Price32::class) val price: String)
object Price32 : Converts<Long, String> { override fun convert(value: Long) = value.toString() }

@MapTo(OrderDto32::class)
data class Order32(val id: OrderId32, val customer: Customer32, val items: List<Item32>, val createdAt: Instant)
data class OrderDto32(val id: String, val customer: CustomerDto32, val items: List<ItemDto32>,
    @MapField(converter = Iso32::class) val createdAt: String)
object Iso32 : Converts<Instant, String> { override fun convert(value: Instant) = value.toString() }


@JvmInline value class OrderId33(val value: String)
@MapTo(AddressDto33::class)
data class Address33(val street: String, val city: String, val zip: String)
data class AddressDto33(val street: String, val city: String, val zip: String)

@MapTo(CustomerDto33::class)
data class Customer33(val name: String, val email: String, val address: Address33)
data class CustomerDto33(val name: String, val email: String, val address: AddressDto33)

@MapTo(ItemDto33::class)
data class Item33(val sku: String, val name: String, val qty: Int, val priceCents: Long)
data class ItemDto33(val sku: String, val name: String, val qty: Int,
    @MapField(from = "priceCents", converter = Price33::class) val price: String)
object Price33 : Converts<Long, String> { override fun convert(value: Long) = value.toString() }

@MapTo(OrderDto33::class)
data class Order33(val id: OrderId33, val customer: Customer33, val items: List<Item33>, val createdAt: Instant)
data class OrderDto33(val id: String, val customer: CustomerDto33, val items: List<ItemDto33>,
    @MapField(converter = Iso33::class) val createdAt: String)
object Iso33 : Converts<Instant, String> { override fun convert(value: Instant) = value.toString() }


@JvmInline value class OrderId34(val value: String)
@MapTo(AddressDto34::class)
data class Address34(val street: String, val city: String, val zip: String)
data class AddressDto34(val street: String, val city: String, val zip: String)

@MapTo(CustomerDto34::class)
data class Customer34(val name: String, val email: String, val address: Address34)
data class CustomerDto34(val name: String, val email: String, val address: AddressDto34)

@MapTo(ItemDto34::class)
data class Item34(val sku: String, val name: String, val qty: Int, val priceCents: Long)
data class ItemDto34(val sku: String, val name: String, val qty: Int,
    @MapField(from = "priceCents", converter = Price34::class) val price: String)
object Price34 : Converts<Long, String> { override fun convert(value: Long) = value.toString() }

@MapTo(OrderDto34::class)
data class Order34(val id: OrderId34, val customer: Customer34, val items: List<Item34>, val createdAt: Instant)
data class OrderDto34(val id: String, val customer: CustomerDto34, val items: List<ItemDto34>,
    @MapField(converter = Iso34::class) val createdAt: String)
object Iso34 : Converts<Instant, String> { override fun convert(value: Instant) = value.toString() }


@JvmInline value class OrderId35(val value: String)
@MapTo(AddressDto35::class)
data class Address35(val street: String, val city: String, val zip: String)
data class AddressDto35(val street: String, val city: String, val zip: String)

@MapTo(CustomerDto35::class)
data class Customer35(val name: String, val email: String, val address: Address35)
data class CustomerDto35(val name: String, val email: String, val address: AddressDto35)

@MapTo(ItemDto35::class)
data class Item35(val sku: String, val name: String, val qty: Int, val priceCents: Long)
data class ItemDto35(val sku: String, val name: String, val qty: Int,
    @MapField(from = "priceCents", converter = Price35::class) val price: String)
object Price35 : Converts<Long, String> { override fun convert(value: Long) = value.toString() }

@MapTo(OrderDto35::class)
data class Order35(val id: OrderId35, val customer: Customer35, val items: List<Item35>, val createdAt: Instant)
data class OrderDto35(val id: String, val customer: CustomerDto35, val items: List<ItemDto35>,
    @MapField(converter = Iso35::class) val createdAt: String)
object Iso35 : Converts<Instant, String> { override fun convert(value: Instant) = value.toString() }


@JvmInline value class OrderId36(val value: String)
@MapTo(AddressDto36::class)
data class Address36(val street: String, val city: String, val zip: String)
data class AddressDto36(val street: String, val city: String, val zip: String)

@MapTo(CustomerDto36::class)
data class Customer36(val name: String, val email: String, val address: Address36)
data class CustomerDto36(val name: String, val email: String, val address: AddressDto36)

@MapTo(ItemDto36::class)
data class Item36(val sku: String, val name: String, val qty: Int, val priceCents: Long)
data class ItemDto36(val sku: String, val name: String, val qty: Int,
    @MapField(from = "priceCents", converter = Price36::class) val price: String)
object Price36 : Converts<Long, String> { override fun convert(value: Long) = value.toString() }

@MapTo(OrderDto36::class)
data class Order36(val id: OrderId36, val customer: Customer36, val items: List<Item36>, val createdAt: Instant)
data class OrderDto36(val id: String, val customer: CustomerDto36, val items: List<ItemDto36>,
    @MapField(converter = Iso36::class) val createdAt: String)
object Iso36 : Converts<Instant, String> { override fun convert(value: Instant) = value.toString() }


@JvmInline value class OrderId37(val value: String)
@MapTo(AddressDto37::class)
data class Address37(val street: String, val city: String, val zip: String)
data class AddressDto37(val street: String, val city: String, val zip: String)

@MapTo(CustomerDto37::class)
data class Customer37(val name: String, val email: String, val address: Address37)
data class CustomerDto37(val name: String, val email: String, val address: AddressDto37)

@MapTo(ItemDto37::class)
data class Item37(val sku: String, val name: String, val qty: Int, val priceCents: Long)
data class ItemDto37(val sku: String, val name: String, val qty: Int,
    @MapField(from = "priceCents", converter = Price37::class) val price: String)
object Price37 : Converts<Long, String> { override fun convert(value: Long) = value.toString() }

@MapTo(OrderDto37::class)
data class Order37(val id: OrderId37, val customer: Customer37, val items: List<Item37>, val createdAt: Instant)
data class OrderDto37(val id: String, val customer: CustomerDto37, val items: List<ItemDto37>,
    @MapField(converter = Iso37::class) val createdAt: String)
object Iso37 : Converts<Instant, String> { override fun convert(value: Instant) = value.toString() }


@JvmInline value class OrderId38(val value: String)
@MapTo(AddressDto38::class)
data class Address38(val street: String, val city: String, val zip: String)
data class AddressDto38(val street: String, val city: String, val zip: String)

@MapTo(CustomerDto38::class)
data class Customer38(val name: String, val email: String, val address: Address38)
data class CustomerDto38(val name: String, val email: String, val address: AddressDto38)

@MapTo(ItemDto38::class)
data class Item38(val sku: String, val name: String, val qty: Int, val priceCents: Long)
data class ItemDto38(val sku: String, val name: String, val qty: Int,
    @MapField(from = "priceCents", converter = Price38::class) val price: String)
object Price38 : Converts<Long, String> { override fun convert(value: Long) = value.toString() }

@MapTo(OrderDto38::class)
data class Order38(val id: OrderId38, val customer: Customer38, val items: List<Item38>, val createdAt: Instant)
data class OrderDto38(val id: String, val customer: CustomerDto38, val items: List<ItemDto38>,
    @MapField(converter = Iso38::class) val createdAt: String)
object Iso38 : Converts<Instant, String> { override fun convert(value: Instant) = value.toString() }


@JvmInline value class OrderId39(val value: String)
@MapTo(AddressDto39::class)
data class Address39(val street: String, val city: String, val zip: String)
data class AddressDto39(val street: String, val city: String, val zip: String)

@MapTo(CustomerDto39::class)
data class Customer39(val name: String, val email: String, val address: Address39)
data class CustomerDto39(val name: String, val email: String, val address: AddressDto39)

@MapTo(ItemDto39::class)
data class Item39(val sku: String, val name: String, val qty: Int, val priceCents: Long)
data class ItemDto39(val sku: String, val name: String, val qty: Int,
    @MapField(from = "priceCents", converter = Price39::class) val price: String)
object Price39 : Converts<Long, String> { override fun convert(value: Long) = value.toString() }

@MapTo(OrderDto39::class)
data class Order39(val id: OrderId39, val customer: Customer39, val items: List<Item39>, val createdAt: Instant)
data class OrderDto39(val id: String, val customer: CustomerDto39, val items: List<ItemDto39>,
    @MapField(converter = Iso39::class) val createdAt: String)
object Iso39 : Converts<Instant, String> { override fun convert(value: Instant) = value.toString() }
