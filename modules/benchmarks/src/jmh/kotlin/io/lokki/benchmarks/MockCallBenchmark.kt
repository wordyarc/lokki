package io.lokki.benchmarks

import io.lokki.clearMocks
import io.lokki.every
import io.lokki.excludeRecords
import io.lokki.mock
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.BenchmarkMode
import org.openjdk.jmh.annotations.Mode
import org.openjdk.jmh.annotations.OperationsPerInvocation
import org.openjdk.jmh.annotations.OutputTimeUnit
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.infra.Blackhole
import java.util.concurrent.TimeUnit

@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@OperationsPerInvocation(CALLS)
open class MockCallBenchmark {
    private val names = Array(CALLS) { "name $it" }

    private val fakeRepository: Repository = FakeRepository()

    private val returning = mock<Repository> {
        every { find(any()) } returns FOUND
        every { findByName(any()) } returns FOUND
    }

    private val answering = mock<Repository> {
        every { find(any()) } answers { FOUND }
    }

    private val unrecorded = mock<Repository> {
        every { find(any()) } returns FOUND
    }

    init {
        excludeRecords { unrecorded.find(any()) }
    }

    @Benchmark
    fun fake(blackhole: Blackhole) {
        for (index in 0..<CALLS) blackhole.consume(fakeRepository.find(FIRST_ID + index))
    }

    @Benchmark
    fun returnsWithIntArgument(blackhole: Blackhole) = calls(returning, blackhole) { find(FIRST_ID + it) }

    @Benchmark
    fun returnsWithStringArgument(blackhole: Blackhole) = calls(returning, blackhole) { findByName(names[it]) }

    @Benchmark
    fun answers(blackhole: Blackhole) = calls(answering, blackhole) { find(FIRST_ID + it) }

    @Benchmark
    fun returnsWithoutRecording(blackhole: Blackhole) = calls(unrecorded, blackhole) { find(FIRST_ID + it) }

    private inline fun calls(mock: Repository, blackhole: Blackhole, call: Repository.(Int) -> String) {
        for (index in 0..<CALLS) blackhole.consume(mock.call(index))
        clearMocks(mock, answers = false, exclusionRules = false)
    }
}
