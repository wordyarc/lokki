package io.lokki.compiler.ir.generate

import io.lokki.compiler.BuildConfig
import io.lokki.compiler.CHAINED_MOCKS
import io.lokki.compiler.LokkiDiagnostics
import io.lokki.compiler.LokkiPluginKey
import io.lokki.compiler.compat.CompilerCompat
import io.lokki.compiler.ir.FriendAwareFakeOverrides
import io.lokki.compiler.ir.LokkiSymbols
import io.lokki.compiler.ir.collect.MockClassKey
import io.lokki.compiler.ir.collect.MockClassUsage
import io.lokki.compiler.ir.collect.MockForm
import io.lokki.compiler.ir.collect.childMockedTypes
import io.lokki.compiler.ir.collect.mockClassKey
import io.lokki.compiler.ir.collect.mockedClass
import io.lokki.compiler.ir.collect.mockedClassTypeParameters
import io.lokki.compiler.ir.collect.referencedTypeParameters
import io.lokki.compiler.ir.irArrayOf
import io.lokki.compiler.ir.redirectedName
import io.lokki.compiler.ir.isVisibleFrom
import io.lokki.compiler.ir.memberKeys
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.incremental.components.LookupTracker
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.builders.IrBlockBodyBuilder
import org.jetbrains.kotlin.ir.builders.IrBuilder
import org.jetbrains.kotlin.ir.builders.IrBuilderWithScope
import org.jetbrains.kotlin.ir.builders.declarations.addBackingField
import org.jetbrains.kotlin.ir.builders.declarations.addConstructor
import org.jetbrains.kotlin.ir.builders.declarations.addField
import org.jetbrains.kotlin.ir.builders.declarations.addFunction
import org.jetbrains.kotlin.ir.builders.declarations.addTypeParameter
import org.jetbrains.kotlin.ir.builders.declarations.addValueParameter
import org.jetbrains.kotlin.ir.builders.declarations.buildClass
import org.jetbrains.kotlin.ir.builders.irAnnotation
import org.jetbrains.kotlin.ir.builders.irAs
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.builders.irBranch
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irCallConstructor
import org.jetbrains.kotlin.ir.builders.irCallOp
import org.jetbrains.kotlin.ir.builders.irDelegatingConstructorCall
import org.jetbrains.kotlin.ir.builders.irElseBranch
import org.jetbrains.kotlin.ir.builders.irEquals
import org.jetbrains.kotlin.ir.builders.irEqualsNull
import org.jetbrains.kotlin.ir.builders.irExprBody
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irGetField
import org.jetbrains.kotlin.ir.builders.irIfThen
import org.jetbrains.kotlin.ir.builders.irInt
import org.jetbrains.kotlin.ir.builders.irNull
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.builders.irSetField
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.builders.irTemporary
import org.jetbrains.kotlin.ir.builders.irWhen
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrConstructor
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOriginImpl
import org.jetbrains.kotlin.ir.declarations.IrField
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrOverridableDeclaration
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.IrTypeParameter
import org.jetbrains.kotlin.ir.declarations.IrValueDeclaration
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.declarations.name
import org.jetbrains.kotlin.ir.expressions.IrBranch
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrStatementContainer
import org.jetbrains.kotlin.ir.expressions.impl.IrCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrConstImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrInstanceInitializerCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrThrowImpl
import org.jetbrains.kotlin.ir.overrides.IrFakeOverrideBuilder
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.IrTypeSystemContextImpl
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.types.isMarkedNullable
import org.jetbrains.kotlin.ir.types.isPrimitiveType
import org.jetbrains.kotlin.ir.types.isUnit
import org.jetbrains.kotlin.ir.types.makeNullable
import org.jetbrains.kotlin.ir.types.starProjectedType
import org.jetbrains.kotlin.ir.types.typeOrFail
import org.jetbrains.kotlin.ir.types.typeOrNull
import org.jetbrains.kotlin.ir.util.addChild
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.copyTo
import org.jetbrains.kotlin.ir.util.createThisReceiverParameter
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.defaultValueForType
import org.jetbrains.kotlin.ir.util.erasedUpperBound
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.util.isInterface
import org.jetbrains.kotlin.ir.util.isLocal
import org.jetbrains.kotlin.ir.util.isMethodOfAny
import org.jetbrains.kotlin.ir.util.nonDispatchParameters
import org.jetbrains.kotlin.ir.util.parentAsClass
import org.jetbrains.kotlin.ir.util.patchDeclarationParents
import org.jetbrains.kotlin.ir.util.primaryConstructor
import org.jetbrains.kotlin.ir.util.substitute
import org.jetbrains.kotlin.ir.visitors.IrVisitorVoid
import org.jetbrains.kotlin.ir.visitors.acceptChildrenVoid
import org.jetbrains.kotlin.ir.visitors.acceptVoid
import org.jetbrains.kotlin.load.kotlin.PackagePartClassUtils
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.util.OperatorNameConventions

internal class MockClassGenerator(
    private val context: IrPluginContext,
    private val symbols: LokkiSymbols,
    private val lookupTracker: LookupTracker?,
    private val compat: CompilerCompat,
) {
    private val origin = IrDeclarationOrigin.GeneratedByPlugin(LokkiPluginKey)
    private val typeSystem = IrTypeSystemContextImpl(context.irBuiltIns)
    private val anyConstructor = context.irBuiltIns.anyClass.owner.constructors.single()
    private val relaxedDefaults = RelaxedDefaults(context, symbols)
    private val memberDescriptions = MemberDescriptions(context, symbols)
    private val instances = MockInstances(context, symbols)

    fun generate(file: IrFile, usages: Map<MockClassKey, MockClassUsage>): Map<MockClassKey, IrClass> {
        val classes = FileClasses(file, usages, LinkedHashMap(), !file.hasAnnotation(CHAINED_MOCKS))
        for ((key, usage) in usages.entries.sortedByDescending { it.value.relaxedChildren }) {
            if (key !in classes.generated) generate(key, usage, classes, listOf(usage.mockedTypes.type))
        }
        return classes.generated
    }

    private fun generate(
        key: MockClassKey,
        usage: MockClassUsage,
        classes: FileClasses,
        path: List<IrSimpleType>,
    ): IrClass {
        val mocked = usage.mockedTypes.type.mockedClass
        val form = usage.mockedTypes.form
        val mock = context.irFactory.buildClass {
            startOffset = usage.firstUse.startOffset
            endOffset = usage.firstUse.endOffset
            name = key.mockClassName(mocked, form)
            origin = this@MockClassGenerator.origin
            visibility = if (mocked.isLocal) DescriptorVisibilities.LOCAL else usage.visibility
        }
        classes.generated[key] = mock
        val parameters = usage.mockedTypes.type.referencedTypeParameters
        val ownParameters = parameters.indices.map { mock.addTypeParameter("T$it", context.irBuiltIns.anyNType) }
        val mockedType = usage.mockedTypes.type
            .substitute(parameters.zip(ownParameters.map { it.defaultType }).toMap()) as IrSimpleType
        mock.superTypes = listOf(mockedType) + usage.mockedTypes.interfaces + symbols.generatedMock.defaultType
        mock.createThisReceiverParameter()
        if (mocked.isLocal) key.file.addAfter(mocked, mock) else key.file.addChild(mock)
        val constructor = when {
            form is MockForm.Construction -> mock.addSpyConstructor(mockedType, form)
            mocked.isInterface -> mock.addSpecConstructor(delegate = mockedType.takeIf { form == MockForm.Delegation })
            else -> {
                mock.addUncalledConstructor(mockedType, key.file)
                null
            }
        }
        IrFakeOverrideBuilder(typeSystem, FriendAwareFakeOverrides, emptyList())
            .buildFakeOverridesForClassUsingOverriddenSymbols(mock, emptyMap(), compatibilityMode = false)
            .forEach(mock::addChild)
        if (usage.mockedTypes.interfaces.isNotEmpty()) {
            val conflict = mock.inheritanceConflict(mocked)
            if (conflict != null) {
                compat.diagnosticContext(context.diagnosticReporter, usage.firstUse, key.file)
                    .report(LokkiDiagnostics.LOKKI_UNSUPPORTED, conflict)
            }
        }

        val stateProperty = mock.declarations.filterIsInstance<IrProperty>()
            .single { symbols.state.symbol in it.overriddenSymbols }
        val functions = mock.declarations.filterIsInstance<IrSimpleFunction>()
        val original = functions.single { symbols.original.symbol in it.overriddenSymbols }
        val coOriginal = functions.single { symbols.coOriginal.symbol in it.overriddenSymbols }
        val relaxed = functions.single { symbols.relaxed.symbol in it.overriddenSymbols }
        val describe = functions.single { symbols.describe.symbol in it.overriddenSymbols }
        val (methodsOfAny, declared) = mock.declarations.flatMap { member ->
            when (member) {
                original, coOriginal, relaxed, describe -> emptyList()
                is IrSimpleFunction -> listOf(member)
                is IrProperty -> if (member == stateProperty) emptyList() else listOfNotNull(member.getter, member.setter)
                else -> emptyList()
            }
        }.partition { it.isMethodOfAny() }
        val members = METHODS_OF_ANY.map { name -> methodsOfAny.single { it.name == name } } +
            declared.filter { it.modality != Modality.FINAL }
        val delegated = members.filter { form == MockForm.Delegation && it.delegatedTo(mocked) != null }.toSet()
        val (coOriginals, originals) = members.withIndex()
            .filter { (_, member) -> member.modality != Modality.ABSTRACT || member in delegated }
            .partition { (_, member) -> member.isSuspend }
        val descriptor = mock.addDescriptor(mocked, members)
        val state = stateProperty.implementState(mock, isFinal = constructor != null)
        val delegate = if (form == MockForm.Delegation) {
            mock.addDelegate(mockedType, isFinal = constructor != null)
        } else {
            null
        }
        if (constructor != null) {
            val spec = constructor.parameters.last()
            state.initializer = DeclarationIrBuilder(context, state.symbol).run {
                irExprBody(irState(mock.thisReceiver!!, spec, descriptor))
            }
            delegate?.initializer = DeclarationIrBuilder(context, delegate.symbol).run {
                irExprBody(irGet(constructor.parameters[constructor.parameters.size - 2]))
            }
        } else {
            mock.addInit(state, descriptor, delegate)
        }
        val spied = mocked.takeIf { form is MockForm.Construction }
        members.forEachIndexed { index, member ->
            if (member.modality != Modality.FINAL) member.implement(mock, state, index, spied)
        }
        val forward = Forward(mocked, delegate, delegated)
        original.implementOriginal(mock, forward, state, originals, ORIGINAL_BRANCH_BYTES)
        coOriginal.implementOriginal(mock, forward, state, coOriginals, CO_ORIGINAL_BRANCH_BYTES)
        val names = ownParameters.zip(parameters).associate { (own, parameter) -> own.symbol to parameter.owner.name }
        val children = if (usage.relaxedChildren) Children(usage, classes, path) else null
        relaxed.implementRelaxed(mock, members, children, classes.suggestsChainedMocks) {
            (names[it.symbol] ?: it.name).asString()
        }
        describe.implementDescribe(mock, members, key.file)
        usage.mockedTypes.classes.forEach { lookupTracker?.recordMockedTypeLookups(it, key.file) }
        return mock
    }

    private fun IrClass.addSpecConstructor(delegate: IrType?): IrConstructor =
        addConstructor {
            startOffset = this@addSpecConstructor.startOffset
            endOffset = this@addSpecConstructor.endOffset
            origin = this@MockClassGenerator.origin
            isPrimary = true
        }.apply {
            if (delegate != null) addValueParameter("delegate", delegate, MOCK_PARAMETER)
            addValueParameter("spec", symbols.mockSpec.defaultType, MOCK_PARAMETER)
            body = DeclarationIrBuilder(context, symbol, startOffset, endOffset).irBlockBody {
                +irDelegatingConstructorCall(anyConstructor)
                +IrInstanceInitializerCallImpl(
                    startOffset,
                    endOffset,
                    this@addSpecConstructor.symbol,
                    context.irBuiltIns.unitType,
                )
            }
        }

    private fun IrClass.addSpyConstructor(mockedType: IrSimpleType, form: MockForm.Construction): IrConstructor {
        val superConstructor = form.constructor
        val superTypeArguments = mockedType.arguments.map { it.typeOrFail }
        val substitution = mockedType.mockedClassTypeParameters.map { it.symbol }.zip(superTypeArguments).toMap()
        val constructor = addConstructor {
            startOffset = this@addSpyConstructor.startOffset
            endOffset = this@addSpyConstructor.endOffset
            origin = this@MockClassGenerator.origin
            isPrimary = true
        }
        val passed = superConstructor.parameters.zip(form.passed).filter { (_, isPassed) -> isPassed }
            .associate { (parameter, _) ->
                parameter to constructor.addValueParameter(
                    if (parameter.kind == IrParameterKind.DispatchReceiver) "outer" else parameter.name.asString(),
                    parameter.type.substitute(substitution),
                    MOCK_PARAMETER,
                ).apply { varargElementType = parameter.varargElementType?.substitute(substitution) }
            }
        constructor.addValueParameter("spec", symbols.mockSpec.defaultType, MOCK_PARAMETER)
        constructor.body = DeclarationIrBuilder(context, constructor.symbol).irBlockBody {
            +irDelegatingConstructorCall(superConstructor).apply {
                typeArguments.indices.forEach { index -> typeArguments[index] = superTypeArguments[index] }
                for ((parameter, value) in passed) arguments[parameter.indexInParameters] = irGet(value)
            }
            +IrInstanceInitializerCallImpl(
                startOffset,
                endOffset,
                this@addSpyConstructor.symbol,
                context.irBuiltIns.unitType,
            )
        }
        return constructor
    }

    private fun IrClass.addUncalledConstructor(mockedType: IrSimpleType, file: IrFile) {
        val mocked = mockedType.mockedClass
        val superConstructor = mocked.constructors
            .filter { it.isVisibleFrom(file) }
            .minBy { it.parameters.size }
        val superTypeArguments = mockedType.arguments.map { it.typeOrFail }
        val substitution = mockedType.mockedClassTypeParameters.map { it.symbol }.zip(superTypeArguments).toMap()
        val constructor = addConstructor {
            startOffset = this@addUncalledConstructor.startOffset
            endOffset = this@addUncalledConstructor.endOffset
            origin = this@MockClassGenerator.origin
            visibility = DescriptorVisibilities.PRIVATE
            isPrimary = true
        }
        constructor.body = DeclarationIrBuilder(context, constructor.symbol).irBlockBody {
            +irDelegatingConstructorCall(superConstructor).apply {
                typeArguments.indices.forEach { index -> typeArguments[index] = superTypeArguments[index] }
                for (parameter in superConstructor.parameters) {
                    val type = parameter.type.substitute(substitution)
                    arguments[parameter.indexInParameters] = when {
                        type.isPrimitiveType() || type.isMarkedNullable() ->
                            IrConstImpl.defaultValueForType(startOffset, endOffset, type)
                        else -> irAs(irNull(), type)
                    }
                }
            }
            +IrInstanceInitializerCallImpl(
                startOffset,
                endOffset,
                this@addUncalledConstructor.symbol,
                context.irBuiltIns.unitType,
            )
        }
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
        val constructor = symbols.mockDescriptor.constructors.single { it.parameters.size == arrays.size + 2 }
        descriptor.markSynthetic()
        descriptor.initializer = DeclarationIrBuilder(context, descriptor.symbol).run {
            irExprBody(
                irCallConstructor(constructor.symbol, emptyList()).apply {
                    arguments[0] = irString(mockedClass.descriptorName)
                    arguments[1] = irInt(BuildConfig.LOKKI_ABI_VERSION)
                    arrays.forEachIndexed { index, strings ->
                        arguments[index + 2] = irArrayOf(context.irBuiltIns.stringType, strings.map(::irString))
                    }
                }
            )
        }
        return descriptor
    }

    private fun IrProperty.implementState(mock: IrClass, isFinal: Boolean): IrField {
        materialize()
        val state = addBackingField {
            type = symbols.mockState.defaultType
            this.isFinal = isFinal
        }
        state.markSynthetic()
        if (!isFinal) state.annotations += DeclarationIrBuilder(context, state.symbol).irAnnotation(symbols.volatile)
        getter!!.run {
            materialize()
            markSynthetic()
            parameters = listOf(mock.dispatchReceiverFor(this))
            body = DeclarationIrBuilder(context, symbol).irBlockBody {
                +irReturn(irGetField(irGet(dispatchReceiverParameter!!), state))
            }
        }
        return state
    }

    private fun IrClass.addDelegate(type: IrType, isFinal: Boolean): IrField =
        addField {
            name = DELEGATE
            this.type = type
            origin = this@MockClassGenerator.origin
            visibility = DescriptorVisibilities.PRIVATE
            this.isFinal = isFinal
        }.apply { markSynthetic() }

    private fun IrClass.addInit(state: IrField, descriptor: IrField, delegate: IrField?) {
        val init = addFunction(INIT.asString(), context.irBuiltIns.unitType, origin = origin)
        val instance = delegate?.let { init.addValueParameter("delegate", it.type, MOCK_PARAMETER) }
        val spec = init.addValueParameter("spec", symbols.mockSpec.defaultType, MOCK_PARAMETER)
        init.markSynthetic()
        init.body = DeclarationIrBuilder(context, init.symbol).irBlockBody {
            val self = init.dispatchReceiverParameter!!
            if (delegate != null) +irSetField(irGet(self), delegate, irGet(instance!!))
            +irSetField(irGet(self), state, irState(self, spec, descriptor))
        }
    }

    private fun IrBuilder.irState(
        self: IrValueDeclaration,
        spec: IrValueDeclaration,
        descriptor: IrField,
    ): IrExpression =
        irCallConstructor(symbols.mockState.primaryConstructor!!.symbol, emptyList()).apply {
            arguments[0] = irGet(self)
            arguments[1] = irGet(spec)
            arguments[2] = irGetField(null, descriptor)
        }

    private fun IrSimpleFunction.implement(mock: IrClass, state: IrField, member: Int, spied: IrClass?) {
        val abstract = modality == Modality.ABSTRACT
        materialize()
        correspondingPropertySymbol?.owner?.materialize()
        adoptParameters(mock)
        body = DeclarationIrBuilder(context, symbol).irBlockBody {
            val self = dispatchReceiverParameter!!
            val current = irGetField(irGet(self), state)
            val receiver = if (spied == null) {
                current
            } else {
                val initialized = irTemporary(current)
                val original = when {
                    abstract -> irUninitialized(spied, this@implement)
                    else -> irForward(
                        Forward(spied, null, emptySet()),
                        this@implement,
                        irGet(self),
                        typeParameters.map { it.defaultType },
                        nonDispatchParameters.map(::irGet),
                    )
                }
                +irIfThen(context.irBuiltIns.unitType, irEqualsNull(irGet(initialized)), irReturn(original))
                irGet(initialized)
            }
            val answer = when {
                isSuspend -> irCall(symbols.coAnswer.symbol, returnType, listOf(returnType))
                else -> irCall(symbols.answer)
            }
            answer.arguments[0] = receiver
            answer.arguments[1] = irInt(member)
            answer.arguments[2] = irArrayOf(context.irBuiltIns.anyNType, nonDispatchParameters.map(::irGet))
            when {
                isSuspend -> +irReturn(answer)
                returnType.isUnit() -> +answer
                else -> +irReturn(irAs(answer, returnType))
            }
        }
    }

    private fun IrSimpleFunction.implementOriginal(
        mock: IrClass,
        forward: Forward,
        state: IrField,
        originals: List<IndexedValue<IrSimpleFunction>>,
        branchBytes: Int,
    ) {
        val widest = originals.maxOfOrNull { it.value.nonDispatchParameters.size } ?: 0
        val partSize = maxOf(1, ORIGINAL_PART_BYTES / (branchBytes + ORIGINAL_ARGUMENT_BYTES * widest))
        implementDispatcher(
            mock,
            originals,
            partSize,
            otherwise = { function ->
                val (member, arguments) = function.nonDispatchParameters
                irCall(symbols.noOriginal).apply {
                    this.arguments[0] = irGetField(irGet(function.dispatchReceiverParameter!!), state)
                    this.arguments[1] = irGet(member)
                    this.arguments[2] = irGet(arguments)
                }
            },
        ) { function, entries ->
            val (member, arguments) = function.nonDispatchParameters
            entries.map { (index, original) ->
                val erasure = original.typeParameters.associate { it.symbol to it.erasure }
                val values = original.nonDispatchParameters.mapIndexed { position, parameter ->
                    irAs(irArrayGet(arguments, position), parameter.type.substitute(erasure))
                }
                val self = irGet(function.dispatchReceiverParameter!!)
                val call = irForward(forward, original, self, original.typeParameters.map { it.erasure }, values)
                irBranch(irEquals(irGet(member), irInt(index)), call)
            }
        }
    }

    private fun IrSimpleFunction.implementRelaxed(
        mock: IrClass,
        members: List<IrSimpleFunction>,
        children: Children?,
        suggestsChainedMocks: Boolean,
        parameterName: (IrTypeParameter) -> String,
    ) {
        val child = children?.let { it::default }
        val defaults = members.withIndex().drop(METHODS_OF_ANY.size).mapNotNull { (index, member) ->
            relaxedDefaults.of(member.returnType, parameterName, child, suggestsChainedMocks)
                ?.let { IndexedValue(index, it) }
        }
        val partSize = PART_BYTES / RELAXED_BRANCH_BYTES
        implementDispatcher(mock, defaults, partSize, otherwise = { irNull() }) { function, entries ->
            val member = function.nonDispatchParameters.single()
            entries.groupBy { it.value.key }.values.map { group ->
                val conditions = group.map { irEquals(irGet(member), irInt(it.index)) }
                irBranch(irAnyOf(conditions), group.first().value.expression(this))
            }
        }
        patchDeclarationParents(mock)
    }

    private class FileClasses(
        val file: IrFile,
        val usages: Map<MockClassKey, MockClassUsage>,
        val generated: MutableMap<MockClassKey, IrClass>,
        val suggestsChainedMocks: Boolean,
    )

    private inner class Children(
        private val usage: MockClassUsage,
        private val classes: FileClasses,
        private val path: List<IrSimpleType>,
    ) {
        fun default(type: IrSimpleType): RelaxedDefault? {
            if (type.referencedTypeParameters.isNotEmpty()) return null
            if (path.any { it.classOrNull == type.classOrNull && type.depth > it.depth }) return null
            val types = type.childMockedTypes(classes.file) ?: return null
            val key = types.mockClassKey(classes.file)
            val child = classes.generated[key] ?: generate(
                key,
                classes.usages[key]?.also { it.relaxedChildren = true }
                    ?: MockClassUsage(usage.firstUse, types, DescriptorVisibilities.PRIVATE, relaxedChildren = true),
                classes,
                path + types.type,
            )
            return RelaxedDefault("child ${key.typeKey}") {
                irCallConstructor(symbols.childMock.constructors.single().symbol, emptyList()).apply {
                    arguments[0] = with(instances) { irFactory(child, types) }
                }
            }
        }
    }

    private fun IrSimpleFunction.implementDescribe(mock: IrClass, members: List<IrSimpleFunction>, file: IrFile) {
        val widest = members.maxOf { it.nonDispatchParameters.size }
        val partSize = maxOf(1, PART_BYTES / (DESCRIBE_BRANCH_BYTES + DESCRIBE_PARAMETER_BYTES * widest))
        implementDispatcher(mock, members.withIndex().toList(), partSize, otherwise = { irNull() }) { function, entries ->
            val member = function.nonDispatchParameters.single()
            entries.map { (index, described) ->
                val description = with(memberDescriptions) { irDescription(described, file) }
                irBranch(irEquals(irGet(member), irInt(index)), description)
            }
        }
    }

    private fun <T> IrSimpleFunction.implementDispatcher(
        mock: IrClass,
        entries: List<IndexedValue<T>>,
        partSize: Int,
        otherwise: IrBuilderWithScope.(IrSimpleFunction) -> IrExpression,
        branches: IrBuilderWithScope.(IrSimpleFunction, List<IndexedValue<T>>) -> List<IrBranch>,
    ) {
        materialize()
        markSynthetic()
        adoptParameters(mock)
        val parts = entries.groupBy { it.index / partSize }
        if (parts.size <= 1) return dispatch(otherwise) { branches(it, entries) }

        val functions = parts.mapValues { (part, members) ->
            mock.addFunction(
                "$name\$$part",
                returnType,
                visibility = DescriptorVisibilities.PRIVATE,
                isSuspend = isSuspend,
                origin = origin,
            ).apply {
                markSynthetic()
                for (parameter in this@implementDispatcher.nonDispatchParameters) {
                    addValueParameter(parameter.name.asString(), parameter.type, MOCK_PARAMETER)
                }
                dispatch(otherwise) { branches(it, members) }
            }
        }
        val parameters = nonDispatchParameters
        dispatch(otherwise) { dispatcher ->
            val part = irTemporary(
                irCallOp(symbols.intDiv.symbol, context.irBuiltIns.intType, irGet(parameters.first()), irInt(partSize))
            )
            functions.map { (index, function) ->
                irBranch(
                    irEquals(irGet(part), irInt(index)),
                    irCall(function).apply {
                        arguments[0] = irGet(dispatcher.dispatchReceiverParameter!!)
                        parameters.forEachIndexed { position, parameter -> arguments[position + 1] = irGet(parameter) }
                    },
                )
            }
        }
    }

    private fun IrSimpleFunction.dispatch(
        otherwise: IrBuilderWithScope.(IrSimpleFunction) -> IrExpression,
        branches: IrBlockBodyBuilder.(IrSimpleFunction) -> List<IrBranch>,
    ) {
        body = DeclarationIrBuilder(context, symbol).irBlockBody {
            val dispatch = branches(this@dispatch) + irElseBranch(otherwise(this@dispatch))
            +irReturn(irWhen(returnType, dispatch))
        }
    }

    private fun IrBuilder.irAnyOf(conditions: List<IrExpression>): IrExpression =
        conditions.singleOrNull() ?: irCall(context.irBuiltIns.ororSymbol, context.irBuiltIns.booleanType).apply {
            arguments[0] = irAnyOf(conditions.subList(0, conditions.size / 2))
            arguments[1] = irAnyOf(conditions.subList(conditions.size / 2, conditions.size))
        }

    private class Forward(val mocked: IrClass, val delegate: IrField?, val delegated: Set<IrSimpleFunction>)

    private fun IrBuilder.irForward(
        forward: Forward,
        member: IrSimpleFunction,
        self: IrExpression,
        typeArguments: List<IrType>,
        arguments: List<IrExpression>,
    ): IrExpression {
        val anyClass = context.irBuiltIns.anyClass
        val (target, qualifier) = when {
            member in forward.delegated -> member.delegatedTo(forward.mocked)!! to null
            forward.mocked.isInterface && member.isMethodOfAny() ->
                anyClass.owner.functions.single { it.name == member.name }.symbol to anyClass
            else -> {
                val overridden = member.overriddenSymbols.first { it.owner.modality != Modality.ABSTRACT }
                overridden to overridden.owner.parentAsClass.symbol
            }
        }
        val substitution = member.typeParameters.map { it.symbol }.zip(typeArguments).toMap()
        val returnType = member.returnType.substitute(substitution)
        return IrCallImpl(startOffset, endOffset, returnType, target, superQualifierSymbol = qualifier).apply {
            typeArguments.forEachIndexed { index, type -> this.typeArguments[index] = type }
            this.arguments[0] = if (qualifier == null) irGetField(self, forward.delegate!!) else self
            member.nonDispatchParameters.forEachIndexed { index, parameter ->
                this.arguments[parameter.indexInParameters] = arguments[index]
            }
        }
    }

    private fun IrBuilder.irUninitialized(spied: IrClass, member: IrSimpleFunction): IrExpression {
        val name = member.correspondingPropertySymbol?.owner?.name ?: member.name
        val message = "abstract ${spied.name}.$name was called by a constructor of ${spied.name} " +
            "before the spy was created"
        return IrThrowImpl(
            startOffset,
            endOffset,
            context.irBuiltIns.nothingType,
            irCallConstructor(symbols.lokkiException.symbol, emptyList()).apply {
                arguments[0] = irString(message)
                arguments[1] = irNull()
            },
        )
    }

    private fun IrBuilder.irArrayGet(array: IrValueParameter, index: Int): IrExpression =
        irCall(symbols.arrayGet.symbol, context.irBuiltIns.anyNType).apply {
            arguments[0] = irGet(array)
            arguments[1] = irInt(index)
        }

    private fun IrSimpleFunction.adoptParameters(mock: IrClass) {
        parameters = parameters.map { parameter ->
            if (parameter.kind == IrParameterKind.DispatchReceiver) {
                mock.dispatchReceiverFor(this)
            } else {
                parameter.apply { origin = MOCK_PARAMETER }
            }
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

internal fun IrClass.dispatchReceiverFor(function: IrFunction): IrValueParameter =
    thisReceiver!!.let { it.copyTo(function, type = it.type) }

private fun IrSimpleFunction.delegatedTo(mocked: IrClass): IrSimpleFunctionSymbol? =
    if (isMethodOfAny()) null else overriddenSymbols.find { it.owner.parentAsClass == mocked }

private fun IrFile.addAfter(local: IrClass, mock: IrClass) {
    mock.parent = local.parent
    acceptVoid(object : IrVisitorVoid() {
        override fun visitElement(element: IrElement) {
            val statements = (element as? IrStatementContainer)?.statements
            val index = statements?.indexOf(local) ?: -1
            if (index >= 0) statements!!.add(index + 1, mock) else element.acceptChildrenVoid(this)
        }
    })
}

private fun MockClassKey.mockClassName(mocked: IrClass, form: MockForm): Name {
    val hash = typeKey.hashCode().toHexString()
    val filePart = PackagePartClassUtils.getFilePartShortName(file.name)
    val kind = if (form == MockForm.Mock) "LokkiMock" else "LokkiSpy"
    return Name.identifier("${mocked.name}\$$kind\$$hash\$$filePart")
}

private val IrClass.descriptorName: String
    get() = redirectedName ?: name.asString()

private val IrType.depth: Int
    get() = 1 + ((this as? IrSimpleType)?.arguments.orEmpty().maxOfOrNull { it.typeOrNull?.depth ?: 0 } ?: 0)

private val IrTypeParameter.erasure: IrType
    get() = erasedUpperBound.symbol.starProjectedType.makeNullable()

private val DESCRIPTOR = Name.identifier("lokki\$descriptor")
private val DELEGATE = Name.identifier("lokki\$delegate")
internal val INIT = Name.identifier("lokki\$init")
private val MOCK_PARAMETER = IrDeclarationOriginImpl("LOKKI_MOCK_PARAMETER", isSynthetic = true)
private val METHODS_OF_ANY =
    listOf(OperatorNameConventions.EQUALS, OperatorNameConventions.HASH_CODE, OperatorNameConventions.TO_STRING)
private const val PART_BYTES = 32_000
private const val ORIGINAL_PART_BYTES = 8_000
private const val ORIGINAL_BRANCH_BYTES = 24
private const val CO_ORIGINAL_BRANCH_BYTES = 96
private const val ORIGINAL_ARGUMENT_BYTES = 16
private const val RELAXED_BRANCH_BYTES = 24
private const val DESCRIBE_BRANCH_BYTES = 32
private const val DESCRIBE_PARAMETER_BYTES = 8
