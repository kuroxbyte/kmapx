package dev.kmapx.bench

import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.BenchmarkMode
import org.openjdk.jmh.annotations.Mode
import org.openjdk.jmh.annotations.OutputTimeUnit
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.infra.Blackhole
import java.util.concurrent.TimeUnit

/**
 * Runtime del mapeo Order -> OrderDto (value class, anidado, colección de 10, enum, converter).
 * kmapx genera Kotlin directo (sin reflection); el baseline es el mismo mapeo a mano. Un empate
 * es el resultado esperado y BUENO: la seguridad en compile-time no cuesta nada en runtime.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
open class MappingBenchmark {

    private val order = Sample.order

    @Benchmark
    fun kmapx(bh: Blackhole) {
        bh.consume(order.toOrderDto())
    }

    @Benchmark
    fun handWritten(bh: Blackhole) {
        bh.consume(HandWritten.map(order))
    }

    @Benchmark
    fun mapStruct(bh: Blackhole) {
        bh.consume(MapStructOrderMapper.INSTANCE.toDto(order))
    }
}
