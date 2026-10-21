package io.lokki.compiler.ir.generate

import io.lokki.compiler.BuildConfig
import io.lokki.compiler.INTERCEPTION_MARK
import io.lokki.compiler.LokkiPluginKey
import io.lokki.compiler.Mockable
import io.lokki.compiler.REDIRECT
import io.lokki.compiler.REDIRECT_PROPERTY
import io.lokki.compiler.ir.FriendAwareFakeOverrides
import io.lokki.compiler.ir.LokkiSymbols
import io.lokki.compiler.ir.irHidden
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.descriptors.DescriptorVisibility
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.ir.builders.declarations.addBackingField
import org.jetbrains.kotlin.ir.builders.declarations.addFunction
import org.jetbrains.kotlin.ir.builders.declarations.addGetter
import org.jetbrains.kotlin.ir.builders.declarations.addProperty
import org.jetbrains.kotlin.ir.builders.declarations.addSetter
import org.jetbrains.kotlin.ir.builders.declarations.IrFunctionBuilder
import org.jetbrains.kotlin.ir.builders.declarations.addValueParameter
import org.jetbrains.kotlin.ir.builders.declarations.buildClass
import org.jetbrains.kotlin.ir.builders.irAnnotation
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irEqualsNull
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irGetField
import org.jetbrains.kotlin.ir.builders.irGetObjectValue
import org.jetbrains.kotlin.ir.builders.irIfThen
import org.jetbrains.kotlin.ir.builders.irImplicitCast
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.builders.irSetField
import org.jetbrains.kotlin.ir.builders.irTemporary
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrField
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.overrides.IrFakeOverrideBuilder
import org.jetbrains.kotlin.ir.types.IrTypeSystemContextImpl
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.types.makeNullable
import org.jetbrains.kotlin.ir.util.addChild
import org.jetbrains.kotlin.ir.util.classIdOrFail
import org.jetbrains.kotlin.ir.util.copyTo
import org.jetbrains.kotlin.ir.util.copyTypeParametersFrom
import org.jetbrains.kotlin.ir.util.createThisReceiverParameter
import org.jetbrains.kotlin.ir.util.deepCopyWithSymbols
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.util.isMethodOfAny
import org.jetbrains.kotlin.ir.util.makeTypeParameterSubstitutionMap
import org.jetbrains.kotlin.ir.util.nonDispatchParameters
import org.jetbrains.kotlin.ir.util.parentAsClass
import org.jetbrains.kotlin.ir.util.substitute
import org.jetbrains.kotlin.load.java.JvmAbi
import org.jetbrains.kotlin.name.Name

internal class InterceptionPoints(
    private val context: IrPluginContext,
    private val symbols: LokkiSymbols,
    private val mockable: Mockable,
) {
    private val origin = IrDeclarationOrigin.GeneratedByPlugin(LokkiPluginKey)
    private val typeSystem = IrTypeSystemContextImpl(context.irBuiltIns)

    fun generate(module: IrModuleFragment) {
        val objects = module.files.flatMap { file -> file.declarations.flatMap(::objects) }
        objects.forEach(::generate)
    }

    private fun objects(declaration: IrDeclaration): List<IrClass> {
        if (declaration !is IrClass || declaration.origin != IrDeclarationOrigin.DEFINED) return emptyList()
        if (DescriptorVisibilities.isPrivate(declaration.visibility)) return emptyList()
        val nested = declaration.declarations.flatMap(::objects)
        return if (declaration.isInterceptable) nested + declaration else nested
    }

    private val IrClass.isInterceptable: Boolean
        get() = kind == ClassKind.OBJECT && !isExpect && (matches() || isCompanion && parentAsClass.matches())

    private fun IrClass.matches(): Boolean =
        mockable.matches(classIdOrFail) || mockable.names.any { hasAnnotation(it) }

    private fun generate(target: IrClass) {
        val functions = target.declarations.filterIsInstance<IrSimpleFunction>().filter { it.isInterceptable }
        val properties = target.declarations.filterIsInstance<IrProperty>().filter { it.isInterceptable }
        val redirect = target.addRedirect()
        val field = target.addRedirectProperty(redirect)
        for (function in functions) {
            val member = redirect.addFunction(
                function.name.asString(),
                context.irBuiltIns.unitType,
                Modality.OPEN,
                isSuspend = function.isSuspend,
                origin = origin,
            )
            target.intercept(function, "${function.name}\$lokkiOriginal", member, field)
            mark(function)
        }
        for (property in properties) {
            val setter = property.setter?.takeIf { it.isInterceptableAccessor }
            val member = redirect.addProperty {
                name = property.name
                isVar = setter != null
                modality = Modality.OPEN
                origin = this@InterceptionPoints.origin
            }
            val accessor: IrFunctionBuilder.() -> Unit = {
                modality = Modality.OPEN
                origin = this@InterceptionPoints.origin
            }
            target.intercept(property.getter!!, "${property.name}\$lokkiGet", member.addGetter(accessor), field)
            if (setter != null) {
                target.intercept(setter, "${property.name}\$lokkiSet", member.addSetter(accessor), field)
            }
            mark(property)
        }
        IrFakeOverrideBuilder(typeSystem, FriendAwareFakeOverrides, emptyList())
            .buildFakeOverridesForClassUsingOverriddenSymbols(redirect, emptyMap(), compatibilityMode = false)
            .forEach(redirect::addChild)
        context.metadataDeclarationRegistrar.registerClassAsMetadataVisible(redirect)
        mark(target)
    }

    private fun IrClass.intercept(function: IrSimpleFunction, name: String, member: IrSimpleFunction, field: IrField) {
        val original = addOriginal(function, Name.identifier(name))
        member.copySignatureOf(function)
        member.forward(original, this)
        function.hook(field, original, member)
    }

    private val IrSimpleFunction.isInterceptable: Boolean
        get() = origin == IrDeclarationOrigin.DEFINED && !isFakeOverride && !isMethodOfAny() &&
            isInterceptableAccessor

    private val IrSimpleFunction.isInterceptableAccessor: Boolean
        get() = body != null && visibility.isInterceptable && !isInline && !isExternal && !isExpect

    private val IrProperty.isInterceptable: Boolean
        get() {
            val getter = getter ?: return false
            return origin == IrDeclarationOrigin.DEFINED && !isFakeOverride && visibility.isInterceptable &&
                !isConst && !isLateinit && !isExternal && !isExpect && getter.isInterceptableAccessor &&
                getter.typeParameters.isEmpty() && !hasAnnotation(JvmAbi.JVM_FIELD_ANNOTATION_FQ_NAME) &&
                backingField?.hasAnnotation(JvmAbi.JVM_FIELD_ANNOTATION_FQ_NAME) != true
        }

    private val DescriptorVisibility.isInterceptable: Boolean
        get() = !DescriptorVisibilities.isPrivate(this) && this != DescriptorVisibilities.PROTECTED

    private fun IrClass.addRedirect(): IrClass {
        val redirect = context.irFactory.buildClass {
            startOffset = this@addRedirect.startOffset
            endOffset = this@addRedirect.endOffset
            name = REDIRECT
            kind = ClassKind.INTERFACE
            modality = Modality.ABSTRACT
            origin = this@InterceptionPoints.origin
        }
        redirect.createThisReceiverParameter()
        redirect.superTypes = listOf(context.irBuiltIns.anyType)
        redirect.annotations = DeclarationIrBuilder(context, redirect.symbol).run {
            listOf(irHidden(symbols, HIDDEN_MESSAGE), irAnnotation(symbols.jvmDefaultWithoutCompatibility))
        }
        addChild(redirect)
        return redirect
    }

    private fun IrClass.addRedirectProperty(redirect: IrClass): IrField {
        val type = redirect.defaultType.makeNullable()
        val property = addProperty {
            name = REDIRECT_PROPERTY
            isVar = true
            origin = this@InterceptionPoints.origin
        }
        val field = property.addBackingField {
            this.type = type
            origin = this@InterceptionPoints.origin
        }
        field.annotations = listOf(DeclarationIrBuilder(context, field.symbol).irAnnotation(symbols.volatile))
        property.addGetter {
            returnType = type
            origin = this@InterceptionPoints.origin
        }.apply {
            parameters = listOf(dispatchReceiverFor(this))
            annotations = listOf(DeclarationIrBuilder(context, symbol).irAnnotation(symbols.jvmSynthetic))
            body = DeclarationIrBuilder(context, symbol).irBlockBody {
                +irReturn(irGetField(irGet(dispatchReceiverParameter!!), field))
            }
        }
        property.addSetter {
            returnType = context.irBuiltIns.unitType
            origin = this@InterceptionPoints.origin
        }.apply {
            parameters = listOf(dispatchReceiverFor(this))
            val value = addValueParameter("value", type)
            annotations = listOf(DeclarationIrBuilder(context, symbol).irAnnotation(symbols.jvmSynthetic))
            body = DeclarationIrBuilder(context, symbol).irBlockBody {
                +irSetField(irGet(dispatchReceiverParameter!!), field, irGet(value))
            }
        }
        property.annotations = listOf(DeclarationIrBuilder(context, property.symbol).irHidden(symbols, HIDDEN_MESSAGE))
        context.metadataDeclarationRegistrar.registerPropertyAsMetadataVisible(property)
        return field
    }

    private fun IrClass.addOriginal(function: IrSimpleFunction, name: Name): IrSimpleFunction {
        val original = function.deepCopyWithSymbols(this)
        original.name = name
        original.origin = origin
        original.visibility = DescriptorVisibilities.PUBLIC
        original.modality = Modality.FINAL
        original.overriddenSymbols = emptyList()
        original.correspondingPropertySymbol = null
        original.metadata = null
        original.attributeOwnerId = original
        original.annotations = listOf(DeclarationIrBuilder(context, original.symbol).irAnnotation(symbols.jvmSynthetic))
        original.parameters.forEach { it.defaultValue = null }
        declarations += original
        return original
    }

    private fun IrSimpleFunction.copySignatureOf(function: IrSimpleFunction) {
        copyTypeParametersFrom(function)
        val substitution = makeTypeParameterSubstitutionMap(function, this)
        val receiver = parentAsClass.thisReceiver!!
        parameters = listOf(receiver.copyTo(this, type = receiver.type)) + function.nonDispatchParameters.map {
            it.copyTo(
                this,
                type = it.type.substitute(substitution),
                varargElementType = it.varargElementType?.substitute(substitution),
                defaultValue = null,
            )
        }
        returnType = function.returnType.substitute(substitution)
    }

    private fun IrSimpleFunction.forward(original: IrSimpleFunction, target: IrClass) {
        body = DeclarationIrBuilder(context, symbol).irBlockBody {
            +irReturn(
                irCall(original.symbol, returnType, typeParameters.map { it.defaultType }).apply {
                    arguments[0] = irGetObjectValue(target.defaultType, target.symbol)
                    nonDispatchParameters.forEachIndexed { index, parameter -> arguments[index + 1] = irGet(parameter) }
                }
            )
        }
    }

    private fun IrSimpleFunction.hook(field: IrField, original: IrSimpleFunction, member: IrSimpleFunction) {
        if (correspondingPropertySymbol != null) origin = this@InterceptionPoints.origin
        isTailrec = false
        val redirectType = member.parentAsClass.defaultType
        body = DeclarationIrBuilder(context, symbol).irBlockBody {
            val self = dispatchReceiverParameter!!
            val redirect = irTemporary(irGetField(irGet(self), field))
            val typeArguments = typeParameters.map { it.defaultType }
            val parameters = nonDispatchParameters
            +irIfThen(
                context.irBuiltIns.unitType,
                irEqualsNull(irGet(redirect)),
                irReturn(
                    irCall(original.symbol, returnType, typeArguments).apply {
                        arguments[0] = irGet(self)
                        parameters.forEachIndexed { index, parameter -> arguments[index + 1] = irGet(parameter) }
                    }
                ),
            )
            +irReturn(
                irCall(member.symbol, returnType, typeArguments).apply {
                    arguments[0] = irImplicitCast(irGet(redirect), redirectType)
                    parameters.forEachIndexed { index, parameter -> arguments[index + 1] = irGet(parameter) }
                }
            )
        }
    }

    private fun mark(declaration: IrDeclaration) {
        context.metadataDeclarationRegistrar.addCustomMetadataExtension(
            declaration,
            BuildConfig.COMPILER_PLUGIN_ID,
            INTERCEPTION_MARK,
        )
    }
}

private const val HIDDEN_MESSAGE = "Generated by lokki for mockObject"
