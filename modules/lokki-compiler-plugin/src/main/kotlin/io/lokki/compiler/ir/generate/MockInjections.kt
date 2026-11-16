package io.lokki.compiler.ir.generate

import io.lokki.compiler.BuildConfig
import io.lokki.compiler.CoreConstructor
import io.lokki.compiler.CoreFunction
import io.lokki.compiler.IntrinsicKind
import io.lokki.compiler.ir.LokkiIrContext
import io.lokki.compiler.ir.buildLambda
import io.lokki.compiler.ir.collect.ClassMockFields
import io.lokki.compiler.ir.collect.InjectedValue
import io.lokki.compiler.ir.collect.Injection
import io.lokki.compiler.ir.collect.MockClassKey
import io.lokki.compiler.ir.collect.MockField
import io.lokki.compiler.ir.collect.referencedTypeParameters
import io.lokki.compiler.ir.collect.valueType
import io.lokki.compiler.ir.irCompanionCall
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.backend.common.lower.irCatch
import org.jetbrains.kotlin.backend.common.lower.irThrow
import org.jetbrains.kotlin.ir.builders.IrBlockBodyBuilder
import org.jetbrains.kotlin.ir.builders.IrBuilderWithScope
import org.jetbrains.kotlin.ir.builders.declarations.buildVariable
import org.jetbrains.kotlin.ir.builders.irBlock
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irCallConstructor
import org.jetbrains.kotlin.ir.builders.irEqualsNull
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irGetField
import org.jetbrains.kotlin.ir.builders.irIfThen
import org.jetbrains.kotlin.ir.builders.irImplicitCast
import org.jetbrains.kotlin.ir.builders.irInt
import org.jetbrains.kotlin.ir.builders.irNotEquals
import org.jetbrains.kotlin.ir.builders.irNull
import org.jetbrains.kotlin.ir.builders.irSetField
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.builders.irTemporary
import org.jetbrains.kotlin.ir.builders.irTry
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrDeclarationParent
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.impl.IrVarargImpl
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.types.makeNotNull
import org.jetbrains.kotlin.ir.types.makeNullable
import org.jetbrains.kotlin.ir.types.typeOrNull
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.isFakeOverride
import org.jetbrains.kotlin.ir.util.isNullable
import org.jetbrains.kotlin.ir.util.render
import org.jetbrains.kotlin.name.Name

internal class MockInjections(lokki: LokkiIrContext) {
    private val context = lokki.pluginContext
    private val symbols = lokki.symbols
    private val lookupTracker = lokki.lookupTracker

    fun IrBlockBodyBuilder.irInjectMocks(
        owner: ClassMockFields,
        parent: IrDeclarationParent,
        self: IrValueParameter,
        file: IrFile,
        mocks: Map<MockClassKey, IrClass>,
    ) {
        for (target in owner.fields) {
            val injected = target.property.valueType.classOrNull?.owner?.takeIf { target.inject != null } ?: continue
            lookupTracker?.recordMockedTypeLookups(injected, file)
        }
        val fields = owner.injected
        val unit = context.irBuiltIns.unitType
        for (field in fields.filter { !it.isConstructedSpy }) {
            val backingField = field.property.backingField!!
            val created = irCreate(field, field.injection!!, self, mocks)
            val assignment = irSetField(irGet(self), backingField, created)
            +irIfThen(unit, irEqualsNull(irGetField(irGet(self), backingField)), assignment)
        }
        for (field in fields) {
            val injection = field.injection!!
            if (injection.properties.isEmpty()) continue
            if (field.kind == IntrinsicKind.SPY) {
                +irWithoutStateChanges(parent) { irInjectProperties(field, injection, self) }
            } else {
                irInjectProperties(field, injection, self)
            }
        }
    }

    private fun IrBuilderWithScope.irCreate(
        field: MockField,
        injection: Injection,
        self: IrValueParameter,
        mocks: Map<MockClassKey, IrClass>,
    ): IrExpression {
        val type = field.property.valueType.makeNotNull() as IrSimpleType
        val constructor = injection.constructor.declaration
        val parameters = constructor.parameters.filter { it.kind == IrParameterKind.Regular }
        val arguments = parameters.indices.map { index ->
            val parameter = injection.constructor.parameters[index]
            val subject = "parameter '${parameter.name}' of '${type.render()}'"
            irArgument(injection.arguments[index], parameter.type, self, subject)
        }
        if (field.kind == IntrinsicKind.SPY) {
            val site = field.site!!
            val types = site.types
            val mock = mocks.getValue(site.key)
            val typeArguments = types.type.referencedTypeParameters.map { it.defaultType }
            val spec = irSpec(symbols, field.name, irInt(BuildConfig.FLAG_SPY))
            return irConstruct(mock, typeArguments, arguments.filterNotNull() + spec)
        }
        val typeArguments = type.arguments.map { it.typeOrNull ?: context.irBuiltIns.anyNType }
        return irCallConstructor(constructor.symbol, typeArguments).apply {
            this.type = type
            parameters.zip(arguments) { parameter, argument -> this.arguments[parameter.indexInParameters] = argument }
        }
    }

    private fun IrBuilderWithScope.irArgument(
        value: InjectedValue,
        type: IrType,
        self: IrValueParameter,
        subject: String,
    ): IrExpression? =
        when (value) {
            InjectedValue.Default -> null
            is InjectedValue.Single -> irRequired(value.source.property, type, self, subject)
            is InjectedValue.All -> {
                val element = (type as IrSimpleType).arguments.single().typeOrNull!!
                val elements = value.sources.map { irRequired(it.property, element, self, subject) }
                irCall(symbols.listOf.symbol, context.irBuiltIns.listClass.typeWith(element), listOf(element)).apply {
                    val arrayType = context.irBuiltIns.arrayClass.typeWith(element)
                    arguments[0] = IrVarargImpl(startOffset, endOffset, arrayType, element, elements)
                }
            }
        }

    private fun IrBuilderWithScope.irRequired(
        source: IrProperty,
        type: IrType,
        self: IrValueParameter,
        subject: String,
    ): IrExpression {
        val read = irRead(source, self)
        if (type.isNullable() || (!source.isLateinit && !read.type.isNullable())) return read
        return irBlock(resultType = read.type.makeNotNull()) {
            val value = irTemporary(read, irType = read.type.makeNullable())
            val message = "@InjectMocks: property '${source.name}' for $subject is null or not initialized"
            val exception = irCallConstructor(symbols[CoreConstructor.LOKKI_EXCEPTION].symbol, emptyList()).apply {
                arguments[0] = irString(message)
            }
            +irIfThen(context.irBuiltIns.unitType, irEqualsNull(irGet(value)), irThrow(exception))
            +irImplicitCast(irGet(value), read.type.makeNotNull())
        }
    }

    private fun IrBuilderWithScope.irRead(source: IrProperty, self: IrValueParameter): IrExpression {
        val field = source.backingField
        return when {
            !source.isFakeOverride && source.isLateinit && field != null ->
                irGetField(irGet(self), field, field.type.makeNullable())
            else -> irCall(source.getter!!).apply { arguments[0] = irGet(self) }
        }
    }

    private fun IrBlockBodyBuilder.irInjectProperties(
        field: MockField,
        injection: Injection,
        self: IrValueParameter,
    ) {
        val unit = context.irBuiltIns.unitType
        val target = irGetField(irGet(self), field.property.backingField!!)
        val instance = irTemporary(irImplicitCast(target, field.property.valueType.makeNotNull()))
        for ((property, source) in injection.properties) {
            val injectable = property.declaration
            val read = irRead(source.property, self)
            val value = irTemporary(read, irType = read.type.makeNullable())
            val current = irCall(injectable.getter!!.symbol, property.type).apply { arguments[0] = irGet(instance) }
            val existing = if (injectable.isLateinit) irUninitializedAsNull(current, property.type) else current
            val assignment = irCall(injectable.setter!!).apply {
                arguments[0] = irGet(instance)
                arguments[1] = irImplicitCast(irGet(value), property.type.makeNotNull())
            }
            +irIfThen(unit, irNotEquals(irGet(value), irNull()), irIfThen(unit, irEqualsNull(existing), assignment))
        }
    }

    private fun IrBuilderWithScope.irUninitializedAsNull(read: IrExpression, type: IrType): IrExpression {
        val exception = buildVariable(
            scope.getLocalDeclarationParent(),
            startOffset,
            endOffset,
            IrDeclarationOrigin.CATCH_PARAMETER,
            Name.identifier("e"),
            symbols.uninitializedPropertyAccessException.defaultType,
        )
        return irTry(type.makeNullable(), read, listOf(irCatch(exception, irNull())), null)
    }

    private fun IrBuilderWithScope.irWithoutStateChanges(
        parent: IrDeclarationParent,
        body: IrBlockBodyBuilder.() -> Unit,
    ): IrExpression {
        val block = context.buildLambda(startOffset, endOffset, context.irBuiltIns.unitType)
        block.parent = parent
        block.body = DeclarationIrBuilder(context, block.symbol, startOffset, endOffset).irBlockBody(body = body)
        return irCompanionCall(symbols[CoreFunction.WITHOUT_STATE_CHANGES], block)
    }
}
