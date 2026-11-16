package io.lokki.compiler.ir.generate

import io.lokki.compiler.BuildConfig
import io.lokki.compiler.IntrinsicKind
import io.lokki.compiler.LokkiPluginKey
import io.lokki.compiler.ParameterNames
import io.lokki.compiler.ir.IrDiagnostics
import io.lokki.compiler.ir.LokkiSymbols
import io.lokki.compiler.ir.argument
import io.lokki.compiler.ir.collect.ClassMockFields
import io.lokki.compiler.ir.collect.MockClassKey
import io.lokki.compiler.ir.collect.MockField
import io.lokki.compiler.ir.collect.MockFields
import io.lokki.compiler.ir.collect.MockForm
import io.lokki.compiler.ir.collect.constructorParameter
import io.lokki.compiler.ir.collect.mockAnnotationName
import io.lokki.compiler.ir.collect.referencedTypeParameters
import io.lokki.compiler.ir.irHidden
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.incremental.components.LookupTracker
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.builders.IrBuilderWithScope
import org.jetbrains.kotlin.ir.builders.declarations.addFunction
import org.jetbrains.kotlin.ir.builders.declarations.addValueParameter
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irCallOp
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irGetField
import org.jetbrains.kotlin.ir.builders.irIfThen
import org.jetbrains.kotlin.ir.builders.irImplicitCast
import org.jetbrains.kotlin.ir.builders.irInt
import org.jetbrains.kotlin.ir.builders.irNotEquals
import org.jetbrains.kotlin.ir.builders.irNotIs
import org.jetbrains.kotlin.ir.builders.irNull
import org.jetbrains.kotlin.ir.builders.irSetField
import org.jetbrains.kotlin.ir.builders.irTemporary
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.expressions.IrConst
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.types.makeNotNull
import org.jetbrains.kotlin.ir.types.makeNullable
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.ir.util.isFakeOverride
import org.jetbrains.kotlin.ir.util.nonDispatchParameters
import org.jetbrains.kotlin.ir.visitors.IrVisitorVoid
import org.jetbrains.kotlin.ir.visitors.acceptChildrenVoid
import org.jetbrains.kotlin.name.Name

internal class MockFieldInitializers(
    private val context: IrPluginContext,
    private val symbols: LokkiSymbols,
    lookupTracker: LookupTracker?,
    private val diagnostics: IrDiagnostics,
) {
    private val origin = IrDeclarationOrigin.GeneratedByPlugin(LokkiPluginKey)
    private val instances = MockInstances(context, symbols)
    private val injections = MockInjections(context, symbols, instances, lookupTracker)
    private val initializers = HashMap<IrClass, Initializer>()

    private class Initializer(val function: IrSimpleFunction, val inherited: IrSimpleFunctionSymbol?)

    fun declare(files: List<MockFields>) {
        val owners = files.flatMap { fields ->
            fields.owners.filterValues { owner -> owner.fields.any { it.isInitialized } }.keys
        }
        val ownerSet = owners.toSet()
        for (owner in owners.sortedBy { it.superclasses().count() }) {
            initializers[owner] = owner.declareInitializer(ownerSet)
        }
    }

    fun implement(file: IrFile, fields: MockFields, mocks: Map<MockClassKey, IrClass>) {
        file.acceptChildrenVoid(object : IrVisitorVoid() {
            override fun visitElement(element: IrElement) {
                element.acceptChildrenVoid(this)
            }

            override fun visitValueParameter(declaration: IrValueParameter) {
                val annotation = declaration.mockAnnotationName ?: return
                diagnostics.unsupported(declaration, file, "'@$annotation' on a parameter is not supported yet")
            }
        })
        for (field in fields.fields.values) {
            val problem = field.problem
            when {
                problem == null -> field.site?.warning?.let { diagnostics.report(field.property, file, it) }
                field.property.constructorParameter?.mockAnnotationName == null ->
                    diagnostics.report(field.property, file, problem)
            }
        }
        for ((owner, owned) in fields.owners) {
            initializers[owner]?.let { owner.implement(it, owned, file, mocks) }
        }
        for (field in fields.fields.values) {
            if (field.problem == null && field.isConstructedSpy) field.constructSpy(mocks)
        }
    }

    private fun IrClass.declareInitializer(owners: Set<IrClass>): Initializer {
        val inherited = functions.singleOrNull { it.name == INIT_MOCK_FIELDS && it.isFakeOverride }
        val modality = if (modality == Modality.FINAL) Modality.FINAL else Modality.OPEN
        val function = inherited?.apply {
            isFakeOverride = false
            origin = this@MockFieldInitializers.origin
            this.modality = modality
            parameters = parameters.map {
                if (it.kind == IrParameterKind.DispatchReceiver) dispatchReceiverFor(this) else it
            }
        } ?: addFunction(INIT_MOCK_FIELDS.asString(), context.irBuiltIns.unitType, modality, origin = origin).apply {
            addValueParameter("flags", context.irBuiltIns.intType)
        }
        val superFunction = when {
            inherited != null -> inherited.overriddenSymbols.first()
            else -> superclasses().firstOrNull { it in owners }?.let { initializers.getValue(it).function.symbol }
        }
        if (inherited == null) {
            function.overriddenSymbols = listOf(superFunction ?: symbols.initMockFields.symbol)
            if (superFunction == null) superTypes += symbols.mockFieldsOwner.defaultType
        }
        function.annotations = listOf(DeclarationIrBuilder(context, function.symbol).irHidden(symbols, HIDDEN_MESSAGE))
        context.metadataDeclarationRegistrar.registerFunctionAsMetadataVisible(function)
        return Initializer(function, superFunction)
    }

    private fun IrClass.implement(
        initializer: Initializer,
        owned: ClassMockFields,
        file: IrFile,
        mocks: Map<MockClassKey, IrClass>,
    ) {
        val function = initializer.function
        val unit = context.irBuiltIns.unitType
        function.body = DeclarationIrBuilder(context, function.symbol).irBlockBody {
            val self = function.dispatchReceiverParameter!!
            val flags = function.nonDispatchParameters.single()
            initializer.inherited?.let { inherited ->
                +irCall(inherited.owner, superQualifierSymbol = superclasses().first().symbol).apply {
                    arguments[0] = irGet(self)
                    arguments[1] = irGet(flags)
                }
            }
            for (field in owned.fields.filter { it.inject == null && it.isInitialized }) {
                val site = field.site!!
                val types = site.types
                val mock = mocks.getValue(site.key)
                val backingField = field.property.backingField!!
                val value = irGetField(irGet(self), backingField)
                val current = irTemporary(value, irType = backingField.type.makeNullable())
                val spec = with(instances) { irSpec(field.name, irFlags(field, types.form, flags)) }
                val created = with(instances) {
                    when (types.form) {
                        MockForm.Mock -> irNewMock(mock, types, spec)
                        MockForm.Delegation -> {
                            val instance = irImplicitCast(irGet(current), backingField.type.makeNotNull())
                            irDelegatingSpy(mock, types, instance, spec)
                        }
                        is MockForm.Construction ->
                            irConstruct(mock, types.type.referencedTypeParameters.map { it.defaultType }, listOf(spec))
                    }
                }
                val assignment = irSetField(irGet(self), backingField, created)
                val initialization = when (types.form) {
                    MockForm.Delegation -> irIfThen(unit, irNotEquals(irGet(current), irNull()), assignment)
                    else -> assignment
                }
                +irIfThen(unit, irNotIs(irGet(current), symbols.generatedMock.defaultType), initialization)
            }
            with(injections) { irInjectMocks(owned, function, self, file, mocks) }
        }
    }

    private fun MockField.constructSpy(mocks: Map<MockClassKey, IrClass>) {
        val types = site!!.types
        val mock = mocks.getValue(site.key)
        val field = property.backingField!!
        val initializer = field.initializer!!
        val typeArguments = types.type.referencedTypeParameters.map { it.defaultType }
        DeclarationIrBuilder(context, field.symbol, initializer.startOffset, initializer.endOffset).run {
            val spec = with(instances) { irSpec(name, irInt(BuildConfig.FLAG_SPY)) }
            initializer.expression = initializer.expression.replaceConstructorCall(mock.typeWith(typeArguments)) {
                with(instances) { irConstruct(mock, typeArguments, it.arguments.filterNotNull() + spec) }
            }
        }
    }

    private fun IrBuilderWithScope.irFlags(field: MockField, form: MockForm, flags: IrValueParameter): IrExpression =
        when (field.kind) {
            IntrinsicKind.SPY -> when (form) {
                MockForm.Delegation -> irInt(BuildConfig.FLAG_SPY or BuildConfig.FLAG_DELEGATE)
                else -> irInt(BuildConfig.FLAG_SPY)
            }
            IntrinsicKind.RELAXED_MOCK -> irInt(BuildConfig.FLAG_RELAXED)
            else -> {
                val own = field.flag(ParameterNames.RELAXED, BuildConfig.FLAG_RELAXED) or
                    field.flag(ParameterNames.RELAX_UNIT_FUN, BuildConfig.FLAG_RELAX_UNIT_FUN)
                val intType = context.irBuiltIns.intType
                if (own == 0) irGet(flags) else irCallOp(symbols.intOr.symbol, intType, irGet(flags), irInt(own))
            }
        }

    private fun MockField.flag(name: String, flag: Int): Int =
        if ((annotation.argument(name) as? IrConst)?.value == true) flag else 0
}

private fun IrClass.superclasses(): Sequence<IrClass> =
    generateSequence(superclass()) { it.superclass() }

private fun IrClass.superclass(): IrClass? =
    superTypes.firstNotNullOfOrNull { type -> type.classOrNull?.owner?.takeIf { it.kind == ClassKind.CLASS } }

private val INIT_MOCK_FIELDS = Name.identifier("lokki\$initMockFields")
private const val HIDDEN_MESSAGE = "Generated by lokki for @Mock, @RelaxedMock, @Spy and @InjectMocks properties"
