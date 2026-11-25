package io.lokki.compiler.ir.lower

import io.lokki.compiler.CONSTRUCTORS
import io.lokki.compiler.ir.ConstructorRedirect
import io.lokki.compiler.ir.LokkiIrContext
import io.lokki.compiler.ir.RedirectLookup
import io.lokki.compiler.ir.irArrayOf
import io.lokki.compiler.ir.irInvoke
import io.lokki.compiler.ir.declaredParameters
import org.jetbrains.kotlin.backend.common.IrElementTransformerVoidWithContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.incremental.components.Position
import org.jetbrains.kotlin.incremental.components.ScopeKind
import org.jetbrains.kotlin.ir.builders.IrBuilderWithScope
import org.jetbrains.kotlin.ir.builders.declarations.addValueParameter
import org.jetbrains.kotlin.ir.builders.declarations.buildFun
import org.jetbrains.kotlin.ir.builders.irBlock
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irCallConstructor
import org.jetbrains.kotlin.ir.builders.irEqualsNull
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irIfThenElse
import org.jetbrains.kotlin.ir.builders.irInt
import org.jetbrains.kotlin.ir.builders.irNull
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.builders.irTemporary
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrConstructor
import org.jetbrains.kotlin.ir.declarations.IrDeclarationParent
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrVariable
import org.jetbrains.kotlin.ir.declarations.path
import org.jetbrains.kotlin.ir.expressions.IrConstructorCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrFunctionReference
import org.jetbrains.kotlin.ir.expressions.impl.IrBlockImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrVarargImpl
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.makeNullable
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.constructedClass
import org.jetbrains.kotlin.ir.util.fileOrNull
import org.jetbrains.kotlin.ir.util.isFromJava
import org.jetbrains.kotlin.ir.util.kotlinFqName
import org.jetbrains.kotlin.ir.util.substitute

internal class ConstructorCalls(private val lokki: LokkiIrContext) {
    private val context = lokki.pluginContext

    fun rewrite(file: IrFile, redirects: RedirectLookup = RedirectLookup(lokki, file)) {
        file.transform(Rewriter(file, redirects), null)
    }

    private inner class Rewriter(
        private val file: IrFile,
        private val redirects: RedirectLookup,
    ) : IrElementTransformerVoidWithContext() {
        private val recorded = HashSet<IrClass>()

        override fun visitConstructorCall(expression: IrConstructorCall): IrExpression {
            val call = super.visitConstructorCall(expression) as IrConstructorCall
            val target = call.symbol.owner.constructedClass
            if (recorded.add(target)) recordLookup(target)
            val redirect = redirects.constructors(target) ?: return call
            if (redirect.create(call.symbol.owner) == null) return call
            val scope = currentScope?.scope ?: return call
            val builder = DeclarationIrBuilder(context, scope.scopeOwnerSymbol, call.startOffset, call.endOffset)
            return builder.irRedirected(call, redirect) ?: call
        }

        override fun visitFunctionReference(expression: IrFunctionReference): IrExpression {
            val reference = super.visitFunctionReference(expression) as IrFunctionReference
            val constructor = reference.symbol.owner as? IrConstructor ?: return reference
            val target = constructor.constructedClass
            if (recorded.add(target)) recordLookup(target)
            val redirect = redirects.constructors(target) ?: return reference
            if (redirect.create(constructor) == null) return reference
            val scope = currentScope?.scope ?: return reference
            val builder = DeclarationIrBuilder(context, scope.scopeOwnerSymbol, reference.startOffset, reference.endOffset)
            return builder.irAdapted(reference, constructor, redirect, scope.getLocalDeclarationParent())
        }

        private fun recordLookup(target: IrClass) {
            if (target.kind != ClassKind.CLASS || target.isFromJava() || target.fileOrNull === file) return
            val scope = target.kotlinFqName.asString()
            if (scope.startsWith("kotlin.")) return
            lokki.lookupTracker?.record(
                file.path,
                Position.NO_POSITION,
                scope,
                ScopeKind.CLASSIFIER,
                CONSTRUCTORS.asString(),
            )
        }
    }

    private fun IrBuilderWithScope.irAdapted(
        reference: IrFunctionReference,
        constructor: IrConstructor,
        redirect: ConstructorRedirect,
        parent: IrDeclarationParent,
    ): IrExpression {
        val owner = constructor.constructedClass
        val typeArguments = reference.typeArguments.take(owner.typeParameters.size).map { it!! }
        val substitution = owner.typeParameters.map { it.symbol }.zip(typeArguments).toMap()
        val adapter = context.irFactory.buildFun {
            startOffset = reference.startOffset
            endOffset = reference.endOffset
            name = owner.name
            origin = lokki.origin
            visibility = DescriptorVisibilities.LOCAL
            returnType = owner.symbol.typeWith(typeArguments)
        }
        adapter.parent = parent
        val parameters = constructor.declaredParameters
        val values = parameters.map { adapter.addValueParameter(it.name, it.type.substitute(substitution)) }
        adapter.body = DeclarationIrBuilder(context, adapter.symbol, startOffset, endOffset).irBlockBody {
            val call = irCallConstructor(constructor.symbol, typeArguments).apply { type = adapter.returnType }
            parameters.forEachIndexed { index, parameter -> call.arguments[parameter] = irGet(values[index]) }
            +irReturn(irRedirected(call, redirect) ?: call)
        }
        reference.reflectionTarget = constructor.symbol
        reference.symbol = adapter.symbol
        reference.typeArguments.clear()
        return IrBlockImpl(
            reference.startOffset,
            reference.endOffset,
            reference.type,
            null,
            listOf(adapter, reference),
        )
    }

    private fun IrBuilderWithScope.irRedirected(call: IrConstructorCall, redirect: ConstructorRedirect): IrExpression? {
        val constructor = call.symbol.owner
        val parameters = constructor.declaredParameters
        val omitted = parameters.filter { call.arguments[it] == null && it.varargElementType == null }
        val construct = redirect.construct(constructor)
        if (omitted.isNotEmpty() && construct == null) return null
        val create = redirect.create(constructor)!!
        val typeArguments = call.typeArguments.take(constructor.constructedClass.typeParameters.size).map { it!! }
        val classParameters = constructor.constructedClass.typeParameters.map { it.symbol }
        val substitution = classParameters.zip(typeArguments).toMap()
        return irBlock(resultType = call.type) {
            val values = parameters.map { parameter ->
                val value = call.arguments[parameter] ?: parameter.varargElementType?.let {
                    IrVarargImpl(startOffset, endOffset, parameter.type.substitute(substitution), it.substitute(substitution))
                }
                value?.let { irTemporary(it) }
            }
            parameters.forEachIndexed { index, parameter ->
                call.arguments[parameter] = values[index]?.let(::irGet)
            }
            val installed = irTemporary(
                irInvoke(redirect.property.getter!!),
                irType = redirect.property.getter!!.returnType.makeNullable(),
            )
            val redirected = if (omitted.isEmpty()) {
                irCall(create.symbol, call.type, typeArguments).apply {
                    arguments[0] = irGet(installed)
                    values.forEachIndexed { index, value -> arguments[index + 1] = irGet(value!!) }
                }
            } else {
                irConstruct(construct!!.symbol, call, typeArguments, installed, values, parameters.size)
            }
            +irIfThenElse(call.type, irEqualsNull(irGet(installed)), call, redirected)
        }
    }

    private fun IrBuilderWithScope.irConstruct(
        construct: IrSimpleFunctionSymbol,
        call: IrConstructorCall,
        typeArguments: List<IrType>,
        installed: IrVariable,
        values: List<IrVariable?>,
        size: Int,
    ): IrExpression {
        val anyN = context.irBuiltIns.anyNType
        val mask = (0 until size).filter { values[it] == null }.fold(0) { mask, index -> mask or (1 shl index) }
        return irCall(construct, call.type, typeArguments).apply {
            arguments[0] = irGet(installed)
            arguments[1] = irArrayOf(anyN, values.map { value -> value?.let(::irGet) ?: irNull() })
            arguments[2] = irInt(mask)
        }
    }
}
