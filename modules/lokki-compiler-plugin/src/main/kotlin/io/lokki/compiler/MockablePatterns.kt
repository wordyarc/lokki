package io.lokki.compiler

import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.isSubpackageOf
import org.jetbrains.kotlin.name.isValidJavaFqName

internal class MockablePatterns(patterns: List<String>) {
    private val packages = patterns.filter { it.endsWith(SUBPACKAGES) }.map { FqName(it.removeSuffix(SUBPACKAGES)) }

    val names: List<FqName> = patterns.filterNot { it.endsWith(SUBPACKAGES) }.map(::FqName)

    fun matches(classId: ClassId): Boolean =
        classId.asSingleFqName() in names || packages.any { classId.packageFqName.isSubpackageOf(it) }

    fun matches(callableId: CallableId): Boolean =
        callableId.asSingleFqName() in names || packages.any { callableId.packageName.isSubpackageOf(it) }
}

internal fun mockablePatternProblem(pattern: String, option: String = BuildConfig.MOCKABLE_OPTION): String? {
    val name = pattern.removeSuffix(SUBPACKAGES)
    if (name.isNotEmpty() && isValidJavaFqName(name)) return null
    return "Invalid $option pattern '$pattern': expected a package followed by '$SUBPACKAGES', a class or an " +
        "annotation, e.g. 'com.acme.**', 'com.acme.Engine' or 'com.acme.Mockable'"
}

private const val SUBPACKAGES = ".**"
