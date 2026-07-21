package dev.kmapx.bench

/** Sanity: los tres mappers producen el MISMO OrderDto (si no, la comparación es injusta). */
fun main() {
    val a = Sample.order.toOrderDto()
    val b = HandWritten.map(Sample.order)
    val c = MapStructOrderMapper.INSTANCE.toDto(Sample.order)
    check(a == b) { "kmapx != handWritten\n$a\n$b" }
    check(a == c) { "kmapx != mapStruct\n$a\n$c" }
    println("OK — los tres mappers producen el mismo resultado:")
    println(a)
}
