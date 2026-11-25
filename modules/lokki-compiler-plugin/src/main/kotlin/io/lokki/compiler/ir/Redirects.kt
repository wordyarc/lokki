package io.lokki.compiler.ir

import io.lokki.compiler.CoreClass
import io.lokki.compiler.CONSTRUCTORS
import io.lokki.compiler.CONSTRUCTORS_HOLDER
import io.lokki.compiler.FILE_REDIRECT_SUFFIX
import io.lokki.compiler.InterceptionMark
import io.lokki.compiler.ParameterNames
import io.lokki.compiler.REDIRECT
import io.lokki.compiler.REDIRECT_PROPERTY
import io.lokki.compiler.StaticInterception
import io.lokki.compiler.ir.collect.MockForm
import io.lokki.compiler.ir.collect.MockSite
import io.lokki.compiler.ir.collect.MockedTypes
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrDeclarationWithName
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrPackageFragment
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrClassReference
import org.jetbrains.kotlin.ir.expressions.IrConst
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrVararg
import org.jetbrains.kotlin.ir.expressions.IrVarargElement
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.types.isString
import org.jetbrains.kotlin.ir.util.classId
import org.jetbrains.kotlin.ir.util.companionObject
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.erasedUpperBound
import org.jetbrains.kotlin.ir.util.getPackageFragment
import org.jetbrains.kotlin.ir.util.isEnumClass
import org.jetbrains.kotlin.ir.util.isObject
import org.jetbrains.kotlin.ir.util.isTopLevel
import org.jetbrains.kotlin.ir.util.kotlinFqName
import org.jetbrains.kotlin.ir.util.parentClassOrNull
import org.jetbrains.kotlin.ir.util.simpleFunctions
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

internal class RedirectLookup(private val lokki: LokkiIrContext, file: IrFile) {
    private val finder = lokki.pluginContext.finderForSource(file)
    private val objects = HashMap<IrClass, ObjectRedirect?>()
    private val statics = HashMap<IrDeclaration, StaticRedirect?>()
    private val constructorRedirects = HashMap<IrClass, ConstructorRedirect?>()

    @OptIn(ExperimentalStdlibApi::class)
    fun of(target: IrClass): ObjectRedirect? = objects.getOrPutIfMissing(target) { find(target) }

    fun of(argument: IrExpression): ObjectRedirect? = argument.type.classOrNull?.owner?.let(::of)

    fun ofStaticClass(element: IrVarargElement): ObjectRedirect? = element.staticOwner?.let(::of)

    fun static(reference: IrExpression): StaticRedirect? =
        when (val target = reference.referencedDeclaration.takeIf { reference.isUnboundReference }) {
            is IrSimpleFunction, is IrProperty -> static(target)
            else -> null
        }

    @OptIn(ExperimentalStdlibApi::class)
    fun static(declaration: IrDeclarationWithName): StaticRedirect? =
        statics.getOrPutIfMissing(declaration) { findStatic(declaration) }

    fun member(function: IrSimpleFunction): RedirectMember? {
        val declaration = function.correspondingPropertySymbol?.owner ?: function
        val parent = function.parent
        if (parent is IrClass && parent.name == REDIRECT) {
            val owner = parent.parentClassOrNull?.let(::of) ?: return null
            return RedirectMember(owner, function.memberKeys.first())
        }
        val redirect = when {
            function.isTopLevel -> static(declaration)
            parent is IrClass -> of(parent)?.takeIf {
                function.isFakeOverride || it.redirect.isCompiledHere ||
                    lokki.mark(declaration) == InterceptionMark.CURRENT
            }
            else -> null
        } ?: return null
        val suffix = function.ownKey.substringAfter('#')
        val member = redirect.members.find { it.ownKey.substringAfter('#') == suffix } ?: return null
        return RedirectMember(redirect, member.memberKeys.first())
    }

    @OptIn(ExperimentalStdlibApi::class)
    fun constructors(target: IrClass): ConstructorRedirect? =
        constructorRedirects.getOrPutIfMissing(target) { findConstructors(target) }

    private fun findConstructors(target: IrClass): ConstructorRedirect? {
        if (target.kind != ClassKind.CLASS) return null
        val classId = target.classId ?: return null
        if (target.isCompiledHere) {
            val nested = target.declarations.filterIsInstance<IrClass>()
            val redirect = nested.find { it.name == CONSTRUCTORS } ?: return null
            val holder = nested.find { it.name == CONSTRUCTORS_HOLDER } ?: return null
            val property = holder.declarations.filterIsInstance<IrProperty>()
                .find { it.name == REDIRECT_PROPERTY } ?: return null
            return ConstructorRedirect(target, redirect, property)
        }
        if (lokki.mark(target) != InterceptionMark.CURRENT) return null
        val redirect = finder.findClass(classId.createNestedClassId(CONSTRUCTORS))?.owner ?: return null
        val holder = classId.createNestedClassId(CONSTRUCTORS_HOLDER)
        val property = finder.findProperties(CallableId(holder, REDIRECT_PROPERTY)).singleOrNull()?.owner
            ?: return null
        return ConstructorRedirect(target, redirect, property)
    }

    fun file(name: String): List<StaticRedirect> = redirectClasses(FqName(name)).flatMap(::declarationsOf)

    private fun redirectClasses(facade: FqName): List<IrClass> {
        val packageName = facade.parent()
        val short = facade.shortName().asString()
        finder.findClass(ClassId(packageName, Name.identifier("$short$FILE_REDIRECT_SUFFIX")))
            ?.let { return listOf(it.owner) }
        val compiled = finder.findClass(CoreClass.GENERATED_MOCK.id)?.owner ?: return emptyList()
        return compiled.topLevelClassifierNames(packageName)
            .filter { it.asString().startsWith("${short}__") && it.asString().endsWith(FILE_REDIRECT_SUFFIX) }
            .sortedBy { it.asString() }
            .mapNotNull { finder.findClass(ClassId(packageName, it))?.owner }
    }

    private fun declarationsOf(redirect: IrClass): List<StaticRedirect> {
        val packageName = redirect.getPackageFragment().packageFqName
        val names = redirect.declarations.mapNotNull { declaration ->
            when {
                declaration is IrProperty && !declaration.isFakeOverride -> declaration.name
                declaration is IrSimpleFunction && !declaration.isFakeOverride &&
                    declaration.correspondingPropertySymbol == null -> declaration.name
                else -> null
            }
        }
        return names.distinct().flatMap { name ->
            val id = CallableId(packageName, name)
            finder.findFunctions(id).map { it.owner } + finder.findProperties(id).map { it.owner }
        }.mapNotNull { static(it) }.filter { it.redirect == redirect }
    }

    private fun find(target: IrClass): ObjectRedirect? {
        if (!target.isRedirectTarget) return null
        val generated = target.declarations.filterIsInstance<IrClass>().find { it.name == REDIRECT }
        if (generated == null && lokki.mark(target) != InterceptionMark.CURRENT) return null
        val classId = target.classId ?: return null
        val redirect = generated ?: finder.findClass(classId.createNestedClassId(REDIRECT))?.owner ?: return null
        val property = target.declarations.filterIsInstance<IrProperty>().find { it.name == REDIRECT_PROPERTY }
            ?: finder.findProperties(CallableId(classId, REDIRECT_PROPERTY)).singleOrNull()?.owner
            ?: return null
        return ObjectRedirect(target, redirect, property)
    }

    private fun findStatic(declaration: IrDeclarationWithName): StaticRedirect? {
        if (!declaration.isTopLevel) return null
        val function = (declaration as? IrProperty)?.getter ?: declaration as? IrSimpleFunction ?: return null
        generatedStatic(declaration, function)?.let { return it }
        val interception = StaticInterception.decode(lokki.metadata(declaration)) ?: return null
        val redirect = finder.findClass(interception.redirect)?.owner ?: return null
        val callable = CallableId(interception.redirect.packageFqName, interception.property)
        val property = finder.findProperties(callable).singleOrNull()?.owner ?: return null
        return StaticRedirect(declaration, function, redirect, property)
    }
}

private fun generatedStatic(declaration: IrDeclarationWithName, function: IrSimpleFunction): StaticRedirect? {
    val generated = (declaration.parent as? IrFile)?.declarations ?: return null
    val name = redirectPropertyName(declaration, function)
    val property = generated.filterIsInstance<IrProperty>().find { it.name == name } ?: return null
    val redirect = generated.filterIsInstance<IrClass>().find { it.name.asString().endsWith(FILE_REDIRECT_SUFFIX) }
        ?: return null
    return StaticRedirect(declaration, function, redirect, property)
}

@OptIn(ExperimentalStdlibApi::class)
internal fun redirectPropertyName(declaration: IrDeclarationWithName, function: IrSimpleFunction): Name =
    Name.identifier("${declaration.name}\$lokkiRedirect\$${function.ownKey.hashCode().toHexString()}")

private val IrDeclaration.isCompiledHere: Boolean
    get() = getPackageFragment() is IrFile

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

internal val IrClass.isRedirectTarget: Boolean
    get() = isObject || isEnumClass

internal val IrClass.objectName: String
    get() = classId?.relativeClassName?.asString() ?: kotlinFqName.asString()

internal val IrClass.redirectedName: String?
    get() = when {
        name == REDIRECT && parentClassOrNull?.isRedirectTarget == true -> parentClassOrNull!!.objectName
        parent is IrPackageFragment && name.asString().endsWith(FILE_REDIRECT_SUFFIX) ->
            name.asString().removeSuffix(FILE_REDIRECT_SUFFIX)
        else -> null
    }

internal val IrCall.mockedObjects: List<IrExpression>?
    get() {
        val parameter = symbol.owner.parameters.firstOrNull { it.varargElementType != null }
        val objects = parameter?.let { arguments[it] } as? IrVararg ?: return emptyList()
        return objects.elements.map { it as? IrExpression ?: return null }
    }

internal val IrCall.staticReferences: List<IrExpression>?
    get() = symbol.owner.parameters.flatMap { parameter ->
        val argument = arguments[parameter] ?: return@flatMap emptyList()
        when {
            parameter.name.asString() !in ParameterNames.REFERENCES -> emptyList()
            argument is IrVararg -> argument.elements.map { it as? IrExpression ?: return null }
            else -> listOf(argument)
        }
    }

internal val IrCall.staticFileArguments: List<IrVarargElement>?
    get() = classesParameter?.takeIf { it.varargElementType?.isString() == true }
        ?.let { parameter -> (arguments[parameter] as? IrVararg)?.elements.orEmpty() }

internal val IrCall.staticClassArguments: List<IrVarargElement>?
    get() = classesParameter?.takeIf { it.varargElementType?.isString() == false }
        ?.let { parameter -> (arguments[parameter] as? IrVararg)?.elements.orEmpty() }

private val IrCall.classesParameter: IrValueParameter?
    get() = symbol.owner.parameters.find { it.name.asString() == ParameterNames.FILE_CLASSES }

private val IrVarargElement.staticOwner: IrClass?
    get() = (this as? IrClassReference)?.classType?.classOrNull?.owner
        ?.let { owner -> owner.takeIf { it.isObject } ?: owner.companionObject() }

internal val IrVarargElement.fileClassName: String?
    get() = (this as? IrConst)?.value as? String

internal val IrCall.constructorClasses: List<IrClass>?
    get() {
        val parameter = symbol.owner.parameters.firstOrNull { it.varargElementType != null }
        val classes = parameter?.let { arguments[it] } as? IrVararg ?: return emptyList()
        return classes.elements.map { (it as? IrClassReference)?.classType?.classOrNull?.owner ?: return null }
    }
