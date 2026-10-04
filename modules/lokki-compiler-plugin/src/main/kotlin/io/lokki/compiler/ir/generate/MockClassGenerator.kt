package io.lokki.compiler.ir.generate

import io.lokki.compiler.LokkiPluginKey
import io.lokki.compiler.ir.LokkiSymbols
import io.lokki.compiler.ir.collect.MockClassKey
import io.lokki.compiler.ir.collect.MockClassUsage
import io.lokki.compiler.ir.irArrayOf
import io.lokki.compiler.ir.memberKeys
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.incremental.components.LookupTracker
import org.jetbrains.kotlin.ir.builders.declarations.addBackingField
import org.jetbrains.kotlin.ir.builders.declarations.addField
import org.jetbrains.kotlin.ir.builders.declarations.addValueParameter
import org.jetbrains.kotlin.ir.builders.declarations.buildClass
import org.jetbrains.kotlin.ir.builders.irAnnotation
import org.jetbrains.kotlin.ir.builders.irAs
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irCallConstructor
import org.jetbrains.kotlin.ir.builders.irExprBody
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irGetField
import org.jetbrains.kotlin.ir.builders.irInt
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOriginImpl
import org.jetbrains.kotlin.ir.declarations.IrField
import org.jetbrains.kotlin.ir.declarations.IrOverridableDeclaration
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.declarations.name
import org.jetbrains.kotlin.ir.types.IrTypeSystemContextImpl
import org.jetbrains.kotlin.ir.types.isUnit
import org.jetbrains.kotlin.ir.util.addChild
import org.jetbrains.kotlin.ir.util.addFakeOverrides
import org.jetbrains.kotlin.ir.util.addSimpleDelegatingConstructor
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.copyTo
import org.jetbrains.kotlin.ir.util.createThisReceiverParameter
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.isMethodOfAny
import org.jetbrains.kotlin.ir.util.kotlinFqName
import org.jetbrains.kotlin.ir.util.nonDispatchParameters
import org.jetbrains.kotlin.ir.util.primaryConstructor
import org.jetbrains.kotlin.load.kotlin.PackagePartClassUtils
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.util.OperatorNameConventions

internal class MockClassGenerator(
    private val context: IrPluginContext,
    private val symbols: LokkiSymbols,
    private val lookupTracker: LookupTracker?,
) {
    private val origin = IrDeclarationOrigin.GeneratedByPlugin(LokkiPluginKey)
    private val typeSystem = IrTypeSystemContextImpl(context.irBuiltIns)
    private val anyConstructor = context.irBuiltIns.anyClass.owner.constructors.single()

    fun generate(key: MockClassKey, usage: MockClassUsage): IrClass {
        val mock = context.irFactory.buildClass {
            startOffset = usage.firstCall.startOffset
            endOffset = usage.firstCall.endOffset
            name = key.mockClassName
            origin = this@MockClassGenerator.origin
            visibility = usage.visibility
        }
        mock.superTypes = listOf(key.mockedClass.defaultType, symbols.generatedMock.defaultType)
        mock.createThisReceiverParameter()
        key.file.addChild(mock)
        val spec = mock.addSimpleDelegatingConstructor(anyConstructor, context.irBuiltIns, isPrimary = true)
            .addValueParameter("spec", symbols.mockSpec.defaultType, MOCK_PARAMETER)
        mock.addFakeOverrides(typeSystem)

        val stateProperty = mock.declarations.filterIsInstance<IrProperty>()
            .single { symbols.state.symbol in it.overriddenSymbols }
        val (methodsOfAny, declared) = mock.declarations.flatMap { member ->
            when (member) {
                is IrSimpleFunction -> listOf(member)
                is IrProperty -> if (member == stateProperty) emptyList() else listOfNotNull(member.getter, member.setter)
                else -> emptyList()
            }
        }.partition { it.isMethodOfAny() }
        val members = METHODS_OF_ANY.map { name -> methodsOfAny.single { it.name == name } } + declared
        val state = stateProperty.implementState(mock, spec, mock.addDescriptor(key.mockedClass, members))
        members.forEachIndexed { index, member -> member.implement(mock, state, index) }
        lookupTracker?.recordMockedTypeLookups(key)
        return mock
    }

    private fun IrClass.addDescriptor(mockedClass: IrClass, members: List<IrSimpleFunction>): IrField {
        val descriptor = addField {
            name = DESCRIPTOR
            type = symbols.mockDescriptor.defaultType
            origin = this@MockClassGenerator.origin
            visibility = DescriptorVisibilities.PRIVATE
            isStatic = true
            isFinal = true
        }
        val keys = members.map { it.memberKeys }
        val aliases = keys.flatMap { memberKeys -> memberKeys.drop(1).flatMap { listOf(it, memberKeys.first()) } }
        val arrays = listOfNotNull(keys.map { it.first() }, aliases.takeIf { it.isNotEmpty() })
        val constructor = symbols.mockDescriptor.constructors.single { it.parameters.size == arrays.size + 1 }
        descriptor.markSynthetic()
        descriptor.initializer = DeclarationIrBuilder(context, descriptor.symbol).run {
            irExprBody(
                irCallConstructor(constructor.symbol, emptyList()).apply {
                    arguments[0] = irString(mockedClass.name.asString())
                    arrays.forEachIndexed { index, strings ->
                        arguments[index + 1] = irArrayOf(context.irBuiltIns.stringType, strings.map(::irString))
                    }
                }
            )
        }
        return descriptor
    }

    private fun IrProperty.implementState(mock: IrClass, spec: IrValueParameter, descriptor: IrField): IrField {
        materialize()
        val state = addBackingField {
            type = symbols.mockState.defaultType
            isFinal = true
        }
        state.markSynthetic()
        state.initializer = DeclarationIrBuilder(context, state.symbol).run {
            irExprBody(
                irCallConstructor(symbols.mockState.primaryConstructor!!.symbol, emptyList()).apply {
                    arguments[0] = irGet(mock.thisReceiver!!)
                    arguments[1] = irGet(spec)
                    arguments[2] = irGetField(null, descriptor)
                }
            )
        }
        getter!!.run {
            materialize()
            markSynthetic()
            parameters = listOf(mock.thisReceiver!!.copyTo(this))
            body = DeclarationIrBuilder(context, symbol).irBlockBody {
                +irReturn(irGetField(irGet(dispatchReceiverParameter!!), state))
            }
        }
        return state
    }

    private fun IrSimpleFunction.implement(mock: IrClass, state: IrField, member: Int) {
        materialize()
        correspondingPropertySymbol?.owner?.materialize()
        parameters = parameters.map { parameter ->
            if (parameter.kind == IrParameterKind.DispatchReceiver) {
                mock.thisReceiver!!.copyTo(this)
            } else {
                parameter.apply { origin = MOCK_PARAMETER }
            }
        }
        body = DeclarationIrBuilder(context, symbol).irBlockBody {
            val answer = irCall(symbols.answer).apply {
                arguments[0] = irGetField(irGet(dispatchReceiverParameter!!), state)
                arguments[1] = irInt(member)
                arguments[2] = irArrayOf(context.irBuiltIns.anyNType, nonDispatchParameters.map(::irGet))
            }
            if (returnType.isUnit()) +answer else +irReturn(irAs(answer, returnType))
        }
    }

    private fun IrDeclaration.markSynthetic() {
        annotations += DeclarationIrBuilder(context, symbol).irAnnotation(symbols.jvmSynthetic)
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

private val DESCRIPTOR = Name.identifier("lokki\$descriptor")
private val MOCK_PARAMETER = IrDeclarationOriginImpl("LOKKI_MOCK_PARAMETER", isSynthetic = true)
private val METHODS_OF_ANY =
    listOf(OperatorNameConventions.EQUALS, OperatorNameConventions.HASH_CODE, OperatorNameConventions.TO_STRING)
