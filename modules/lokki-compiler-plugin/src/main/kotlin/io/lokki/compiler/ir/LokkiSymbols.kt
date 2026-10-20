package io.lokki.compiler.ir

import io.lokki.compiler.AWAIT_CANCELLATION
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrConstructor
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.util.companionObject
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.erasedUpperBound
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.ir.util.properties
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.util.OperatorNameConventions

internal class LokkiSymbols(private val context: IrPluginContext) {
    private val finder = context.finderForBuiltins()

    val generatedMock by lazy { findClass(INTERNAL_PACKAGE, "GeneratedMock") }
    val state by lazy { generatedMock.properties.single { it.name.asString() == "lokki\$state" } }
    val original by lazy { generatedMock.function("lokki\$original") }
    val coOriginal by lazy { generatedMock.function("lokki\$coOriginal") }
    val relaxed by lazy { generatedMock.function("lokki\$relaxed") }
    val describe by lazy { generatedMock.function("lokki\$describe") }
    val methodDescriptions by lazy { findClass(INTERNAL_PACKAGE, "MethodDescriptions") }
    val methodDescription by lazy { methodDescriptions.function("of") }
    val noRelaxedDefault by lazy { findClass(INTERNAL_PACKAGE, "NoRelaxedDefault") }
    val of by lazy { generatedMock.companionObject()!!.function("of") }
    val withoutStateChanges by lazy { generatedMock.companionObject()!!.function("withoutStateChanges") }
    val evaluate by lazy { generatedMock.companionObject()!!.function("evaluate") }

    val mockDescriptor by lazy { findClass(INTERNAL_PACKAGE, "MockDescriptor") }
    val mockSpec by lazy { findClass(INTERNAL_PACKAGE, "MockSpec") }

    val instanceAllocator by lazy { findClass(INTERNAL_PACKAGE, "InstanceAllocator") }
    val allocate by lazy { instanceAllocator.function("allocate") }

    val mockState by lazy { findClass(INTERNAL_PACKAGE, "MockState") }
    val answer by lazy { mockState.function("answer") }
    val coAnswer by lazy { mockState.function("coAnswer") }
    val noOriginal by lazy { mockState.function("noOriginal") }
    val stub by lazy { mockState.function("stub") }
    val captureDefaults by lazy { mockState.function("captureDefaults") }
    val coCaptureDefaults by lazy { mockState.function("coCaptureDefaults") }
    val exclude by lazy { mockState.function("exclude") }

    val stubScope by lazy { findClass(PACKAGE, "StubScope") }
    val returns by lazy { stubScope.function("returns") }
    val coAnswers by lazy { stubScope.function("coAnswers") }
    val additionalAnswerScope by lazy { findClass(PACKAGE, "AdditionalAnswerScope") }
    val coAndThen by lazy { additionalAnswerScope.function("coAndThen") }
    val answerScope by lazy { findClass(PACKAGE, "AnswerScope") }
    val call by lazy { findClass(PACKAGE, "Call") }
    val awaitCancellation by lazy { finder.findFunctions(AWAIT_CANCELLATION).singleOrNull()?.owner }

    val verification by lazy { findClass(INTERNAL_PACKAGE, "Verification") }
    val expect by lazy { verification.function("expect") }
    val expectLink by lazy { verification.function("expectLink") }
    val check by lazy { verification.function("check") }

    val matcher by lazy { findClass(PACKAGE, "Matcher") }
    val matcherScope by lazy { findClass(PACKAGE, "MatcherScope") }
    val matcherOf by lazy { matcherScope.function("matcherOf") }
    val registrationScope by lazy { findClass(INTERNAL_PACKAGE, "RegistrationScope") }
    val anyMatcher by lazy { findClass(INTERNAL_PACKAGE, "AnyMatcher") }
    val eqMatcher by lazy { findClass(INTERNAL_PACKAGE, "EqMatcher") }
    val arrayMatcher by lazy { findClass(INTERNAL_PACKAGE, "ArrayMatcher") }
    val matchers by lazy { findClass(INTERNAL_PACKAGE, "Matchers") }

    val arrayGet by lazy { context.irBuiltIns.arrayClass.owner.function("get") }
    val intDiv by lazy {
        context.irBuiltIns.intClass.owner.functions.single {
            it.name == OperatorNameConventions.DIV && it.firstParameterClass == context.irBuiltIns.intClass.owner
        }
    }
    val intOr by lazy { context.irBuiltIns.intClass.owner.function("or") }

    val emptyList by lazy { findFunction(COLLECTIONS_PACKAGE, "emptyList") }
    val emptySet by lazy { findFunction(COLLECTIONS_PACKAGE, "emptySet") }
    val emptyMap by lazy { findFunction(COLLECTIONS_PACKAGE, "emptyMap") }
    val emptySequence by lazy { findFunction(SEQUENCES_PACKAGE, "emptySequence") }
    val optionalEmpty by lazy { findClass(JAVA_UTIL_PACKAGE, "Optional").function("empty") }

    val jvmSynthetic by lazy { finder.findConstructors(ClassId(JVM_PACKAGE, Name.identifier("JvmSynthetic"))).single() }
    val apply by lazy { finder.findFunctions(CallableId(KOTLIN_PACKAGE, Name.identifier("apply"))).single() }
    val javaClass by lazy {
        finder.findProperties(CallableId(JVM_PACKAGE, Name.identifier("java"))).single().owner.getter!!
    }

    fun verificationOf(calls: IrClass): IrConstructor =
        verification.constructors.single { it.firstParameterClass?.symbol == calls.symbol }

    fun expectNoCalls(mocks: IrClass): IrSimpleFunction =
        verification.functions.single {
            it.name.asString() == "expectNoCalls" && it.firstParameterClass?.symbol == mocks.symbol
        }

    fun matcherFactory(name: String): IrSimpleFunction = matchers.function(name)

    fun matcherFactory(function: IrSimpleFunction): IrSimpleFunction {
        val factories = matchers.functions.filter { it.name == function.name }.toList()
        return factories.singleOrNull() ?: factories.single { it.firstParameterClass == function.firstParameterClass }
    }

    fun javaUtilClass(name: String): IrClass = findClass(JAVA_UTIL_PACKAGE, name)

    private fun findClass(packageName: FqName, name: String): IrClass =
        finder.findClass(ClassId(packageName, Name.identifier(name)))!!.owner

    private fun findFunction(packageName: FqName, name: String): IrSimpleFunction =
        finder.findFunctions(CallableId(packageName, Name.identifier(name))).single().owner
}

private fun IrClass.function(name: String): IrSimpleFunction = functions.single { it.name.asString() == name }

private val IrFunction.firstParameterClass: IrClass?
    get() = parameters.firstOrNull { it.kind == IrParameterKind.Regular }?.type?.erasedUpperBound

private val KOTLIN_PACKAGE = FqName("kotlin")
private val COLLECTIONS_PACKAGE = FqName("kotlin.collections")
private val SEQUENCES_PACKAGE = FqName("kotlin.sequences")
private val JAVA_UTIL_PACKAGE = FqName("java.util")
private val JVM_PACKAGE = FqName("kotlin.jvm")
private val PACKAGE = FqName("io.lokki")
private val INTERNAL_PACKAGE = FqName("io.lokki.internal")
