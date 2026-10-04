package io.lokki.compiler.ir.generate

import io.lokki.compiler.LokkiPluginKey
import io.lokki.compiler.ir.collect.MockClassKey
import io.lokki.compiler.ir.collect.MockClassUsage
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.backend.common.lower.irThrow
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.incremental.components.LookupTracker
import org.jetbrains.kotlin.ir.builders.declarations.buildClass
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.builders.irCallConstructor
import org.jetbrains.kotlin.ir.builders.irNull
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrOverridableDeclaration
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.name
import org.jetbrains.kotlin.ir.types.IrTypeSystemContextImpl
import org.jetbrains.kotlin.ir.util.addChild
import org.jetbrains.kotlin.ir.util.addFakeOverrides
import org.jetbrains.kotlin.ir.util.addSimpleDelegatingConstructor
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.copyTo
import org.jetbrains.kotlin.ir.util.createThisReceiverParameter
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.isMethodOfAny
import org.jetbrains.kotlin.ir.util.kotlinFqName
import org.jetbrains.kotlin.load.kotlin.PackagePartClassUtils
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

internal class MockClassGenerator(private val context: IrPluginContext, private val lookupTracker: LookupTracker?) {
    private val origin = IrDeclarationOrigin.GeneratedByPlugin(LokkiPluginKey)
    private val typeSystem = IrTypeSystemContextImpl(context.irBuiltIns)
    private val anyConstructor = context.irBuiltIns.anyClass.owner.constructors.single()
    private val lokkiExceptionConstructor by lazy {
        context.finderForBuiltins().findConstructors(LOKKI_EXCEPTION).single()
    }

    fun generate(key: MockClassKey, usage: MockClassUsage): IrClass {
        val mock = context.irFactory.buildClass {
            startOffset = usage.firstCall.startOffset
            endOffset = usage.firstCall.endOffset
            name = key.mockClassName
            origin = this@MockClassGenerator.origin
            visibility = usage.visibility
        }
        mock.superTypes = listOf(key.mockedClass.defaultType)
        mock.createThisReceiverParameter()
        key.file.addChild(mock)
        mock.addSimpleDelegatingConstructor(anyConstructor, context.irBuiltIns, isPrimary = true)
        mock.addFakeOverrides(typeSystem)
        for (member in mock.declarations) {
            when (member) {
                is IrSimpleFunction -> if (!member.isMethodOfAny()) member.implement(mock, key.mockedClass)
                is IrProperty -> {
                    member.materialize()
                    member.getter?.implement(mock, key.mockedClass)
                    member.setter?.implement(mock, key.mockedClass)
                }
                else -> {}
            }
        }
        lookupTracker?.recordMockedTypeLookups(key)
        return mock
    }

    private fun IrSimpleFunction.implement(mock: IrClass, mockedClass: IrClass) {
        materialize()
        parameters = parameters.map { parameter ->
            if (parameter.kind == IrParameterKind.DispatchReceiver) mock.thisReceiver!!.copyTo(this) else parameter
        }
        body = DeclarationIrBuilder(context, symbol).irBlockBody {
            +irThrow(
                irCallConstructor(lokkiExceptionConstructor, emptyList()).apply {
                    arguments[0] = irString("no answer found for ${mockedClass.name}.$name")
                    arguments[1] = irNull()
                }
            )
        }
    }

    private fun IrOverridableDeclaration<*>.materialize() {
        isFakeOverride = false
        origin = this@MockClassGenerator.origin
        modality = Modality.FINAL
    }
}

private val MockClassKey.mockClassName: Name
    get() {
        val hash = mockedClass.kotlinFqName.asString().hashCode().toHexString()
        val filePart = PackagePartClassUtils.getFilePartShortName(file.name)
        return Name.identifier("${mockedClass.name}\$LokkiMock\$$hash\$$filePart")
    }

private val LOKKI_EXCEPTION = ClassId.topLevel(FqName("io.lokki.LokkiException"))
