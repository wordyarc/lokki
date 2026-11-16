package io.lokki.compiler

import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.Modality

internal fun declarationKindProblem(kind: ClassKind, modality: Modality?, suggestsMockable: Boolean): String? =
    when {
        kind == ClassKind.OBJECT -> "it is an object"
        kind == ClassKind.ENUM_CLASS || kind == ClassKind.ENUM_ENTRY -> "it is an enum class"
        kind == ClassKind.ANNOTATION_CLASS -> "it is an annotation class"
        modality == Modality.SEALED -> "it is sealed"
        kind == ClassKind.CLASS && modality == Modality.FINAL -> "it is a final class. " + if (suggestsMockable) {
            "Extract an interface, make the class and its members open, or add it to lokki { mockable(...) }"
        } else {
            "Extract an interface or make the class and its members open"
        }
        else -> null
    }

internal fun missingSupertypesProblem(supertypes: Collection<String>): String =
    "its supertypes are missing from the classpath: ${supertypes.joinToString()}. $ADD_DEPENDENCY"

internal fun missingMemberClassesProblem(members: Collection<String>): String =
    "it or its supertypes declare members that refer to classes missing from the classpath: " +
        "${members.joinToString()}. $ADD_DEPENDENCY"

internal fun memberWithClasses(member: String, classes: Collection<String>): String =
    "'$member' (${classes.joinToString()})"

internal fun notInterfaceProblem(owner: String): String = "it is not an interface, and $owner takes only interfaces"

internal const val TYPE_PARAMETER_PROBLEM = "it is a type parameter"
internal const val NOT_CLASS_PROBLEM = "it is not a class"
private const val ADD_DEPENDENCY =
    "Add the dependency with these classes to the test compilation and runtime, e.g. with 'testImplementation'"
internal const val CHILD_MOCK_NEEDED = "A matcher in a link of a call chain needs a child mock of its return type"
