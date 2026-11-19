package io.lokki.compiler.test.services

import org.jetbrains.kotlin.incremental.components.LookupTracker
import org.jetbrains.kotlin.incremental.components.Position
import org.jetbrains.kotlin.incremental.components.ScopeKind
import org.jetbrains.kotlin.test.model.AfterAnalysisChecker
import org.jetbrains.kotlin.test.model.TestModule
import org.jetbrains.kotlin.test.services.ServiceRegistrationData
import org.jetbrains.kotlin.test.services.TestService
import org.jetbrains.kotlin.test.services.TestServices
import org.jetbrains.kotlin.test.services.assertions
import org.jetbrains.kotlin.test.services.moduleStructure
import org.jetbrains.kotlin.test.services.service
import org.jetbrains.kotlin.test.utils.withExtension
import java.io.File
import java.util.TreeMap
import java.util.TreeSet

internal class RecordedLookups : TestService {
    private val trackers = LinkedHashMap<String, RecordingLookupTracker>()

    fun trackerOf(module: TestModule): LookupTracker = trackers.getOrPut(module.name, ::RecordingLookupTracker)

    fun dump(): String = trackers.flatMap { (module, tracker) -> tracker.lines(module) }.joinToString("\n")
}

internal val TestServices.recordedLookups: RecordedLookups by TestServices.testServiceAccessor()

private class RecordingLookupTracker : LookupTracker {
    private val names = TreeMap<String, TreeMap<String, TreeSet<String>>>()

    override val requiresPosition: Boolean
        get() = false

    override fun record(filePath: String, position: Position, scopeFqName: String, scopeKind: ScopeKind, name: String) {
        names.getOrPut(File(filePath).name, ::TreeMap).getOrPut(scopeFqName, ::TreeSet).add(name)
    }

    override fun clear() {
        names.clear()
    }

    fun lines(module: String): List<String> =
        names.flatMap { (file, scopes) ->
            scopes.map { (scope, names) -> "$module: $file: $scope: ${names.joinToString()}" }
        }
}

internal class LookupsChecker(testServices: TestServices) : AfterAnalysisChecker(testServices) {
    override val additionalServices: List<ServiceRegistrationData>
        get() = listOf(service(::RecordedLookups))

    override fun check(thereWereFailures: Boolean) {
        val expected = testServices.moduleStructure.originalTestDataFiles.first().withExtension("lookups.txt")
        testServices.assertions.assertEqualsToFile(expected, testServices.recordedLookups.dump())
    }
}
