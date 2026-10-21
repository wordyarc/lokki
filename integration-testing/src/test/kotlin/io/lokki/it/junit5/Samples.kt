package io.lokki.it.junit5

import org.junit.jupiter.api.Tag
import org.junit.platform.engine.TestExecutionResult
import org.junit.platform.engine.discovery.DiscoverySelectors.selectClass
import org.junit.platform.testkit.engine.EngineExecutionResults
import org.junit.platform.testkit.engine.EngineTestKit
import org.junit.platform.testkit.engine.Event
import kotlin.reflect.KClass

@Tag("sample")
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class Sample

fun runSample(sample: KClass<*>, vararg parameters: Pair<String, String>): EngineExecutionResults =
    EngineTestKit.engine("junit-jupiter")
        .selectors(selectClass(sample.java))
        .configurationParameters(mapOf(*parameters))
        .execute()

val EngineExecutionResults.testFailures: Map<String, Throwable>
    get() = testEvents().failed().list().associate { it.testDescriptor.displayName to it.throwable }

val EngineExecutionResults.containerFailures: List<Throwable>
    get() = containerEvents().failed().list().map { it.throwable }

val EngineExecutionResults.succeededTests: List<String>
    get() = testEvents().succeeded().list().map { it.testDescriptor.displayName }

private val Event.throwable: Throwable
    get() = getRequiredPayload(TestExecutionResult::class.java).throwable.get()
