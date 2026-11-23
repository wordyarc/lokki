package io.lokki.compiler.ir.generate

import io.lokki.compiler.BuildConfig
import io.lokki.compiler.MockablePatterns
import io.lokki.compiler.ir.LokkiIrContext
import io.lokki.compiler.ir.irHidden
import io.lokki.compiler.ir.isClassFinalizer
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.ir.builders.irAnnotation
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrGetValue
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.types.typeOrNull
import org.jetbrains.kotlin.ir.util.classIdOrFail
import org.jetbrains.kotlin.ir.util.deepCopyWithSymbols
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.util.isLocal
import org.jetbrains.kotlin.ir.util.nonDispatchParameters
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

internal class PrivateMembers(private val lokki: LokkiIrContext, private val mockable: MockablePatterns) {
    private val context = lokki.pluginContext
    private val symbols = lokki.symbols

    fun generate(module: IrModuleFragment) {
        if (!lokki.compat.registersClassesAsMetadataVisible) return
        module.files.forEach { file -> file.declarations.forEach(::generate) }
    }

    private fun generate(declaration: IrDeclaration) {
        if (declaration !is IrClass || declaration.origin != IrDeclarationOrigin.DEFINED) return
        declaration.declarations.toList().forEach(::generate)
        if (!declaration.isMockableBy(mockable)) return
        declaration.declarations.filterIsInstance<IrSimpleFunction>()
            .filter { it.isPrivateInterceptable && !it.isTailrec && !it.isClassFinalizer }
            .forEach { declaration.addHook(it) }
    }

    private fun IrClass.addHook(function: IrSimpleFunction) {
        val hook = function.deepCopyWithSymbols(this)
        hook.name = privateHookName(function)
        hook.origin = lokki.origin
        hook.visibility = DescriptorVisibilities.PROTECTED
        hook.modality = Modality.OPEN
        hook.overriddenSymbols = emptyList()
        hook.metadata = null
        hook.attributeOwnerId = hook
        hook.parameters.forEach { it.defaultValue = null }
        hook.annotations = DeclarationIrBuilder(context, hook.symbol).run {
            listOf(irHidden(symbols, HOOK_MESSAGE), irAnnotation(symbols.jvmSynthetic))
        }
        hook.callsItselfDirectly(this)
        declarations += hook
        function.body = DeclarationIrBuilder(context, function.symbol).irBlockBody {
            +irReturn(
                irCall(hook.symbol, function.returnType, function.typeParameters.map { it.defaultType }).apply {
                    arguments[0] = irGet(function.dispatchReceiverParameter!!)
                    function.nonDispatchParameters.forEach { parameter ->
                        arguments[parameter.indexInParameters] = irGet(parameter)
                    }
                }
            )
        }
        context.metadataDeclarationRegistrar.registerFunctionAsMetadataVisible(hook)
    }

    private fun IrSimpleFunction.callsItselfDirectly(owner: IrClass) {
        val self = dispatchReceiverParameter!!.symbol
        val hook = symbol
        body?.transformChildrenVoid(object : IrElementTransformerVoid() {
            override fun visitCall(expression: IrCall): IrExpression {
                val receiver = expression.dispatchReceiver as? IrGetValue
                if (expression.symbol == hook && receiver?.symbol == self) expression.superQualifierSymbol = owner.symbol
                return super.visitCall(expression)
            }
        })
    }
}

internal val IrSimpleFunction.isPrivateInterceptable: Boolean
    get() = origin == IrDeclarationOrigin.DEFINED && !isFakeOverride && correspondingPropertySymbol == null &&
        dispatchReceiverParameter != null && DescriptorVisibilities.isPrivate(visibility) && body != null &&
        !isInline && !isExternal && !isExpect && !isComposable && hasExposableSignature

internal val IrSimpleFunction.isComposable: Boolean
    get() = hasAnnotation(COMPOSABLE)

private val IrSimpleFunction.hasExposableSignature: Boolean
    get() = returnType.isExposable && nonDispatchParameters.all { it.type.isExposable } &&
        typeParameters.all { parameter -> parameter.superTypes.all { it.isExposable } }

private val IrType.isExposable: Boolean
    get() {
        val owner = classOrNull?.owner
        val hidden = owner != null && generateSequence(owner) { it.parent as? IrClass }.any {
            it.isLocal || DescriptorVisibilities.isPrivate(it.visibility)
        }
        return !hidden && (this as? IrSimpleType)?.arguments.orEmpty().all { it.typeOrNull?.isExposable != false }
    }

@OptIn(ExperimentalStdlibApi::class)
internal fun IrClass.privateHookName(function: IrSimpleFunction): Name =
    Name.identifier("${function.name}${BuildConfig.PRIVATE_HOOK_INFIX}${classIdOrFail.asString().hashCode().toHexString()}")

private val COMPOSABLE = FqName("androidx.compose.runtime.Composable")
private const val HOOK_MESSAGE = "Generated by lokki for private members of mockable classes"
