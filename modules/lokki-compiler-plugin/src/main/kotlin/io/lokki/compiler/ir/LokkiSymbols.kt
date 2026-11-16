package io.lokki.compiler.ir

import io.lokki.compiler.AWAIT_CANCELLATION
import io.lokki.compiler.CoreClasses
import io.lokki.compiler.CoreFunction
import io.lokki.compiler.IncompatibleRuntimeException
import io.lokki.compiler.LOKKI_PACKAGE
import io.lokki.compiler.ORDERING
import io.lokki.compiler.STATE_PROPERTY_NAME
import io.lokki.compiler.internalClassId
import io.lokki.compiler.publicClassId
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrConstructor
import org.jetbrains.kotlin.ir.declarations.IrEnumEntry
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.erasedUpperBound
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.util.primaryConstructor
import org.jetbrains.kotlin.ir.util.properties
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.util.OperatorNameConventions

internal class LokkiSymbols(private val context: IrPluginContext) {
    private val finder = context.finderForBuiltins()

    val isRuntimeAvailable: Boolean
        get() = finder.findClass(CoreClasses.GENERATED_MOCK) != null

    val generatedMock by lazy { coreClass(CoreClasses.GENERATED_MOCK) }
    val state by lazy {
        generatedMock.properties.singleOrNull { it.name.asString() == STATE_PROPERTY_NAME }
            ?: incompatible("'${generatedMock.name}' has no property '$STATE_PROPERTY_NAME'")
    }
    val original by lazy { function(CoreFunction.ORIGINAL) }
    val coOriginal by lazy { function(CoreFunction.CO_ORIGINAL) }
    val relaxed by lazy { function(CoreFunction.RELAXED) }
    val describe by lazy { function(CoreFunction.DESCRIBE) }
    val methodDescriptions by lazy { coreClass(CoreClasses.METHOD_DESCRIPTIONS) }
    val methodDescription by lazy { function(CoreFunction.METHOD_DESCRIPTION) }
    val noRelaxedDefault by lazy { coreClass(internalClassId("NoRelaxedDefault")) }
    val of by lazy { function(CoreFunction.OF) }
    val withoutStateChanges by lazy { function(CoreFunction.WITHOUT_STATE_CHANGES) }
    val evaluate by lazy { function(CoreFunction.EVALUATE) }
    val chain by lazy { function(CoreFunction.CHAIN) }
    val link by lazy { function(CoreFunction.LINK) }
    val childMock by lazy { coreClass(internalClassId("ChildMock")) }
    val redirects by lazy { coreClass(CoreClasses.REDIRECTS) }
    val mockObject by lazy { function(CoreFunction.MOCK_OBJECT) }
    val mockObjectWithBlock by lazy { function(CoreFunction.MOCK_OBJECT_WITH_BLOCK) }
    val mockStatic by lazy { function(CoreFunction.MOCK_STATIC) }
    val mockStaticWithBlock by lazy { function(CoreFunction.MOCK_STATIC_WITH_BLOCK) }
    val redirectOf by lazy { function(CoreFunction.REDIRECT_OF) }
    val staticRedirectOf by lazy { function(CoreFunction.STATIC_REDIRECT_OF) }
    val unmock by lazy { function(CoreFunction.UNMOCK) }
    val redirect by lazy { coreClass(internalClassId("Redirect")) }
    val clearMocks by lazy { publicFunction("clearMocks") { true } }
    val confirmVerified by lazy {
        publicFunction("confirmVerified") { it.parameters.first().varargElementType != null }
    }
    val mockFieldsOwner by lazy { coreClass(CoreClasses.MOCK_FIELDS_OWNER) }
    val initMockFields by lazy { function(CoreFunction.INIT_MOCK_FIELDS) }

    val lokkiException by lazy {
        coreClass(publicClassId("LokkiException")).primaryConstructor
            ?: incompatible("'LokkiException' has no primary constructor")
    }

    val staticInitializer by lazy { coreClass(CoreClasses.STATIC_INITIALIZER) }
    val enterStaticInitializer by lazy { function(CoreFunction.ENTER_STATIC_INITIALIZER) }
    val exitStaticInitializer by lazy { function(CoreFunction.EXIT_STATIC_INITIALIZER) }

    val mockDescriptor by lazy { coreClass(internalClassId("MockDescriptor")) }
    val mockSpec by lazy { coreClass(internalClassId("MockSpec")) }

    val instanceAllocator by lazy { coreClass(CoreClasses.INSTANCE_ALLOCATOR) }
    val allocate by lazy { function(CoreFunction.ALLOCATE) }

    val mockState by lazy { coreClass(CoreClasses.MOCK_STATE) }
    val answer by lazy { function(CoreFunction.ANSWER) }
    val coAnswer by lazy { function(CoreFunction.CO_ANSWER) }
    val noOriginal by lazy { function(CoreFunction.NO_ORIGINAL) }
    val stub by lazy { function(CoreFunction.STUB) }
    val captureDefaults by lazy { function(CoreFunction.CAPTURE_DEFAULTS) }
    val coCaptureDefaults by lazy { function(CoreFunction.CO_CAPTURE_DEFAULTS) }
    val exclude by lazy { function(CoreFunction.EXCLUDE) }
    val mockStateLink by lazy { function(CoreFunction.MOCK_STATE_LINK) }

    val stubScope by lazy { coreClass(CoreClasses.STUB_SCOPE) }
    val returns by lazy { function(CoreFunction.RETURNS) }
    val coAnswers by lazy { function(CoreFunction.CO_ANSWERS) }
    val coAndThen by lazy { function(CoreFunction.CO_AND_THEN) }
    val answerScope by lazy { coreClass(publicClassId("AnswerScope")) }
    val call by lazy { coreClass(publicClassId("Call")) }
    val awaitCancellation by lazy { finder.findFunctions(AWAIT_CANCELLATION).singleOrNull()?.owner }

    val verification by lazy { coreClass(CoreClasses.VERIFICATION) }
    val ordering by lazy { coreClass(ORDERING) }
    val expect by lazy { function(CoreFunction.EXPECT) }
    val expectLink by lazy { function(CoreFunction.EXPECT_LINK) }
    val check by lazy { function(CoreFunction.CHECK) }

    val matcher by lazy { coreClass(publicClassId("Matcher")) }
    val matcherOf by lazy { function(CoreFunction.MATCHER_OF) }
    val any by lazy { function(CoreFunction.ANY) }
    val registrationScope by lazy { coreClass(internalClassId("RegistrationScope")) }
    val anyMatcher by lazy { coreClass(internalClassId("AnyMatcher")) }
    val eqMatcher by lazy { coreClass(internalClassId("EqMatcher")) }
    val arrayMatcher by lazy { coreClass(internalClassId("ArrayMatcher")) }
    val matchers by lazy { coreClass(internalClassId("Matchers")) }

    val arrayGet by lazy { context.irBuiltIns.arrayClass.owner.function("get") }
    val intDiv by lazy {
        context.irBuiltIns.intClass.owner.functions.single {
            it.name == OperatorNameConventions.DIV && it.firstParameterClass == context.irBuiltIns.intClass.owner
        }
    }
    val intOr by lazy { context.irBuiltIns.intClass.owner.function("or") }

    val emptyList by lazy { findFunction(COLLECTIONS_PACKAGE, "emptyList") }
    val listOf by lazy {
        finder.findFunctions(CallableId(COLLECTIONS_PACKAGE, Name.identifier("listOf"))).map { it.owner }
            .single { it.parameters.singleOrNull()?.varargElementType != null }
    }
    val uninitializedPropertyAccessException by lazy {
        findClass(KOTLIN_PACKAGE, "UninitializedPropertyAccessException")
    }
    val emptySet by lazy { findFunction(COLLECTIONS_PACKAGE, "emptySet") }
    val emptyMap by lazy { findFunction(COLLECTIONS_PACKAGE, "emptyMap") }
    val emptySequence by lazy { findFunction(SEQUENCES_PACKAGE, "emptySequence") }
    val optionalEmpty by lazy { findClass(JAVA_UTIL_PACKAGE, "Optional").function("empty") }

    val jvmSynthetic by lazy { finder.findConstructors(ClassId(JVM_PACKAGE, Name.identifier("JvmSynthetic"))).single() }
    val volatile by lazy { finder.findConstructors(ClassId(JVM_PACKAGE, Name.identifier("Volatile"))).single() }
    val jvmDefaultWithoutCompatibility by lazy {
        finder.findConstructors(ClassId(JVM_PACKAGE, Name.identifier("JvmDefaultWithoutCompatibility"))).single()
    }
    val deprecated by lazy { finder.findConstructors(DEPRECATED).single() }
    val hiddenDeprecation by lazy {
        finder.findClass(ClassId(KOTLIN_PACKAGE, Name.identifier("DeprecationLevel")))!!.owner
            .declarations.filterIsInstance<IrEnumEntry>().single { it.name.asString() == "HIDDEN" }
    }
    val apply by lazy { finder.findFunctions(CallableId(KOTLIN_PACKAGE, Name.identifier("apply"))).single() }
    val javaClass by lazy {
        finder.findProperties(CallableId(JVM_PACKAGE, Name.identifier("java")))
            .single { !it.owner.hasAnnotation(DEPRECATED) }.owner.getter!!
    }

    fun verificationOf(calls: IrClass): IrConstructor =
        verification.constructors.singleOrNull { it.firstParameterClass?.symbol == calls.symbol }
            ?: incompatible("'${verification.name}' has no constructor that takes '${calls.name}'")

    fun orderingEntry(name: String): IrEnumEntry =
        ordering.declarations.filterIsInstance<IrEnumEntry>().singleOrNull { it.name.asString() == name }
            ?: incompatible("'${ordering.name}' has no entry '$name'")

    fun expectNoCalls(mocks: IrClass): IrSimpleFunction =
        verification.functions.singleOrNull {
            it.name.asString() == "expectNoCalls" && it.firstParameterClass?.symbol == mocks.symbol
        } ?: incompatible("'${verification.name}' has no function 'expectNoCalls' that takes '${mocks.name}'")

    fun matcherFactory(name: String): IrSimpleFunction =
        matchers.functions.singleOrNull { it.name.asString() == name } ?: noMatcherFactory(name)

    fun matcherFactory(function: IrSimpleFunction): IrSimpleFunction {
        val factories = matchers.functions.filter { it.name == function.name }.toList()
        return factories.singleOrNull()
            ?: factories.singleOrNull { it.firstParameterClass == function.firstParameterClass }
            ?: noMatcherFactory(function.name.asString())
    }

    fun javaUtilClass(name: String): IrClass = findClass(JAVA_UTIL_PACKAGE, name)

    private fun coreClass(id: ClassId): IrClass =
        finder.findClass(id)?.owner ?: incompatible("it has no class '${id.asFqNameString()}'")

    private fun function(entry: CoreFunction): IrSimpleFunction {
        val candidates = coreClass(entry.owner).functions.filter { function ->
            function.name.asString() == entry.functionName &&
                (entry.valueParameters == null || function.regularParameters == entry.valueParameters)
        }.toList()
        val arity = entry.valueParameters?.let { " with $it parameters" }.orEmpty()
        return candidates.singleOrNull() ?: incompatible(
            "'${entry.owner.asFqNameString()}' declares ${candidates.size} functions '${entry.functionName}'$arity, " +
                "and the compiler plugin needs exactly one"
        )
    }

    private fun publicFunction(name: String, matches: (IrSimpleFunction) -> Boolean): IrSimpleFunction =
        finder.findFunctions(CallableId(LOKKI_PACKAGE, Name.identifier(name))).map { it.owner }.singleOrNull(matches)
            ?: incompatible("'${LOKKI_PACKAGE.child(Name.identifier(name))}' is not a single function")

    private fun noMatcherFactory(name: String): Nothing =
        incompatible("'${matchers.name}' has no single factory of the matcher '$name'")

    private fun incompatible(problem: String): Nothing = throw IncompatibleRuntimeException(problem)

    private fun findClass(packageName: FqName, name: String): IrClass =
        finder.findClass(ClassId(packageName, Name.identifier(name)))!!.owner

    private fun findFunction(packageName: FqName, name: String): IrSimpleFunction =
        finder.findFunctions(CallableId(packageName, Name.identifier(name))).single().owner
}

private fun IrClass.function(name: String): IrSimpleFunction = functions.single { it.name.asString() == name }

private val IrFunction.regularParameters: Int
    get() = parameters.count { it.kind == IrParameterKind.Regular }

private val IrFunction.firstParameterClass: IrClass?
    get() = parameters.firstOrNull { it.kind == IrParameterKind.Regular }?.type?.erasedUpperBound

private val KOTLIN_PACKAGE = FqName("kotlin")
private val DEPRECATED = ClassId(KOTLIN_PACKAGE, Name.identifier("Deprecated"))
private val COLLECTIONS_PACKAGE = FqName("kotlin.collections")
private val SEQUENCES_PACKAGE = FqName("kotlin.sequences")
private val JAVA_UTIL_PACKAGE = FqName("java.util")
private val JVM_PACKAGE = FqName("kotlin.jvm")
