package io.lokki.benchmarks

import io.lokki.every
import io.lokki.mock
import io.lokki.verify
import io.lokki.verifyOrder
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.BenchmarkMode
import org.openjdk.jmh.annotations.Mode
import org.openjdk.jmh.annotations.OutputTimeUnit
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.Setup
import org.openjdk.jmh.annotations.State
import java.util.concurrent.TimeUnit

@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
open class VerificationBenchmark {
    private val repository = mock<Repository> {
        every { find(any()) } returns FOUND
    }

    @Setup
    fun record() {
        for (id in 0..<RECORDED) repository.find(id)
    }

    @Benchmark
    fun verifyWithMatcher() = verify(exactly = RECORDED) { repository.find(any()) }

    @Benchmark
    fun verifyWithValue() = verify { repository.find(RECORDED - 1) }

    @Benchmark
    fun verifyOrderOfTwoCalls() = verifyOrder {
        repository.find(0)
        repository.find(RECORDED - 1)
    }

    private companion object {
        const val RECORDED = 100
    }
}
