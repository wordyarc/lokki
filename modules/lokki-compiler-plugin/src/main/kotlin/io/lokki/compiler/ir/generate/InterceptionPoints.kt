package io.lokki.compiler.ir.generate

import io.lokki.compiler.BuildConfig
import io.lokki.compiler.CONSTRUCTORS
import io.lokki.compiler.FILE_REDIRECT_SUFFIX
import io.lokki.compiler.INTERCEPTION_MARK
import io.lokki.compiler.MockablePatterns
import io.lokki.compiler.REDIRECT
import io.lokki.compiler.REDIRECT_PROPERTY
import io.lokki.compiler.StaticInterception
import io.lokki.compiler.ir.LokkiIrContext
import io.lokki.compiler.ir.addFriendAwareFakeOverrides
import io.lokki.compiler.ir.constructName
import io.lokki.compiler.ir.constructorsPropertyName
import io.lokki.compiler.ir.createName
import io.lokki.compiler.ir.irArrayGet
import io.lokki.compiler.ir.irCast
import io.lokki.compiler.ir.irGetObject
import io.lokki.compiler.ir.irHidden
import io.lokki.compiler.ir.isOverridable
import io.lokki.compiler.ir.redirectPropertyName
import io.lokki.compiler.ir.declaredParameters
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.backend.jvm.ir.getJvmNameFromAnnotation
import org.jetbrains.kotlin.backend.jvm.lower.getFileClassInfo
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.descriptors.DescriptorVisibility
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.ir.builders.declarations.IrFunctionBuilder
import org.jetbrains.kotlin.ir.builders.declarations.addBackingField
import org.jetbrains.kotlin.ir.builders.declarations.addFunction
import org.jetbrains.kotlin.ir.builders.declarations.addGetter
import org.jetbrains.kotlin.ir.builders.declarations.addProperty
import org.jetbrains.kotlin.ir.builders.declarations.addSetter
import org.jetbrains.kotlin.ir.builders.declarations.addValueParameter
import org.jetbrains.kotlin.ir.builders.declarations.buildClass
import org.jetbrains.kotlin.ir.builders.declarations.buildProperty
import org.jetbrains.kotlin.ir.builders.irAnnotation
import org.jetbrains.kotlin.ir.builders.irBlock
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irCallOp
import org.jetbrains.kotlin.ir.builders.irEqeqeq
import org.jetbrains.kotlin.ir.builders.irEqualsNull
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irGetField
import org.jetbrains.kotlin.ir.builders.irGetObjectValue
import org.jetbrains.kotlin.ir.builders.irIfThen
import org.jetbrains.kotlin.ir.builders.irIfThenElse
import org.jetbrains.kotlin.ir.builders.irImplicitCast
import org.jetbrains.kotlin.ir.builders.irInt
import org.jetbrains.kotlin.ir.builders.irNotEquals
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.builders.irSet
import org.jetbrains.kotlin.ir.builders.irSetField
import org.jetbrains.kotlin.ir.builders.irTemporary
import org.jetbrains.kotlin.ir.builders.irWhile
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrConstructor
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrDeclarationContainer
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrDeclarationParent
import org.jetbrains.kotlin.ir.declarations.IrDeclarationWithName
import org.jetbrains.kotlin.ir.declarations.IrEnumEntry
import org.jetbrains.kotlin.ir.declarations.IrField
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.IrValueDeclaration
import org.jetbrains.kotlin.ir.declarations.IrVariable
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrGetValue
import org.jetbrains.kotlin.ir.expressions.impl.IrCallImpl
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.types.makeNullable
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.IrTypeParameterRemapper
import org.jetbrains.kotlin.ir.util.addChild
import org.jetbrains.kotlin.ir.util.classIdOrFail
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.copyTo
import org.jetbrains.kotlin.ir.util.copyTypeParametersFrom
import org.jetbrains.kotlin.ir.util.createThisReceiverParameter
import org.jetbrains.kotlin.ir.util.deepCopyWithSymbols
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.getAnnotation
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.util.isEnumClass
import org.jetbrains.kotlin.ir.util.isMethodOfAny
import org.jetbrains.kotlin.ir.util.makeTypeParameterSubstitutionMap
import org.jetbrains.kotlin.ir.util.nonDispatchParameters
import org.jetbrains.kotlin.ir.util.parentAsClass
import org.jetbrains.kotlin.ir.util.patchDeclarationParents
import org.jetbrains.kotlin.ir.util.properties
import org.jetbrains.kotlin.ir.util.remapTypes
import org.jetbrains.kotlin.ir.util.resolveFakeOverride
import org.jetbrains.kotlin.ir.util.substitute
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid
import org.jetbrains.kotlin.load.java.JvmAbi
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.JvmStandardClassIds
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.util.OperatorNameConventions

internal class InterceptionPoints(private val lokki: LokkiIrContext, private val mockable: MockablePatterns) {
    private val context = lokki.pluginContext
    private val symbols = lokki.symbols
    private val compat = lokki.compat
    private val origin = lokki.origin

    fun generate(module: IrModuleFragment) {
        if (!compat.registersClassesAsMetadataVisible) return
        val objects = module.files.flatMap { file -> file.declarations.flatMap(::objects) }
        objects.forEach(::generate)
        module.files.forEach(::generate)
        module.files.forEach(::generateConstructors)
    }

    private fun constructible(declaration: IrDeclaration): List<IrClass> {
        if (declaration !is IrClass || declaration.origin != IrDeclarationOrigin.DEFINED) return emptyList()
        if (DescriptorVisibilities.isPrivate(declaration.visibility)) return emptyList()
        val nested = declaration.declarations.flatMap(::constructible)
        val eligible = declaration.kind == ClassKind.CLASS && !declaration.isInner && !declaration.isValue &&
            !declaration.isExpect && declaration.modality != Modality.ABSTRACT &&
            declaration.modality != Modality.SEALED &&
            !declaration.hasAnnotation(JvmStandardClassIds.JVM_RECORD_ANNOTATION_CLASS_ID) && declaration.matches()
        return if (eligible) nested + declaration else nested
    }

    private fun generateConstructors(file: IrFile) {
        for (target in file.declarations.flatMap(::constructible)) {
            val constructors = target.constructors.filter { it.visibility.isInterceptable && !it.isExpect }.toList()
            if (constructors.isEmpty()) continue
            val redirect = target.addRedirect(CONSTRUCTORS, target, CONSTRUCTOR_MESSAGE)
            file.addRedirectProperty(redirect, target.constructorsPropertyName, CONSTRUCTOR_MESSAGE)
            for (constructor in constructors) {
                val create = redirect.addCreate(target, constructor)
                val parameters = constructor.declaredParameters
                if (parameters.any { it.defaultValue != null } && parameters.size <= MAX_MASKED_PARAMETERS) {
                    redirect.addConstruct(target, constructor, create)
                }
            }
            redirect.complete()
        }
    }

    private fun IrClass.addCreate(target: IrClass, constructor: IrConstructor): IrSimpleFunction {
        val member = addFunction(
            constructor.createName.asString(),
            context.irBuiltIns.unitType,
            Modality.ABSTRACT,
            origin = this@InterceptionPoints.origin,
        )
        member.copyTypeParametersFrom(target)
        val substitution = makeTypeParameterSubstitutionMap(target, member)
        val receiver = thisReceiver!!
        member.parameters = listOf(receiver.copyTo(member, type = receiver.type)) +
            constructor.declaredParameters.map {
                it.copyTo(member, type = it.type.substitute(substitution), varargElementType = null, defaultValue = null)
            }
        member.returnType = target.symbol.typeWith(member.typeParameters.map { it.defaultType })
        return member
    }

    private fun IrClass.addConstruct(target: IrClass, constructor: IrConstructor, create: IrSimpleFunction) {
        val builtIns = context.irBuiltIns
        val member = addFunction(
            constructor.constructName.asString(),
            builtIns.unitType,
            Modality.OPEN,
            origin = this@InterceptionPoints.origin,
        )
        member.copyTypeParametersFrom(target)
        val substitution = makeTypeParameterSubstitutionMap(target, member)
        val remapper = IrTypeParameterRemapper(target.typeParameters.zip(member.typeParameters).toMap())
        member.returnType = target.symbol.typeWith(member.typeParameters.map { it.defaultType })
        val args = member.addValueParameter("args", builtIns.arrayClass.typeWith(builtIns.anyNType))
        val mask = member.addValueParameter("mask", builtIns.intType)
        val parameters = constructor.declaredParameters
        member.body = DeclarationIrBuilder(context, member.symbol).irBlockBody {
            val values = HashMap<IrValueDeclaration, IrVariable>()
            parameters.forEachIndexed { index, parameter ->
                val type = parameter.type.substitute(substitution)
                val passed = irCast(irArrayGet(symbols, args, index), type)
                val default = parameter.defaultValue?.expression?.deepCopyWithSymbols(member)
                val value = if (default == null) {
                    passed
                } else {
                    default.remapTypes(remapper)
                    val replaced = default.transform(
                        object : IrElementTransformerVoid() {
                            override fun visitGetValue(expression: IrGetValue): IrExpression =
                                values[expression.symbol.owner]?.let { irGet(it) } ?: expression
                        },
                        null,
                    )
                    val bit = irCallOp(symbols.intAnd.symbol, builtIns.intType, irGet(mask), irInt(1 shl index))
                    irIfThenElse(type, irNotEquals(bit, irInt(0)), replaced, passed)
                }
                values[parameter] = irTemporary(value, irType = type)
            }
            +irReturn(
                irCall(create.symbol, member.returnType, member.typeParameters.map { it.defaultType }).apply {
                    arguments[0] = irGet(member.dispatchReceiverParameter!!)
                    parameters.forEachIndexed { index, parameter ->
                        arguments[index + 1] = irGet(values.getValue(parameter))
                    }
                }
            )
        }
        member.patchDeclarationParents(this)
    }

    private fun objects(declaration: IrDeclaration): List<IrClass> {
        if (declaration !is IrClass || declaration.origin != IrDeclarationOrigin.DEFINED) return emptyList()
        if (DescriptorVisibilities.isPrivate(declaration.visibility)) return emptyList()
        val nested = declaration.declarations.flatMap(::objects)
        return if (declaration.isInterceptable) nested + declaration else nested
    }

    private val IrClass.isInterceptable: Boolean
        get() = (kind == ClassKind.OBJECT || kind == ClassKind.ENUM_CLASS) && !isExpect &&
            (matches() || isCompanion && parentAsClass.matches())

    private fun IrClass.matches(): Boolean =
        mockable.matches(classIdOrFail) || mockable.names.any { hasAnnotation(it) }

    private fun IrDeclarationWithName.matches(file: IrFile): Boolean =
        mockable.matches(CallableId(file.packageFqName, name)) ||
            mockable.names.any { hasAnnotation(it) || file.hasAnnotation(it) }

    private fun generate(target: IrClass) {
        val functions = target.declarations.filterIsInstance<IrSimpleFunction>()
            .filter { it.isInterceptable || target.isEnumClass && it.isAbstractInterceptable }
            .filterNot { target.isEnumClass && it.isComposable }
        val properties = target.declarations.filterIsInstance<IrProperty>()
            .filter { it.isInterceptable || target.isEnumClass && it.isAbstractInterceptable }
            .filterNot { target.isEnumClass && it.getter?.isComposable == true }
        val privates = target.declarations.filterIsInstance<IrSimpleFunction>().filter { it.isPrivateInterceptable }
        val inheritedFunctions = target.declarations.filterIsInstance<IrSimpleFunction>()
            .filter { it.correspondingPropertySymbol == null && it.isInheritedInterceptable }
        val inheritedProperties = target.declarations.filterIsInstance<IrProperty>()
            .filter { it.isInheritedInterceptable }
        val redirect = target.addRedirect(REDIRECT, target, OBJECT_MESSAGE)
        val field = target.addRedirectProperty(redirect, REDIRECT_PROPERTY, OBJECT_MESSAGE)
        for (function in functions) {
            target.intercept(function, redirect, field)
            mark(function, INTERCEPTION_MARK)
        }
        for (function in privates) target.intercept(function, redirect, field, target.privateHookName(function))
        for (property in properties) {
            target.intercept(property, redirect, field)
            mark(property, INTERCEPTION_MARK)
        }
        for (function in inheritedFunctions) {
            function.overrideInherited(target)
            target.intercept(function, redirect, field)
        }
        for (property in inheritedProperties) {
            property.isFakeOverride = false
            property.origin = origin
            property.getter!!.overrideInherited(target)
            property.setter?.takeIf { it.isInheritedAccessor }?.overrideInherited(target)
            target.intercept(property, redirect, field)
        }
        redirect.complete()
        mark(target, INTERCEPTION_MARK)
    }

    private fun generate(file: IrFile) {
        val functions = file.declarations.filterIsInstance<IrSimpleFunction>()
            .filter { it.isInterceptable && it.matches(file) }
        val properties = file.declarations.filterIsInstance<IrProperty>()
            .filter { it.isInterceptable && it.matches(file) }
        if (functions.isEmpty() && properties.isEmpty()) return
        val part = file.getFileClassInfo().fileClassFqName.shortName().asString()
        val first = functions.firstOrNull() ?: properties.first()
        val redirect = file.addRedirect(Name.identifier("$part$FILE_REDIRECT_SUFFIX"), first, STATIC_MESSAGE)
        for (function in functions) {
            val field = file.addRedirectProperty(redirect, function, function)
            file.intercept(function, redirect, field)
            mark(function, StaticInterception(redirect.classIdOrFail, field.name).encode())
        }
        for (property in properties) {
            val field = file.addRedirectProperty(redirect, property, property.getter!!)
            file.intercept(property, redirect, field)
            mark(property, StaticInterception(redirect.classIdOrFail, field.name).encode())
        }
        redirect.complete()
    }

    private fun IrFile.addRedirectProperty(
        redirect: IrClass,
        declaration: IrDeclarationWithName,
        function: IrSimpleFunction,
    ): IrField {
        return addRedirectProperty(redirect, redirectPropertyName(declaration, function), STATIC_MESSAGE)
    }

    private fun IrDeclarationContainer.intercept(
        function: IrSimpleFunction,
        redirect: IrClass,
        field: IrField,
        memberName: Name = function.name,
    ) {
        val member = redirect.addFunction(
            memberName.asString(),
            context.irBuiltIns.unitType,
            Modality.OPEN,
            isSuspend = function.isSuspend,
            origin = origin,
        )
        intercept(function, ORIGINAL, member, field)
    }

    private fun IrDeclarationContainer.intercept(property: IrProperty, redirect: IrClass, field: IrField) {
        val setter = property.setter?.takeIf {
            it.isInterceptableAccessor || it.modality == Modality.ABSTRACT && it.visibility.isInterceptable
        }
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
        intercept(property.getter!!, GETTER, member.addGetter(accessor), field)
        if (setter != null) intercept(setter, SETTER, member.addSetter(accessor), field)
    }

    private fun IrDeclarationContainer.intercept(
        function: IrSimpleFunction,
        suffix: String,
        member: IrSimpleFunction,
        field: IrField,
    ) {
        val jvmName = function.getAnnotation(JvmStandardClassIds.JVM_NAME)
        val name = function.getJvmNameFromAnnotation()
            ?: (function.correspondingPropertySymbol?.owner ?: function).name.asString()
        val target = this as? IrClass
        val original = addOriginal(function, Name.identifier("$name$suffix"), isOverridable = target?.isEnumClass == true)
        member.copySignatureOf(function)
        member.annotations = listOfNotNull(jvmName?.deepCopyWithSymbols(member))
        if (target?.isEnumClass == true) {
            member.forwardToEntry(original, target.entryLookup(member.parentAsClass, field))
            target.entryOverrides(function).forEach { (entry, override) ->
                val entryOriginal = entry.addOriginal(override, original.name, overriding = original)
                entryOriginal.callsOriginalOfSuper(function, original)
                override.hook(field, entryOriginal, member)
            }
        } else {
            member.forward(original, target)
        }
        if (function.body != null) function.hook(field, original, member)
    }

    private fun IrClass.entryOverrides(function: IrSimpleFunction): List<Pair<IrClass, IrSimpleFunction>> =
        declarations.filterIsInstance<IrEnumEntry>().mapNotNull { it.correspondingClass }.flatMap { entry ->
            entry.declarations.flatMap { declaration ->
                when (declaration) {
                    is IrSimpleFunction -> listOf(declaration)
                    is IrProperty -> listOfNotNull(declaration.getter, declaration.setter)
                    else -> emptyList()
                }
            }.filter { function.symbol in it.overriddenSymbols && !it.isFakeOverride }.map { entry to it }
        }

    private fun IrSimpleFunction.callsOriginalOfSuper(function: IrSimpleFunction, original: IrSimpleFunction) {
        body?.transformChildrenVoid(object : IrElementTransformerVoid() {
            override fun visitCall(expression: IrCall): IrExpression {
                if (expression.symbol == function.symbol && expression.superQualifierSymbol != null) {
                    expression.symbol = original.symbol
                }
                return super.visitCall(expression)
            }
        })
    }

    private fun IrClass.entryLookup(redirect: IrClass, field: IrField): IrSimpleFunction {
        declarations.filterIsInstance<IrSimpleFunction>().find { it.name == ENTRY_LOOKUP }?.let { return it }
        val builtIns = context.irBuiltIns
        val lookup = addFunction(
            ENTRY_LOOKUP.asString(),
            defaultType,
            isStatic = true,
            origin = this@InterceptionPoints.origin,
        )
        val wanted = lookup.addValueParameter("redirect", redirect.defaultType)
        lookup.annotations = listOf(DeclarationIrBuilder(context, lookup.symbol).irAnnotation(symbols.jvmSynthetic))
        val valuesFunction = functions.single { it.name == StandardNames.ENUM_VALUES && it.nonDispatchParameters.isEmpty() }
        val size = builtIns.arrayClass.owner.properties.single { it.name.asString() == "size" }.getter!!
        val plus = builtIns.intClass.owner.functions.single {
            it.name == OperatorNameConventions.PLUS && it.nonDispatchParameters.singleOrNull()?.type == builtIns.intType
        }
        val less = builtIns.lessFunByOperandType.getValue(builtIns.intClass)
        lookup.body = DeclarationIrBuilder(context, lookup.symbol).irBlockBody {
            val entries = irTemporary(irCall(valuesFunction.symbol))
            val index = irTemporary(irInt(0), isMutable = true)
            +irWhile().apply {
                condition = irCall(less).apply {
                    arguments[0] = irGet(index)
                    arguments[1] = irCall(size.symbol, builtIns.intType).apply { arguments[0] = irGet(entries) }
                }
                body = irBlock {
                    val entry = irTemporary(
                        irCall(symbols.arrayGet.symbol, defaultType).apply {
                            arguments[0] = irGet(entries)
                            arguments[1] = irGet(index)
                        }
                    )
                    +irIfThen(
                        builtIns.unitType,
                        irEqeqeq(irGetField(irGet(entry), field), irGet(wanted)),
                        irReturn(irGet(entry)),
                    )
                    +irSet(
                        index,
                        irCall(plus.symbol, builtIns.intType).apply {
                            arguments[0] = irGet(index)
                            arguments[1] = irInt(1)
                        },
                    )
                }
            }
            +irReturn(irCall(builtIns.noWhenBranchMatchedExceptionSymbol))
        }
        return lookup
    }

    private fun IrSimpleFunction.forwardToEntry(original: IrSimpleFunction, lookup: IrSimpleFunction) {
        body = DeclarationIrBuilder(context, symbol).irBlockBody {
            +irReturn(
                irCall(original.symbol, returnType, typeParameters.map { it.defaultType }).apply {
                    arguments[0] = irCall(lookup.symbol).apply { arguments[0] = irGet(dispatchReceiverParameter!!) }
                    nonDispatchParameters.forEachIndexed { index, parameter -> arguments[index + 1] = irGet(parameter) }
                }
            )
        }
    }

    private fun IrClass.complete() {
        addFriendAwareFakeOverrides(lokki.typeSystem)
        compat.registerClassAsMetadataVisible(context.metadataDeclarationRegistrar, this)
    }

    private val IrSimpleFunction.isInterceptable: Boolean
        get() = origin == IrDeclarationOrigin.DEFINED && !isFakeOverride && !isMethodOfAny() &&
            isInterceptableAccessor

    private val IrSimpleFunction.isInterceptableAccessor: Boolean
        get() = body != null && visibility.isInterceptable && !isInline && !isExternal && !isExpect

    private val IrSimpleFunction.isInheritedInterceptable: Boolean
        get() = isFakeOverride && !isMethodOfAny() && isInheritedAccessor

    private val IrSimpleFunction.isInheritedAccessor: Boolean
        get() {
            val inherited = resolveFakeOverride() ?: return false
            return isFakeOverride && visibility.isInterceptable && !isInline && !isExternal && !isExpect &&
                !inherited.isInline && !inherited.isExternal && inherited.modality != Modality.ABSTRACT &&
                (modality != Modality.FINAL || inherited.modality != Modality.FINAL || isOverridable)
        }

    private val IrProperty.isInheritedInterceptable: Boolean
        get() {
            val getter = getter ?: return false
            val inherited = getter.resolveFakeOverride()?.correspondingPropertySymbol?.owner ?: return false
            return isFakeOverride && visibility.isInterceptable && !isConst && !inherited.isConst &&
                !inherited.isLateinit && getter.isInheritedAccessor && getter.typeParameters.isEmpty() &&
                !inherited.hasAnnotation(JvmAbi.JVM_FIELD_ANNOTATION_FQ_NAME) &&
                inherited.backingField?.hasAnnotation(JvmAbi.JVM_FIELD_ANNOTATION_FQ_NAME) != true
        }

    private fun IrSimpleFunction.overrideInherited(owner: IrClass) {
        val inherited = overriddenSymbols.first { it.owner.modality != Modality.ABSTRACT }
        isFakeOverride = false
        origin = this@InterceptionPoints.origin
        modality = Modality.FINAL
        parameters = parameters.map {
            if (it.kind == IrParameterKind.DispatchReceiver) owner.dispatchReceiverFor(this) else it
        }
        body = DeclarationIrBuilder(context, symbol).irBlockBody {
            +irReturn(
                IrCallImpl(
                    startOffset,
                    endOffset,
                    returnType,
                    inherited,
                    superQualifierSymbol = inherited.owner.parentAsClass.symbol,
                ).apply {
                    typeParameters.forEachIndexed { index, parameter -> typeArguments[index] = parameter.defaultType }
                    arguments[0] = irGet(dispatchReceiverParameter!!)
                    nonDispatchParameters.forEach { arguments[it.indexInParameters] = irGet(it) }
                }
            )
        }
    }

    private val IrSimpleFunction.isAbstractInterceptable: Boolean
        get() = origin == IrDeclarationOrigin.DEFINED && !isFakeOverride && modality == Modality.ABSTRACT &&
            correspondingPropertySymbol == null && visibility.isInterceptable && !isExpect

    private val IrProperty.isAbstractInterceptable: Boolean
        get() = origin == IrDeclarationOrigin.DEFINED && !isFakeOverride && modality == Modality.ABSTRACT &&
            visibility.isInterceptable && !isExpect && getter?.typeParameters?.isEmpty() == true

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

    private fun IrDeclarationContainer.addRedirect(name: Name, source: IrDeclaration, message: String): IrClass {
        val redirect = context.irFactory.buildClass {
            startOffset = source.startOffset
            endOffset = source.endOffset
            this.name = name
            kind = ClassKind.INTERFACE
            modality = Modality.ABSTRACT
            origin = this@InterceptionPoints.origin
        }
        redirect.createThisReceiverParameter()
        redirect.superTypes = listOf(context.irBuiltIns.anyType)
        redirect.annotations = DeclarationIrBuilder(context, redirect.symbol).run {
            listOf(irHidden(symbols, message), irAnnotation(symbols.jvmDefaultWithoutCompatibility))
        }
        addChild(redirect)
        return redirect
    }

    private fun IrDeclarationContainer.addRedirectProperty(redirect: IrClass, name: Name, message: String): IrField {
        val type = redirect.defaultType.makeNullable()
        val owner = this as? IrClass
        val property = context.irFactory.buildProperty {
            this.name = name
            isVar = true
            origin = this@InterceptionPoints.origin
        }
        addChild(property)
        val field = property.addBackingField {
            this.type = type
            isStatic = owner == null
            origin = this@InterceptionPoints.origin
        }
        field.annotations = listOf(DeclarationIrBuilder(context, field.symbol).irAnnotation(symbols.volatile))
        property.addGetter {
            returnType = type
            origin = this@InterceptionPoints.origin
        }.apply {
            parameters = listOfNotNull(owner?.dispatchReceiverFor(this))
            annotations = listOf(DeclarationIrBuilder(context, symbol).irAnnotation(symbols.jvmSynthetic))
            body = DeclarationIrBuilder(context, symbol).irBlockBody {
                +irReturn(irGetField(dispatchReceiverParameter?.let(::irGet), field))
            }
        }
        property.addSetter {
            returnType = context.irBuiltIns.unitType
            origin = this@InterceptionPoints.origin
        }.apply {
            parameters = listOfNotNull(owner?.dispatchReceiverFor(this))
            val value = addValueParameter("value", type)
            annotations = listOf(DeclarationIrBuilder(context, symbol).irAnnotation(symbols.jvmSynthetic))
            body = DeclarationIrBuilder(context, symbol).irBlockBody {
                +irSetField(dispatchReceiverParameter?.let(::irGet), field, irGet(value))
            }
        }
        property.annotations = listOf(DeclarationIrBuilder(context, property.symbol).irHidden(symbols, message))
        compat.registerPropertyAsMetadataVisible(context.metadataDeclarationRegistrar, property)
        return field
    }

    private fun IrDeclarationContainer.addOriginal(
        function: IrSimpleFunction,
        name: Name,
        isOverridable: Boolean = false,
        overriding: IrSimpleFunction? = null,
    ): IrSimpleFunction {
        val original = function.deepCopyWithSymbols(this as IrDeclarationParent)
        original.name = name
        original.origin = origin
        original.visibility = DescriptorVisibilities.PUBLIC
        original.modality = when {
            !isOverridable -> Modality.FINAL
            function.body == null -> Modality.ABSTRACT
            else -> Modality.OPEN
        }
        original.overriddenSymbols = listOfNotNull(overriding?.symbol)
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

    private fun IrSimpleFunction.forward(original: IrSimpleFunction, target: IrClass?) {
        body = DeclarationIrBuilder(context, symbol).irBlockBody {
            +irReturn(
                irCall(original.symbol, returnType, typeParameters.map { it.defaultType }).apply {
                    val values = nonDispatchParameters.map { irGet(it) }
                    val receiver = target?.let { irGetObject(it) }
                    (listOfNotNull(receiver) + values).forEachIndexed { index, value -> arguments[index] = value }
                }
            )
        }
    }

    private fun IrSimpleFunction.hook(field: IrField, original: IrSimpleFunction, member: IrSimpleFunction) {
        if (correspondingPropertySymbol != null) origin = this@InterceptionPoints.origin
        isTailrec = false
        val redirectType = member.parentAsClass.defaultType
        val getter = field.correspondingPropertySymbol!!.owner.getter!!
        val inherited = parent != field.parent
        body = DeclarationIrBuilder(context, symbol).irBlockBody {
            val self = dispatchReceiverParameter
            val current = when {
                inherited -> irCall(getter.symbol).apply { arguments[0] = irGet(self!!) }
                else -> irGetField(self?.let(::irGet), field)
            }
            val redirect = irTemporary(current)
            val typeArguments = typeParameters.map { it.defaultType }
            val values = nonDispatchParameters
            +irIfThen(
                context.irBuiltIns.unitType,
                irEqualsNull(irGet(redirect)),
                irReturn(
                    irCall(original.symbol, returnType, typeArguments).apply {
                        val all = listOfNotNull(self) + values
                        all.forEachIndexed { index, value -> arguments[index] = irGet(value) }
                    }
                ),
            )
            +irReturn(
                irCall(member.symbol, returnType, typeArguments).apply {
                    arguments[0] = irImplicitCast(irGet(redirect), redirectType)
                    values.forEachIndexed { index, value -> arguments[index + 1] = irGet(value) }
                }
            )
        }
    }

    private fun mark(declaration: IrDeclaration, data: ByteArray) {
        val registrar = context.metadataDeclarationRegistrar
        registrar.addCustomMetadataExtension(declaration, BuildConfig.COMPILER_PLUGIN_ID, data)
    }
}

private const val OBJECT_MESSAGE = "Generated by lokki for mockObject"
private const val STATIC_MESSAGE = "Generated by lokki for mockStatic"
private const val CONSTRUCTOR_MESSAGE = "Generated by lokki for mockConstructor"
private const val MAX_MASKED_PARAMETERS = 31
private const val ORIGINAL = "\$lokkiOriginal"
private val ENTRY_LOOKUP = Name.identifier("lokki\$entry")
private const val GETTER = "\$lokkiGet"
private const val SETTER = "\$lokkiSet"
