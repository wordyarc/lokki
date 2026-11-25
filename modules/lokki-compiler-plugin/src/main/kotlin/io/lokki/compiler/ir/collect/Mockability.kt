package io.lokki.compiler.ir.collect

import io.lokki.compiler.NOT_CLASS_PROBLEM
import io.lokki.compiler.SEALED_WITHOUT_MOCKABLE_SUBCLASS
import io.lokki.compiler.TYPE_PARAMETER_PROBLEM
import io.lokki.compiler.declarationKindProblem
import io.lokki.compiler.ir.LokkiIrContext
import io.lokki.compiler.ir.effectiveModality
import io.lokki.compiler.ir.inaccessibleClasses
import io.lokki.compiler.ir.isOverridable
import io.lokki.compiler.ir.isPrivateHook
import io.lokki.compiler.ir.isVisibleFrom
import io.lokki.compiler.ir.missingClassesProblem
import io.lokki.compiler.ir.packageFqName
import io.lokki.compiler.ir.spiedConstructorCall
import io.lokki.compiler.ir.typeKey
import io.lokki.compiler.ir.unwrapTemporary
import io.lokki.compiler.memberWithClasses
import io.lokki.compiler.notInterfaceProblem
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrConstructor
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrOverridableMember
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.moduleDescriptor
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.symbols.IrTypeParameterSymbol
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.IrTypeArgument
import org.jetbrains.kotlin.ir.types.IrTypeProjection
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.types.classifierOrNull
import org.jetbrains.kotlin.ir.types.impl.IrStarProjectionImpl
import org.jetbrains.kotlin.ir.types.makeNotNull
import org.jetbrains.kotlin.ir.types.removeAnnotations
import org.jetbrains.kotlin.ir.types.typeWithArguments
import org.jetbrains.kotlin.ir.util.constructedClass
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.getAllSuperclasses
import org.jetbrains.kotlin.ir.util.getPackageFragment
import org.jetbrains.kotlin.ir.util.isFromJava
import org.jetbrains.kotlin.ir.util.isInterface
import org.jetbrains.kotlin.ir.util.isLocal
import org.jetbrains.kotlin.ir.util.isSubtypeOf
import org.jetbrains.kotlin.ir.util.kotlinFqName
import org.jetbrains.kotlin.ir.util.render
import org.jetbrains.kotlin.ir.util.resolveFakeOverrideMaybeAbstractOrFail
import org.jetbrains.kotlin.ir.util.simpleFunctions
import org.jetbrains.kotlin.ir.util.superClass
import org.jetbrains.kotlin.load.java.JavaDescriptorVisibilities

internal sealed interface MockSite {
    class Mocked(val types: MockedTypes, val warning: MockProblem? = null) : MockSite {
        val key: MockClassKey
            get() = types.key

        fun chained(): Mocked = if (types.chained) this else Mocked(types.chained(), warning)
    }

    class Rejected(val problem: MockProblem) : MockSite

    object Reified : MockSite
}

internal sealed interface MockProblem {
    val element: IrElement?
}

internal class NotMockable(
    val type: String,
    val reason: String,
    override val element: IrElement? = null,
    val isFrontendRule: Boolean = false,
) : MockProblem {
    val message: String
        get() = "'$type' cannot be mocked: $reason"

    fun at(element: IrElement): NotMockable = NotMockable(type, reason, element, isFrontendRule)

    fun withReason(reason: String): NotMockable = NotMockable(type, reason, element, isFrontendRule)
}

internal class UnsafeSpyDelegation(
    val type: String,
    val members: List<String>,
    val name: String,
    override val element: IrElement?,
) : MockProblem

internal class DelegatingSpy(val type: String, val name: String, override val element: IrElement?) : MockProblem

internal class Unsupported(val reason: String, override val element: IrElement? = null) : MockProblem

internal class FailedInjection(val problem: InjectionProblem, val type: String) : MockProblem {
    override val element: IrElement?
        get() = null
}

internal class MockRequest(
    val isSpy: Boolean,
    val type: IrType,
    val instance: IrExpression?,
    val interfaces: List<AddedInterface>?,
    val interfacesOwner: String,
    val construction: MockForm.Construction? = null,
)

internal class AddedInterface(val type: IrType, val literal: IrElement)

internal class Mockability(private val file: IrFile, private val lokki: LokkiIrContext) {
    private val classProblems = HashMap<IrClass, ClassProblem?>()

    private class ClassProblem(val reason: String, val isFrontendRule: Boolean)

    @OptIn(ExperimentalStdlibApi::class)
    fun problemOf(type: IrType): NotMockable? {
        val mocked = type.classOrNull?.owner ?: return when (val classifier = type.classifierOrNull) {
            is IrTypeParameterSymbol ->
                NotMockable(classifier.owner.name.asString(), TYPE_PARAMETER_PROBLEM, isFrontendRule = true)
            else -> NotMockable(type.render(), NOT_CLASS_PROBLEM, isFrontendRule = true)
        }
        return classProblems.getOrPutIfMissing(mocked) { mocked.problem() }
            ?.let { NotMockable(mocked.displayName, it.reason, isFrontendRule = it.isFrontendRule) }
    }

    fun site(request: MockRequest): MockSite {
        val constructed = request.instance?.takeIf { request.isSpy }?.unwrapTemporary()?.spiedConstructorCall()
            ?.takeIf { it.symbol.owner.constructedClass.canBeSubclassed }
        val mockedType = constructed?.type ?: if (request.isSpy) request.type else request.type.sealedRepresentative()
        (if (request.isSpy) problemOf(mockedType) else mockProblem(mockedType))?.let { return MockSite.Rejected(it) }
        val mocked = (mockedType as IrSimpleType).mockedClass
        var warning: MockProblem? = null
        val form = when {
            !request.isSpy -> MockForm.Mock
            constructed != null ->
                MockForm.Construction(constructed.symbol.owner, constructed.arguments.map { it != null })
            request.instance != null -> {
                if (!mocked.isInterface) {
                    val members = mocked.unforwardableMembers()
                    val name = mocked.name.asString()
                    if (members.isNotEmpty()) {
                        val problem = UnsafeSpyDelegation(mocked.displayName, members, name, request.instance)
                        return MockSite.Rejected(problem)
                    }
                    warning = DelegatingSpy(mocked.displayName, name, request.instance)
                }
                MockForm.Delegation
            }
            request.construction != null -> request.construction
            mocked.isInterface -> MockForm.Mock
            else -> mocked.constructorWithoutArguments()?.let { MockForm.Construction(it, it.parameters.map { false }) }
                ?: return MockSite.Rejected(NotMockable(mocked.displayName, mocked.spyConstructorProblem()))
        }
        val added = request.interfaces ?: return MockSite.Rejected(
            Unsupported("${request.interfacesOwner} takes only class literals of interfaces, such as 'Closeable::class'")
        )
        for (extra in added) {
            val extraClass = extra.type.classOrNull?.owner
            if (extraClass != null && !extraClass.isInterface) {
                val reason = notInterfaceProblem(request.interfacesOwner)
                val problem = NotMockable(extraClass.displayName, reason, extra.literal, isFrontendRule = true)
                return MockSite.Rejected(problem)
            }
            problemOf(extra.type)?.let { return MockSite.Rejected(it.at(extra.literal)) }
        }
        val type = mockedType.approximated()
        val interfaces = added.map { (it.type as IrSimpleType).approximated() }.distinctBy { it.mockedClass }
        val all = listOf(type) + interfaces
        val extra = interfaces.filter { candidate ->
            all.none { other ->
                other !== candidate && (other.mockedClass == candidate.mockedClass ||
                    candidate.mockedClass in other.mockedClass.getAllSuperclasses())
            }
        }
        return MockSite.Mocked(MockedTypes(type, extra.sortedBy { it.typeKey(emptyList()) }, form), warning)
    }

    fun reified(type: IrType): MockSite {
        val site = site(MockRequest(false, type, null, emptyList(), ""))
        val problem = (site as? MockSite.Rejected)?.problem as? NotMockable ?: return site
        return MockSite.Rejected(NotMockable(problem.type, problem.reason))
    }

    fun child(type: IrType): MockSite.Mocked? {
        val mocked = type.makeNotNull().removeAnnotations().sealedRepresentative()
        if (problemOf(mocked) != null) return null
        return MockSite.Mocked(MockedTypes((mocked as IrSimpleType).approximated(), emptyList(), MockForm.Mock))
    }

    fun childProblem(type: IrType): NotMockable? =
        mockProblem(type.makeNotNull().removeAnnotations().sealedRepresentative())

    private fun mockProblem(type: IrType): NotMockable? {
        val problem = problemOf(type) ?: return null
        val sealed = type.classOrNull?.owner?.takeIf { it.effectiveModality(lokki) == Modality.SEALED }
            ?: return problem
        return NotMockable(problem.type, SEALED_WITHOUT_MOCKABLE_SUBCLASS, isFrontendRule = !sealed.frontendAcceptsMock)
    }

    private fun IrType.sealedRepresentative(): IrType {
        val type = this as? IrSimpleType ?: return this
        val sealed = type.classOrNull?.owner?.takeIf { it.effectiveModality(lokki) == Modality.SEALED } ?: return this
        return sealed.sealedSubclasses.firstNotNullOfOrNull { subclass ->
            type.asSubclass(subclass.owner)?.sealedRepresentative()?.takeIf {
                problemOf(it) == null && subclass.owner.isAccessibleFromFile
            }
        } ?: this
    }

    private fun IrSimpleType.asSubclass(subclass: IrClass): IrSimpleType? {
        if (subclass.isInner) return null
        val parent = subclass.superTypes.firstOrNull { it.classOrNull == classOrNull } as? IrSimpleType ?: return null
        val bound = HashMap<IrTypeParameterSymbol, IrTypeArgument>()
        parent.arguments.zip(arguments).forEach { (declared, requested) ->
            val parameter = (declared as? IrTypeProjection)?.type?.classifierOrNull as? IrTypeParameterSymbol
            if (parameter != null && parameter.owner.parent == subclass) bound[parameter] = requested
        }
        val candidate = subclass.symbol.typeWithArguments(
            subclass.typeParameters.map { bound[it.symbol] ?: IrStarProjectionImpl }
        )
        return candidate.takeIf { it.isSubtypeOf(this, lokki.typeSystem) }
    }

    private val IrClass.isAccessibleFromFile: Boolean
        get() = generateSequence(this) { it.parent as? IrClass }.all { owner ->
            owner.getPackageFragment() == file || owner.isVisibleFrom(file)
        }

    private val IrClass.frontendAcceptsMock: Boolean
        get() = when (effectiveModality(lokki)) {
            Modality.SEALED -> sealedSubclasses.any { it.owner.frontendAcceptsMock }
            else -> frontendProblem() == null
        }

    private fun IrClass.frontendProblem(): String? =
        declarationKindProblem(kind, effectiveModality(lokki), isFromFriendKotlinModule && !isValue)
            ?: missingClassesProblem()

    private fun IrClass.problem(): ClassProblem? {
        val frontend = frontendProblem()
        if (frontend != null) return ClassProblem(frontend, isFrontendRule = true)
        val reason = hiddenAbstractMembers() ?: uninheritedAbstractMembers() ?: membersWithInaccessibleClasses()
        return reason?.let { ClassProblem(it, isFrontendRule = false) }
    }

    private val IrClass.canBeSubclassed: Boolean
        get() = kind == ClassKind.CLASS &&
            effectiveModality(lokki).let { it != Modality.FINAL && it != Modality.SEALED }

    private fun IrClass.spyConstructorProblem(): String =
        when {
            constructors.none { it.isVisibleFrom(file) } -> "none of its constructors is visible from this file"
            else -> "none of its constructors can be called without arguments. Pass a constructor call to " +
                "'spy', such as 'spy($name(...))'"
        }

    private val IrClass.isFromFriendKotlinModule: Boolean
        get() {
            val module = getPackageFragment().moduleDescriptor
            return !isFromJava() && module != file.moduleDescriptor && file.moduleDescriptor.shouldSeeInternalsOf(module)
        }

    private fun IrClass.hiddenAbstractMembers(): String? {
        val hidden = declarations.filterIsInstance<IrOverridableMember>()
            .filter { it.modality == Modality.ABSTRACT && !it.isVisibleFrom(file) }
        if (hidden.isEmpty()) return null
        return "a mock cannot implement its abstract members that are not visible from this file: ${hidden.quoted()}"
    }

    private fun IrClass.uninheritedAbstractMembers(): String? {
        val superclasses = generateSequence(this) { it.superClass }.toList()
        val uninherited = superclasses.withIndex()
            .filter { (index, owner) -> superclasses.take(index).any { it.packageFqName != owner.packageFqName } }
            .flatMap { (_, owner) -> owner.declarations.filterIsInstance<IrOverridableMember>() }
            .filter { it.modality == Modality.ABSTRACT && it.visibility == JavaDescriptorVisibilities.PACKAGE_VISIBILITY }
        if (uninherited.isEmpty()) return null
        return "a mock cannot implement its package-private abstract members that it inherits through a class of " +
            "another package: ${uninherited.quoted()}"
    }

    private fun IrClass.membersWithInaccessibleClasses(): String? {
        val inaccessible = simpleFunctions()
            .filter { it.isOverridable(lokki) && it.resolveFakeOverrideMaybeAbstractOrFail().isVisibleFrom(file) }
            .associateWith { it.inaccessibleClasses(file) }
            .filterValues { it.isNotEmpty() }
        if (inaccessible.isEmpty()) return null
        val members = inaccessible.mapNotNull { (member, classes) -> member.withClasses(classes) }.toSortedSet()
        val packages = inaccessible.values.flatten().map { it.packageFqName.asString() }.distinct()
        val advice = packages.singleOrNull()?.let { ". Create the mock in a file of package '$it'" }.orEmpty()
        return "a mock cannot override its members that refer to package-private classes of another package: " +
            members.joinToString() + advice
    }

    private fun IrClass.constructorWithoutArguments(): IrConstructor? {
        val candidates = constructors.filter { constructor ->
            constructor.isVisibleFrom(file) && constructor.parameters.all { it.defaultValue != null }
        }.toList()
        return candidates.find { it.parameters.isEmpty() } ?: candidates.singleOrNull()
    }

    private fun IrClass.unforwardableMembers(): List<String> =
        simpleFunctions()
            .filter { member ->
                !DescriptorVisibilities.isPrivate(member.visibility) && !member.isPrivateHook &&
                    (!member.isOverridable(lokki) || member.visibility in PROTECTED || !member.isVisibleFrom(file))
            }
            .map { it.memberName }
            .distinct()
            .sorted()
}

internal val IrClass.displayName: String
    get() = if (isLocal) name.asString() else kotlinFqName.asString()

private val IrSimpleFunction.memberName: String
    get() = (correspondingPropertySymbol?.owner?.name ?: name).asString()

private fun IrSimpleFunction.withClasses(classes: List<IrClass>): String? {
    if (classes.isEmpty()) return null
    return memberWithClasses(memberName, classes.map { it.kotlinFqName.asString() })
}

private fun List<IrOverridableMember>.quoted(): String =
    map { it.name.asString() }.distinct().sorted().joinToString { "'$it'" }

private val PROTECTED = setOf(
    DescriptorVisibilities.PROTECTED,
    JavaDescriptorVisibilities.PROTECTED_AND_PACKAGE,
    JavaDescriptorVisibilities.PROTECTED_STATIC_VISIBILITY,
)
