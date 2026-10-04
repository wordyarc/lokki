package io.lokki.compiler.ir

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.util.companionObject
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.ir.util.properties
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

internal class LokkiSymbols(context: IrPluginContext) {
    private val finder = context.finderForBuiltins()

    val generatedMock by lazy { findClass(INTERNAL_PACKAGE, "GeneratedMock") }
    val state by lazy { generatedMock.properties.single { it.name.asString() == "lokki\$state" } }
    val of by lazy { generatedMock.companionObject()!!.function("of") }

    val mockDescriptor by lazy { findClass(INTERNAL_PACKAGE, "MockDescriptor") }

    val mockState by lazy { findClass(INTERNAL_PACKAGE, "MockState") }
    val answer by lazy { mockState.function("answer") }
    val stub by lazy { mockState.function("stub") }
    val captureDefaults by lazy { mockState.function("captureDefaults") }

    val matcher by lazy { findClass(PACKAGE, "Matcher") }
    val anyMatcher by lazy { findClass(INTERNAL_PACKAGE, "AnyMatcher") }
    val eqMatcher by lazy { findClass(INTERNAL_PACKAGE, "EqMatcher") }
    val arrayMatcher by lazy { findClass(INTERNAL_PACKAGE, "ArrayMatcher") }
    val matchers by lazy { findClass(INTERNAL_PACKAGE, "Matchers") }

    fun matcherFactory(name: String): IrSimpleFunction = matchers.function(name)

    private fun findClass(packageName: FqName, name: String): IrClass =
        finder.findClass(ClassId(packageName, Name.identifier(name)))!!.owner
}

private fun IrClass.function(name: String): IrSimpleFunction = functions.single { it.name.asString() == name }

private val PACKAGE = FqName("io.lokki")
private val INTERNAL_PACKAGE = FqName("io.lokki.internal")
