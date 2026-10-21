package io.lokki.compiler.ir

import io.lokki.compiler.BuildConfig
import io.lokki.compiler.InterceptionMark
import io.lokki.compiler.REDIRECT
import io.lokki.compiler.REDIRECT_PROPERTY
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrVararg
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.util.classId
import org.jetbrains.kotlin.ir.util.isObject
import org.jetbrains.kotlin.ir.util.kotlinFqName
import org.jetbrains.kotlin.ir.util.parentClassOrNull
import org.jetbrains.kotlin.name.CallableId

internal class ObjectRedirects(private val context: IrPluginContext) {
    private val finder = context.finderForBuiltins()
    private val redirects = HashMap<IrClass, ObjectRedirect?>()

    fun of(target: IrClass): ObjectRedirect? = redirects.getOrPut(target) { find(target) }

    fun of(argument: IrExpression): ObjectRedirect? = argument.type.classOrNull?.owner?.let(::of)

    fun member(function: IrSimpleFunction): RedirectMember? {
        val redirect = function.parentClassOrNull?.let(::of) ?: return null
        val declaration = function.correspondingPropertySymbol?.owner ?: function
        if (mark(declaration) != InterceptionMark.CURRENT) return null
        val suffix = function.ownKey.substringAfter('#')
        val member = redirect.members.find { it.ownKey.substringAfter('#') == suffix } ?: return null
        return RedirectMember(redirect, member.memberKeys.first())
    }

    fun mark(declaration: IrDeclaration): InterceptionMark =
        InterceptionMark.of(
            context.metadataDeclarationRegistrar.getCustomMetadataExtension(declaration, BuildConfig.COMPILER_PLUGIN_ID)
        )

    private fun find(target: IrClass): ObjectRedirect? {
        if (!target.isObject || mark(target) != InterceptionMark.CURRENT) return null
        val classId = target.classId ?: return null
        val redirect = finder.findClass(classId.createNestedClassId(REDIRECT))?.owner ?: return null
        val property = finder.findProperties(CallableId(classId, REDIRECT_PROPERTY)).singleOrNull()?.owner
            ?: return null
        return ObjectRedirect(target, redirect, property)
    }
}

internal class ObjectRedirect(val target: IrClass, val redirect: IrClass, val property: IrProperty) {
    val name: String
        get() = target.objectName

    val members: List<IrSimpleFunction>
        get() = redirect.declarations.flatMap { member ->
            when (member) {
                is IrSimpleFunction -> listOf(member)
                is IrProperty -> listOfNotNull(member.getter, member.setter)
                else -> emptyList()
            }
        }
}

internal class RedirectMember(val redirect: ObjectRedirect, val key: String)

internal val IrClass.objectName: String
    get() = classId?.relativeClassName?.asString() ?: kotlinFqName.asString()

internal val IrClass.isRedirect: Boolean
    get() = name == REDIRECT && parentClassOrNull?.isObject == true

internal val IrCall.mockedObjects: List<IrExpression>?
    get() {
        val objects = arguments.firstOrNull() as? IrVararg ?: return emptyList()
        return objects.elements.map { it as? IrExpression ?: return null }
    }
