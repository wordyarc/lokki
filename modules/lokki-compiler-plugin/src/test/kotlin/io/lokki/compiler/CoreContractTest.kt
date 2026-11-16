package io.lokki.compiler

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CoreContractTest {
    @Test
    fun testKindsMatchCore() {
        val core = Class.forName("io.lokki.internal.LokkiIntrinsic\$Kind").enumConstants.map { (it as Enum<*>).name }
        assertEquals(core.sorted(), (IntrinsicKind.entries - IntrinsicKind.UNKNOWN).map { it.name }.sorted())
    }

    @Test
    fun testBuiltinMatchersAreDeclared() {
        val scopes = methods("io.lokki.MatcherScope") + methods("io.lokki.VerificationScope")
        assertDeclared(MatcherNames.BUILTIN, scopes, "matcher scopes")
    }

    @Test
    fun testMatcherFactoriesAreDeclared() {
        val constructed = MatcherNames.BUILTIN - MatcherNames.ANY - MatcherNames.ARRAY
        val factories = listOf(MatcherNames.TYPED_FACTORY, MatcherNames.SPREAD_FACTORY, MatcherNames.VARARG_OF_FACTORY)
        assertDeclared(constructed + factories, methods("io.lokki.internal.Matchers"), "Matchers")
    }

    @Test
    fun testAnswersAreDeclared() {
        val answers = methods("io.lokki.StubScope") + methods("io.lokki.AdditionalAnswerScope")
        assertDeclared(AnswerNames.BLOCKS + AnswerNames.RETURNS_ARGUMENT, answers, "answer scopes")
        assertDeclared(AnswerNames.ORIGINAL_CALLS, methods("io.lokki.AnswerScope"), "AnswerScope")
    }

    @Test
    fun testSlotReadsAreDeclared() {
        val getters = SLOT_READS.map { if (it.startsWith("is")) it else "get${it.replaceFirstChar(Char::uppercase)}" }
        assertDeclared(getters, methods("io.lokki.CapturingSlot"), "CapturingSlot")
    }

    @Test
    fun testEnumEntriesAreDeclared() {
        assertDeclared(VERIFICATION_ORDERINGS.values + UNORDERED, entries("io.lokki.Ordering"), "Ordering")
        val lookupTypes = entries("io.lokki.InjectionLookupType")
        assertDeclared(listOf(LOOKUP_BY_NAME, LOOKUP_BY_TYPE), lookupTypes, "InjectionLookupType")
    }

    private fun methods(type: String): Set<String> = Class.forName(type).declaredMethods.mapTo(HashSet()) { it.name }

    private fun entries(type: String): Set<String> =
        Class.forName(type).enumConstants.mapTo(HashSet()) { (it as Enum<*>).name }

    private fun assertDeclared(names: Collection<String>, declared: Set<String>, owner: String) {
        val missing = names.filterNot { it in declared }
        assertTrue(missing.isEmpty(), "$owner of lokki-core do not declare $missing")
    }
}
