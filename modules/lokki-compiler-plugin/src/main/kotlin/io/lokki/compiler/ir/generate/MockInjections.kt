package io.lokki.compiler.ir.generate

import io.lokki.compiler.CoreFunction
import io.lokki.compiler.IntrinsicKind
import io.lokki.compiler.ir.LokkiIrContext
import io.lokki.compiler.ir.buildLambda
import io.lokki.compiler.ir.collect.ClassMockFields
import io.lokki.compiler.ir.collect.InjectedValue
import io.lokki.compiler.ir.collect.Injection
import io.lokki.compiler.ir.collect.InjectionSource
import io.lokki.compiler.ir.collect.MockClassKey
import io.lokki.compiler.ir.collect.MockSite
import io.lokki.compiler.ir.collect.valueType
import io.lokki.compiler.ir.irCompanionCall
import io.lokki.compiler.ir.irThrowLokkiException
import io.lokki.compiler.ir.irVararg
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.backend.common.lower.irCatch
import org.jetbrains.kotlin.ir.builders.IrBlockBodyBuilder
import org.jetbrains.kotlin.ir.builders.IrBuilderWithScope
import org.jetbrains.kotlin.ir.builders.IrStatementsBuilder
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
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classOrNull
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
            val type = field.property.valueType.makeNotNull() as IrSimpleType
            val site = field.site.takeIf { field.kind == IntrinsicKind.SPY }
            val created = irCreate(type, field.name, site, field.injection!!, mocks) { source, parameterType, subject ->
                irRequired(source.property, parameterType, self, subject)
            }
            val assignment = irSetField(irGet(self), backingField, created)
            +irIfThen(unit, irEqualsNull(irGetField(irGet(self), backingField)), assignment)
        }
        for (field in fields) {
            val injection = field.injection!!
            if (injection.properties.isEmpty()) continue
            val type = field.property.valueType.makeNotNull()
            val backingField = field.property.backingField!!
            val read: IrBuilderWithScope.(InjectionSource) -> IrExpression = { irRead(it.property, self) }
            if (field.kind == IntrinsicKind.SPY) {
                +irWithoutStateChanges(parent) {
                    irInjectProperties(irGetField(irGet(self), backingField), type, injection, read)
                }
            } else {
                irInjectProperties(irGetField(irGet(self), backingField), type, injection, read)
            }
        }
    }

    fun IrBuilderWithScope.irCreate(
        type: IrSimpleType,
        name: String,
        spy: MockSite.Mocked?,
        injection: Injection,
        mocks: Map<MockClassKey, IrClass>,
        read: IrBuilderWithScope.(InjectionSource, IrType, String) -> IrExpression,
    ): IrExpression {
        val constructor = injection.constructor.declaration
        val parameters = constructor.parameters.filter { it.kind == IrParameterKind.Regular }
        val arguments = parameters.indices.map { index ->
            val parameter = injection.constructor.parameters[index]
            val subject = "parameter '${parameter.name}' of '${type.render()}'"
            irArgument(injection.arguments[index], parameter.type, subject, read)
        }
        if (spy != null) {
            val types = spy.types
            val mock = mocks.getValue(spy.key)
            val spec = irSpec(symbols, irString(name), irInt(types.form.spyFlags))
            return irConstruct(mock, types.typeArguments, arguments.filterNotNull() + spec)
        }
        val typeArguments = type.arguments.map { it.typeOrNull ?: context.irBuiltIns.anyNType }
        return irCallConstructor(constructor.symbol, typeArguments).apply {
            this.type = type
            parameters.forEachIndexed { index, parameter -> this.arguments[parameter] = arguments[index] }
        }
    }

    private fun IrBuilderWithScope.irArgument(
        value: InjectedValue,
        type: IrType,
        subject: String,
        read: IrBuilderWithScope.(InjectionSource, IrType, String) -> IrExpression,
    ): IrExpression? =
        when (value) {
            InjectedValue.Default -> null
            is InjectedValue.Single -> read(value.source, type, subject)
            is InjectedValue.All -> {
                val element = (type as IrSimpleType).arguments.single().typeOrNull!!
                val elements = value.sources.map { read(it, element, subject) }
                irCall(symbols.listOf.symbol, context.irBuiltIns.listClass.typeWith(element), listOf(element)).apply {
                    arguments[0] = irVararg(element, elements)
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
            +irIfThen(context.irBuiltIns.unitType, irEqualsNull(irGet(value)), irThrowLokkiException(symbols, message))
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

    fun IrStatementsBuilder<*>.irInjectProperties(
        target: IrExpression,
        type: IrType,
        injection: Injection,
        read: IrBuilderWithScope.(InjectionSource) -> IrExpression,
    ) {
        val unit = context.irBuiltIns.unitType
        val instance = irTemporary(irImplicitCast(target, type))
        for ((property, source) in injection.properties) {
            val injectable = property.declaration
            val current = read(source)
            val value = irTemporary(current, irType = current.type.makeNullable())
            val assignment = irCall(injectable.setter!!).apply {
                arguments[0] = irGet(instance)
                arguments[1] = irImplicitCast(irGet(value), property.type.makeNotNull())
            }
            val assigned = if (injection.overrideValues) {
                assignment
            } else {
                val held = irCall(injectable.getter!!.symbol, property.type).apply { arguments[0] = irGet(instance) }
                val existing = if (injectable.isLateinit) irUninitializedAsNull(held, property.type) else held
                irIfThen(unit, irEqualsNull(existing), assignment)
            }
            +irIfThen(unit, irNotEquals(irGet(value), irNull()), assigned)
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

    fun IrBuilderWithScope.irWithoutStateChanges(
        parent: IrDeclarationParent,
        body: IrBlockBodyBuilder.() -> Unit,
    ): IrExpression {
        val block = context.buildLambda(startOffset, endOffset, context.irBuiltIns.unitType)
        block.parent = parent
        block.body = DeclarationIrBuilder(context, block.symbol, startOffset, endOffset).irBlockBody(body = body)
        return irCompanionCall(symbols[CoreFunction.WITHOUT_STATE_CHANGES], block)
    }
}
