package io.lokki.compiler.ir.generate

import io.lokki.compiler.ir.LokkiIrContext
import io.lokki.compiler.ir.addFriendAwareFakeOverrides
import io.lokki.compiler.ir.irClassReference
import io.lokki.compiler.ir.irInstanceInitializer
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.descriptors.DescriptorVisibility
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.builders.IrBuilderWithScope
import org.jetbrains.kotlin.ir.builders.declarations.addConstructor
import org.jetbrains.kotlin.ir.builders.declarations.buildClass
import org.jetbrains.kotlin.ir.builders.irAnnotation
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.builders.irDelegatingConstructorCall
import org.jetbrains.kotlin.ir.builders.irInt
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrConstructor
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.declarations.name
import org.jetbrains.kotlin.ir.expressions.IrAnnotation
import org.jetbrains.kotlin.ir.util.addChild
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.copyTo
import org.jetbrains.kotlin.ir.util.createThisReceiverParameter
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.load.kotlin.PackagePartClassUtils
import org.jetbrains.kotlin.name.Name

internal fun IrFile.addImplementation(
    lokki: LokkiIrContext,
    name: Name,
    position: IrElement,
    supertype: IrClass,
    visibility: DescriptorVisibility = DescriptorVisibilities.PUBLIC,
    parameters: IrConstructor.() -> Unit = {},
): IrClass {
    val implementation = lokki.pluginContext.irFactory.buildClass {
        startOffset = position.startOffset
        endOffset = position.endOffset
        this.name = name
        origin = lokki.origin
        this.visibility = visibility
    }
    implementation.superTypes = listOf(supertype.defaultType)
    implementation.createThisReceiverParameter()
    addChild(implementation)
    implementation.addPrimaryConstructor(lokki, parameters = parameters)
    implementation.addFriendAwareFakeOverrides(lokki.typeSystem)
    return implementation
}

internal fun IrClass.addPrimaryConstructor(
    lokki: LokkiIrContext,
    visibility: DescriptorVisibility = DescriptorVisibilities.PUBLIC,
    parameters: IrConstructor.() -> Unit = {},
): IrConstructor {
    val owner = this
    val context = lokki.pluginContext
    val constructor = addConstructor {
        startOffset = owner.startOffset
        endOffset = owner.endOffset
        origin = lokki.origin
        this.visibility = visibility
        isPrimary = true
    }
    constructor.parameters()
    constructor.body = DeclarationIrBuilder(context, constructor.symbol).irBlockBody {
        +irDelegatingConstructorCall(context.irBuiltIns.anyClass.owner.constructors.single())
        +irInstanceInitializer(owner)
    }
    return constructor
}

internal fun IrClass.implementing(overridden: IrSimpleFunction, origin: IrDeclarationOrigin): IrSimpleFunction =
    functions.single { overridden.symbol in it.overriddenSymbols }.apply { materializeIn(this@implementing, origin) }

internal fun IrSimpleFunction.materializeIn(
    owner: IrClass,
    origin: IrDeclarationOrigin,
    modality: Modality = Modality.FINAL,
) {
    isFakeOverride = false
    this.origin = origin
    this.modality = modality
    parameters = parameters.map {
        if (it.kind == IrParameterKind.DispatchReceiver) owner.dispatchReceiverFor(this) else it
    }
}

internal fun IrClass.dispatchReceiverFor(function: IrFunction): IrValueParameter =
    thisReceiver!!.let { it.copyTo(function, type = it.type) }

@OptIn(ExperimentalStdlibApi::class)
internal fun IrFile.generatedClassName(owner: Name, kind: String, key: String): Name =
    Name.identifier("$owner\$$kind\$${key.hashCode().toHexString()}\$${PackagePartClassUtils.getFilePartShortName(name)}")

internal fun IrBuilderWithScope.irFactoryAnnotation(constructor: IrConstructor, factory: IrClass, index: Int): IrAnnotation =
    irAnnotation(constructor.symbol).apply {
        arguments[0] = irClassReference(factory.defaultType)
        arguments[1] = irInt(index)
    }
