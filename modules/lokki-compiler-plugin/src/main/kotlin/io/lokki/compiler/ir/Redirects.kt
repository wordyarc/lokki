package io.lokki.compiler.ir

import io.lokki.compiler.BuildConfig
import io.lokki.compiler.FILE_REDIRECT_SUFFIX
import io.lokki.compiler.InterceptionMark
import io.lokki.compiler.ParameterNames
import io.lokki.compiler.REDIRECT
import io.lokki.compiler.REDIRECT_PROPERTY
import io.lokki.compiler.StaticInterception
import io.lokki.compiler.ir.collect.MockForm
import io.lokki.compiler.ir.collect.MockSite
import io.lokki.compiler.ir.collect.MockedTypes
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrDeclarationWithName
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrPackageFragment
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrVararg
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.util.classId
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.erasedUpperBound
import org.jetbrains.kotlin.ir.util.isObject
import org.jetbrains.kotlin.ir.util.isTopLevel
import org.jetbrains.kotlin.ir.util.kotlinFqName
import org.jetbrains.kotlin.ir.util.parentClassOrNull
import org.jetbrains.kotlin.ir.util.simpleFunctions
import org.jetbrains.kotlin.name.CallableId

internal class RedirectLookup(private val context: IrPluginContext, file: IrFile) {
    private val finder = context.finderForSource(file)
    private val objects = HashMap<IrClass, ObjectRedirect?>()
    private val statics = HashMap<IrDeclaration, StaticRedirect?>()

    fun of(target: IrClass): ObjectRedirect? = objects.getOrPut(target) { find(target) }

    fun of(argument: IrExpression): ObjectRedirect? = argument.type.classOrNull?.owner?.let(::of)

    fun static(reference: IrExpression): StaticRedirect? =
        when (val target = reference.referencedDeclaration.takeIf { reference.isUnboundReference }) {
            is IrSimpleFunction, is IrProperty -> static(target)
            else -> null
        }

    fun static(declaration: IrDeclarationWithName): StaticRedirect? =
        statics.getOrPut(declaration) { findStatic(declaration) }

    fun member(function: IrSimpleFunction): RedirectMember? {
        val declaration = function.correspondingPropertySymbol?.owner ?: function
        val parent = function.parent
        val redirect = when {
            function.isTopLevel -> static(declaration)
            parent is IrClass -> of(parent)?.takeIf { mark(declaration) == InterceptionMark.CURRENT }
            else -> null
        } ?: return null
        val suffix = function.ownKey.substringAfter('#')
        val member = redirect.members.find { it.ownKey.substringAfter('#') == suffix } ?: return null
        return RedirectMember(redirect, member.memberKeys.first())
    }

    fun mark(declaration: IrDeclaration): InterceptionMark = InterceptionMark.of(data(declaration))

    private fun data(declaration: IrDeclaration): ByteArray? =
        context.metadataDeclarationRegistrar.getCustomMetadataExtension(declaration, BuildConfig.COMPILER_PLUGIN_ID)

    private fun find(target: IrClass): ObjectRedirect? {
        if (!target.isObject || mark(target) != InterceptionMark.CURRENT) return null
        val classId = target.classId ?: return null
        val redirect = finder.findClass(classId.createNestedClassId(REDIRECT))?.owner ?: return null
        val property = finder.findProperties(CallableId(classId, REDIRECT_PROPERTY)).singleOrNull()?.owner
            ?: return null
        return ObjectRedirect(target, redirect, property)
    }

    private fun findStatic(declaration: IrDeclarationWithName): StaticRedirect? {
        if (!declaration.isTopLevel) return null
        val function = (declaration as? IrProperty)?.getter ?: declaration as? IrSimpleFunction ?: return null
        val interception = StaticInterception.decode(data(declaration)) ?: return null
        val redirect = finder.findClass(interception.redirect)?.owner ?: return null
        val callable = CallableId(interception.redirect.packageFqName, interception.property)
        val property = finder.findProperties(callable).singleOrNull()?.owner ?: return null
        return StaticRedirect(declaration, function, redirect, property)
    }
}

internal sealed class Redirect(val redirect: IrClass, val property: IrProperty) {
    abstract val name: String

    val site = MockSite.Mocked(MockedTypes(redirect.defaultType, emptyList(), MockForm.Mock))

    val members: List<IrSimpleFunction>
        get() = redirect.simpleFunctions()
}

internal class ObjectRedirect(
    val target: IrClass,
    redirect: IrClass,
    property: IrProperty,
) : Redirect(redirect, property) {
    override val name: String
        get() = target.objectName
}

internal class StaticRedirect(
    private val declaration: IrDeclarationWithName,
    private val function: IrSimpleFunction,
    redirect: IrClass,
    property: IrProperty,
) : Redirect(redirect, property) {
    val key: String
        get() = function.ownKey

    override val name: String
        get() {
            val receiver = function.parameters.find { it.kind == IrParameterKind.ExtensionReceiver }
            return "${receiver?.type?.erasedUpperBound?.name?.asString().orEmpty()}::${declaration.name}"
        }
}

internal class RedirectMember(val redirect: Redirect, val key: String)

internal val IrClass.objectName: String
    get() = classId?.relativeClassName?.asString() ?: kotlinFqName.asString()

internal val IrClass.redirectedName: String?
    get() = when {
        name == REDIRECT && parentClassOrNull?.isObject == true -> parentClassOrNull!!.objectName
        parent is IrPackageFragment && name.asString().endsWith(FILE_REDIRECT_SUFFIX) ->
            name.asString().removeSuffix(FILE_REDIRECT_SUFFIX)
        else -> null
    }

internal val IrCall.mockedObjects: List<IrExpression>?
    get() {
        val parameter = symbol.owner.parameters.firstOrNull { it.varargElementType != null }
        val objects = parameter?.let { arguments[it.indexInParameters] } as? IrVararg ?: return emptyList()
        return objects.elements.map { it as? IrExpression ?: return null }
    }

internal val IrCall.staticReferences: List<IrExpression>?
    get() = symbol.owner.parameters.flatMap { parameter ->
        val argument = arguments[parameter.indexInParameters] ?: return@flatMap emptyList()
        when {
            parameter.name.asString() !in ParameterNames.REFERENCES -> emptyList()
            argument is IrVararg -> argument.elements.map { it as? IrExpression ?: return null }
            else -> listOf(argument)
        }
    }
