package io.lokki.compiler

import java.lang.reflect.Method
import java.lang.reflect.Modifier
import kotlin.coroutines.Continuation
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
    fun testMatcherFactoriesTakeArgumentsOfMatchers() {
        val scopes = arities("io.lokki.MatcherScope") + arities("io.lokki.VerificationScope")
        val factories = arities("io.lokki.internal.Matchers")
        val constructed = MatcherNames.BUILTIN - MatcherNames.ANY - MatcherNames.ARRAY
        val mismatched = scopes.filter { (name, _) -> name in constructed }.filterNot { it in factories }
        assertTrue(
            mismatched.isEmpty(),
            "Matchers of lokki-core must declare a factory with the parameters of each of $mismatched: " +
                "the compiler plugin passes the arguments of a matcher to its factory by position",
        )
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

    @Test
    fun testClassesAreDeclared() {
        val missing = CoreClass.entries.filter { runCatching { it.load() }.isFailure }
        assertTrue(missing.isEmpty(), "lokki-core does not declare $missing")
    }

    @Test
    fun testEntryPointsAreDeclaredOnce() {
        val ambiguous = CoreFunction.entries.filterNot { entry ->
            val declared = entry.owner.load().declaredMethods.count { method ->
                !Modifier.isPrivate(method.modifiers) && method.declares(entry.functionName) &&
                    method.arity == entry.arity
            }
            declared == 1
        }
        assertTrue(
            ambiguous.isEmpty(),
            "lokki-core must declare each of $ambiguous once: the compiler plugin finds an entry point by its " +
                "owner, name and the number of its parameters, so a new signature needs another number of " +
                "parameters or a new name",
        )
    }

    @Test
    fun testConstructorsAreDeclaredOnce() {
        val ambiguous = CoreConstructor.entries.filterNot { entry ->
            val declared = entry.owner.load().declaredConstructors.count { constructor ->
                !Modifier.isPrivate(constructor.modifiers) && !constructor.isSynthetic &&
                    constructor.parameterCount == entry.arity
            }
            declared == 1
        }
        assertTrue(
            ambiguous.isEmpty(),
            "lokki-core must declare each of $ambiguous once: the compiler plugin finds a constructor by its " +
                "class and the number of its parameters, so a new signature needs another number of parameters",
        )
    }

    private fun CoreClass.load(): Class<*> =
        Class.forName("${id.packageFqName.asString()}.${id.relativeClassName.asString().replace('.', '$')}")

    private fun Method.declares(function: String): Boolean =
        name == function || name.startsWith("$function$") && COMPILER_SUFFIXES.none(name::endsWith)

    private val Method.arity: Int
        get() = parameterTypes.count { it != Continuation::class.java }

    private fun arities(type: String): Set<Pair<String, Int>> =
        Class.forName(type).declaredMethods.mapTo(HashSet()) { it.name to it.parameterCount }

    private fun methods(type: String): Set<String> = Class.forName(type).declaredMethods.mapTo(HashSet()) { it.name }

    private fun entries(type: String): Set<String> =
        Class.forName(type).enumConstants.mapTo(HashSet()) { (it as Enum<*>).name }

    private fun assertDeclared(names: Collection<String>, declared: Set<String>, owner: String) {
        val missing = names.filterNot { it in declared }
        assertTrue(missing.isEmpty(), "$owner of lokki-core do not declare $missing")
    }
}

private val COMPILER_SUFFIXES = listOf("\$default", "\$\$forInline")
