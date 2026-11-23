package io.lokki.compiler.ir.collect

import io.lokki.compiler.BuildConfig
import io.lokki.compiler.IntrinsicKind
import io.lokki.compiler.ParameterNames
import io.lokki.compiler.REIFIED_HELPER_MARK
import io.lokki.compiler.ir.LokkiIrContext
import io.lokki.compiler.ir.argument
import io.lokki.compiler.ir.intrinsicKind
import io.lokki.compiler.ir.unwrapTemporary
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.fir.backend.FirMetadataSource
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.IrTypeParameter
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrClassReference
import org.jetbrains.kotlin.ir.expressions.IrVararg
import org.jetbrains.kotlin.ir.symbols.IrTypeParameterSymbol
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classifierOrNull
import org.jetbrains.kotlin.ir.util.resolveFakeOverride
import org.jetbrains.kotlin.ir.visitors.IrVisitorVoid
import org.jetbrains.kotlin.ir.visitors.acceptChildrenVoid
import org.jetbrains.kotlin.ir.visitors.acceptVoid

internal class ReifiedHelpers(lokki: LokkiIrContext) {
    private val registrar = lokki.pluginContext.metadataDeclarationRegistrar
    private val declared = HashMap<IrSimpleFunction, Set<Int>>()

    fun collect(module: IrModuleFragment) {
        val helpers = ArrayList<IrSimpleFunction>()
        module.acceptVoid(object : IrVisitorVoid() {
            override fun visitElement(element: IrElement) {
                element.acceptChildrenVoid(this)
            }

            override fun visitSimpleFunction(declaration: IrSimpleFunction) {
                if (declaration.isHelperCandidate) helpers += declaration
                super.visitSimpleFunction(declaration)
            }
        })
        do {
            var changed = false
            for (helper in helpers) {
                val mocked = helper.collectMockedParameters()
                if (mocked != declared[helper].orEmpty()) {
                    declared[helper] = mocked
                    changed = true
                }
            }
        } while (changed)
        for ((helper, parameters) in declared) {
            if (parameters.isEmpty() || helper.metadata !is FirMetadataSource) continue
            if (DescriptorVisibilities.isPrivate(helper.visibility)) continue
            val data = REIFIED_HELPER_MARK + parameters.sorted().map(Int::toByte).toByteArray()
            registrar.addCustomMetadataExtension(helper, BuildConfig.COMPILER_PLUGIN_ID, data)
        }
    }

    fun concreteTypes(call: IrCall): List<IrType> {
        val parameters = mockedParameters(call.symbol.owner)
        return parameters.mapNotNull { index ->
            call.typeArguments[index]?.takeUnless { it.classifierOrNull is IrTypeParameterSymbol }
        }
    }

    private fun mockedParameters(function: IrSimpleFunction): Set<Int> {
        if (!function.isInline) return emptySet()
        val declaration = function.resolveFakeOverride() ?: function
        declared[declaration]?.let { return it }
        val data = registrar.getCustomMetadataExtension(declaration, BuildConfig.COMPILER_PLUGIN_ID) ?: return emptySet()
        if (data.firstOrNull() != REIFIED_HELPER_MARK[0]) return emptySet()
        return data.drop(REIFIED_HELPER_MARK.size).map(Byte::toInt).toSet()
    }

    private fun IrSimpleFunction.collectMockedParameters(): Set<Int> {
        val mocked = HashSet<Int>()
        val own = typeParameters
        fun add(type: IrType?) {
            val parameter = (type?.classifierOrNull as? IrTypeParameterSymbol)?.owner ?: return
            if (parameter in own) mocked += parameter.index
        }
        acceptChildrenVoid(object : IrVisitorVoid() {
            override fun visitElement(element: IrElement) {
                element.acceptChildrenVoid(this)
            }

            override fun visitCall(expression: IrCall) {
                if (expression.reifiedMockParameter != null) add(expression.typeArguments.single())
                mockedParameters(expression.symbol.owner).forEach { add(expression.typeArguments[it]) }
                super.visitCall(expression)
            }
        })
        return mocked
    }
}

internal val IrCall.reifiedMockParameter: IrTypeParameter?
    get() {
        if (intrinsicKind != IntrinsicKind.MOCK) return null
        if (argument(ParameterNames.MOCKED_CLASS)?.let { it !is IrClassReference } == true) return null
        val interfaces = argument(ParameterNames.MORE_INTERFACES)?.unwrapTemporary() as? IrVararg
        if (interfaces != null && interfaces.elements.isNotEmpty()) return null
        if (argument(ParameterNames.MORE_INTERFACES) != null && interfaces == null) return null
        val parameter = (typeArguments.singleOrNull()?.classifierOrNull as? IrTypeParameterSymbol)?.owner ?: return null
        return parameter.takeIf { it.isReified && (it.parent as? IrSimpleFunction)?.isHelperCandidate == true }
    }

private val IrSimpleFunction.isHelperCandidate: Boolean
    get() = isInline && correspondingPropertySymbol == null && typeParameters.any { it.isReified }
