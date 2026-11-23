package io.lokki.compiler.ir.generate

import io.lokki.compiler.BuildConfig
import io.lokki.compiler.CoreClass
import io.lokki.compiler.CoreFunction
import io.lokki.compiler.INIT_MOCK_FIELDS_NAME
import io.lokki.compiler.IntrinsicKind
import io.lokki.compiler.ir.FileContext
import io.lokki.compiler.ir.LokkiIrContext
import io.lokki.compiler.ir.argument
import io.lokki.compiler.ir.collect.ClassMockFields
import io.lokki.compiler.ir.collect.MockClassKey
import io.lokki.compiler.ir.collect.MockField
import io.lokki.compiler.ir.collect.MockFields
import io.lokki.compiler.ir.collect.MockForm
import io.lokki.compiler.ir.irHidden
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.descriptors.Modality
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
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.builders.irTemporary
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.expressions.IrConst
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.types.makeNotNull
import org.jetbrains.kotlin.ir.types.makeNullable
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.ir.util.isFakeOverride
import org.jetbrains.kotlin.ir.util.nonDispatchParameters
import org.jetbrains.kotlin.ir.util.superClass
import org.jetbrains.kotlin.name.Name

internal class MockFieldInitializers(lokki: LokkiIrContext) {
    private val context = lokki.pluginContext
    private val symbols = lokki.symbols
    private val origin = lokki.origin
    private val injections = MockInjections(lokki)
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

    fun implement(file: FileContext, mocks: Map<MockClassKey, IrClass>) {
        val fields = file.fields
        for ((owner, owned) in fields.owners) {
            initializers[owner]?.let { owner.implement(it, owned, file.ir, mocks) }
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
            function.overriddenSymbols = listOf(superFunction ?: symbols[CoreFunction.INIT_MOCK_FIELDS].symbol)
            if (superFunction == null) superTypes += symbols[CoreClass.MOCK_FIELDS_OWNER].defaultType
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
                val spec = irSpec(symbols, irString(field.name), irFlags(field, types.form, flags))
                val created = when (types.form) {
                    MockForm.Mock -> irNewMock(symbols, mock, types, spec)
                    MockForm.Delegation -> {
                        val instance = irImplicitCast(irGet(current), backingField.type.makeNotNull())
                        irDelegatingSpy(symbols, mock, types, instance, spec)
                    }
                    is MockForm.Construction -> irConstruct(mock, types.typeArguments, listOf(spec))
                }
                val assignment = irSetField(irGet(self), backingField, created)
                val initialization = when (types.form) {
                    MockForm.Delegation -> irIfThen(unit, irNotEquals(irGet(current), irNull()), assignment)
                    else -> assignment
                }
                +irIfThen(unit, irNotIs(irGet(current), symbols[CoreClass.GENERATED_MOCK].defaultType), initialization)
            }
            with(injections) { irInjectMocks(owned, function, self, file, mocks) }
        }
    }

    private fun MockField.constructSpy(mocks: Map<MockClassKey, IrClass>) {
        val types = site!!.types
        val mock = mocks.getValue(site.key)
        val field = property.backingField!!
        val initializer = field.initializer!!
        val typeArguments = types.typeArguments
        DeclarationIrBuilder(context, field.symbol, initializer.startOffset, initializer.endOffset).run {
            val spec = irSpec(symbols, irString(name), irInt(types.form.spyFlags or annotation.recordsPrivateCalls))
            initializer.expression = initializer.expression.replaceConstructorCall(mock.typeWith(typeArguments)) {
                irConstruct(mock, typeArguments, it.arguments.filterNotNull() + spec)
            }
        }
    }

    private fun IrBuilderWithScope.irFlags(field: MockField, form: MockForm, flags: IrValueParameter): IrExpression =
        when (field.kind) {
            IntrinsicKind.SPY -> {
                val own = form.spyFlags or field.annotation.recordsPrivateCalls
                irOr(irInt(own), irAnd(irGet(flags), BuildConfig.FLAG_RECORD_PRIVATE_CALLS))
            }
            IntrinsicKind.RELAXED_MOCK -> irInt(BuildConfig.FLAG_RELAXED)
            else -> {
                val own = MOCK_FLAGS.fold(0) { own, (name, flag) -> own or field.flag(name, flag) }
                val relaxation = irAnd(irGet(flags), MOCK_FLAGS.fold(0) { all, (_, flag) -> all or flag })
                if (own == 0) relaxation else irOr(relaxation, irInt(own))
            }
        }

    private fun IrBuilderWithScope.irOr(left: IrExpression, right: IrExpression): IrExpression =
        irCallOp(symbols.intOr.symbol, context.irBuiltIns.intType, left, right)

    private fun IrBuilderWithScope.irAnd(value: IrExpression, mask: Int): IrExpression =
        irCallOp(symbols.intAnd.symbol, context.irBuiltIns.intType, value, irInt(mask))

    private fun MockField.flag(name: String, flag: Int): Int =
        if ((annotation.argument(name) as? IrConst)?.value == true) flag else 0
}

private fun IrClass.superclasses(): Sequence<IrClass> = generateSequence(superClass) { it.superClass }

private val INIT_MOCK_FIELDS = Name.identifier(INIT_MOCK_FIELDS_NAME)
private const val HIDDEN_MESSAGE = "Generated by lokki for @Mock, @RelaxedMock, @Spy and @InjectMocks properties"
