package dev.kmapx.bench

/** Baseline: el mapeo escrito a mano — el techo de rendimiento contra el que medir kmapx. */
object HandWritten {
    fun map(order: Order): OrderDto = OrderDto(
        id = order.id.value,
        customer = CustomerDto(
            name = order.customer.name,
            email = order.customer.email,
            address = AddressDto(
                street = order.customer.address.street,
                city = order.customer.address.city,
                zip = order.customer.address.zip,
            ),
        ),
        items = order.items.map {
            ItemDto(sku = it.sku, name = it.name, qty = it.qty, price = PriceUsd.convert(it.priceCents))
        },
        status = when (order.status) {
            Status.NEW -> StatusDto.NEW
            Status.PAID -> StatusDto.PAID
            Status.SHIPPED -> StatusDto.SHIPPED
            Status.CANCELLED -> StatusDto.CANCELLED
        },
        createdAt = instantToIso(order.createdAt),
    )
}

/** Un pedido de muestra con varios items — compartido por todos los benchmarks. */
object Sample {
    val order: Order = Order(
        id = OrderId("ord-100"),
        customer = Customer("Ada Lovelace", "ada@example.com", Address("221B Baker St", "London", "NW1")),
        items = List(10) { i -> Item(sku = "SKU-$i", name = "Item $i", qty = i + 1, priceCents = 1999L + i * 100) },
        status = Status.PAID,
        createdAt = java.time.Instant.ofEpochMilli(1_700_000_000_000),
    )
}
