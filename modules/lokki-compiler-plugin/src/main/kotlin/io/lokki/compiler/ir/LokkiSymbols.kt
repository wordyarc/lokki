package io.lokki.compiler.ir

import io.lokki.compiler.AWAIT_CANCELLATION
import io.lokki.compiler.CoreClass
import io.lokki.compiler.CoreConstructor
import io.lokki.compiler.CoreFunction
import io.lokki.compiler.IncompatibleRuntimeException
import io.lokki.compiler.LOKKI_PACKAGE
import io.lokki.compiler.STATE_PROPERTY_NAME
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
import org.jetbrains.kotlin.ir.util.properties
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.util.OperatorNameConventions

internal class LokkiSymbols(private val context: IrPluginContext) {
    private val finder = context.finderForBuiltins()
    private val classes = arrayOfNulls<IrClass>(CoreClass.entries.size)
    private val functions = arrayOfNulls<IrSimpleFunction>(CoreFunction.entries.size)
    private val constructors = arrayOfNulls<IrConstructor>(CoreConstructor.entries.size)

    val isRuntimeAvailable: Boolean
        get() = finder.findClass(CoreClass.GENERATED_MOCK.id) != null

    val state by lazy {
        val generatedMock = this[CoreClass.GENERATED_MOCK]
        generatedMock.properties.singleOrNull { it.name.asString() == STATE_PROPERTY_NAME }
            ?: incompatible("'${generatedMock.name}' has no property '$STATE_PROPERTY_NAME'")
    }
    val clearMocks by lazy { publicFunction("clearMocks") { true } }
    val confirmVerified by lazy {
        publicFunction("confirmVerified") { it.parameters.first().varargElementType != null }
    }
    val awaitCancellation by lazy { finder.findFunctions(AWAIT_CANCELLATION).singleOrNull()?.owner }

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

    operator fun get(entry: CoreClass): IrClass =
        classes[entry.ordinal] ?: find(entry).also { classes[entry.ordinal] = it }

    operator fun get(entry: CoreFunction): IrSimpleFunction =
        functions[entry.ordinal] ?: find(entry).also { functions[entry.ordinal] = it }

    operator fun get(entry: CoreConstructor): IrConstructor =
        constructors[entry.ordinal] ?: find(entry).also { constructors[entry.ordinal] = it }

    fun verificationOf(calls: IrClass): IrConstructor {
        val verification = this[CoreClass.VERIFICATION]
        return verification.constructors
            .singleOrNull { it.regularParameters == 1 && it.firstParameterClass?.symbol == calls.symbol }
            ?: incompatible("'${verification.name}' has no constructor that takes '${calls.name}'")
    }

    fun orderingEntry(name: String): IrEnumEntry {
        val ordering = this[CoreClass.ORDERING]
        return ordering.declarations.filterIsInstance<IrEnumEntry>().singleOrNull { it.name.asString() == name }
            ?: incompatible("'${ordering.name}' has no entry '$name'")
    }

    fun expectNoCalls(mocks: IrClass): IrSimpleFunction {
        val verification = this[CoreClass.VERIFICATION]
        return verification.functions.singleOrNull {
            it.name.asString() == "expectNoCalls" && it.firstParameterClass?.symbol == mocks.symbol
        } ?: incompatible("'${verification.name}' has no function 'expectNoCalls' that takes '${mocks.name}'")
    }

    fun matcherFactory(name: String): IrSimpleFunction =
        this[CoreClass.MATCHERS].functions.singleOrNull { it.name.asString() == name } ?: noMatcherFactory(name)

    fun matcherFactory(function: IrSimpleFunction): IrSimpleFunction {
        val factories = this[CoreClass.MATCHERS].functions.filter { it.name == function.name }.toList()
        return factories.singleOrNull()
            ?: factories.singleOrNull { it.firstParameterClass == function.firstParameterClass }
            ?: noMatcherFactory(function.name.asString())
    }

    fun javaUtilClass(name: String): IrClass = findClass(JAVA_UTIL_PACKAGE, name)

    private fun find(entry: CoreClass): IrClass =
        finder.findClass(entry.id)?.owner ?: incompatible("it has no class '${entry.id.asFqNameString()}'")

    private fun find(entry: CoreFunction): IrSimpleFunction {
        val arity = entry.valueParameters
        val candidates = this[entry.owner].functions
            .filter { it.name.asString() == entry.functionName && it.regularParameters == arity }
            .toList()
        return candidates.singleOrNull() ?: incompatible(
            "'${entry.owner.id.asFqNameString()}' declares ${candidates.size} functions " +
                "'${entry.functionName}' with $arity parameters, and the compiler plugin needs exactly one"
        )
    }

    private fun find(entry: CoreConstructor): IrConstructor {
        val arity = entry.valueParameters
        val candidates = this[entry.owner].constructors.filter { it.regularParameters == arity }.toList()
        return candidates.singleOrNull() ?: incompatible(
            "'${entry.owner.id.asFqNameString()}' declares ${candidates.size} constructors with $arity parameters, " +
                "and the compiler plugin needs exactly one"
        )
    }

    private fun publicFunction(name: String, matches: (IrSimpleFunction) -> Boolean): IrSimpleFunction =
        finder.findFunctions(CallableId(LOKKI_PACKAGE, Name.identifier(name))).map { it.owner }.singleOrNull(matches)
            ?: incompatible("'${LOKKI_PACKAGE.child(Name.identifier(name))}' is not a single function")

    private fun noMatcherFactory(name: String): Nothing =
        incompatible("'${CoreClass.MATCHERS.id.shortClassName}' has no single factory of the matcher '$name'")

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
