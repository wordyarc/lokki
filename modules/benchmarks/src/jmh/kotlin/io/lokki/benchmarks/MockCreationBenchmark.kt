package io.lokki.benchmarks

import io.lokki.every
import io.lokki.mock
import io.lokki.spy
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.BenchmarkMode
import org.openjdk.jmh.annotations.Mode
import org.openjdk.jmh.annotations.OutputTimeUnit
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.State
import java.util.concurrent.TimeUnit

@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
open class MockCreationBenchmark {
    @Benchmark
    fun mockOfInterface(): Repository = mock()

    @Benchmark
    fun mockOfClass(): Ledger = mock()

    @Benchmark
    fun relaxedMock(): Repository = mock(relaxed = true)

    @Benchmark
    fun mockWithStub(): Repository = mock { every { find(any()) } returns FOUND }

    @Benchmark
    fun spyOfNewObject(): Ledger = spy(Ledger("owner"))
}
