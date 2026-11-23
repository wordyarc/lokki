package io.lokki.compiler.test.services

import org.jetbrains.kotlin.test.directives.model.SimpleDirectivesContainer

object LokkiDirectives : SimpleDirectivesContainer() {
    val WARNING_LEVEL by stringDirective("Sets the level of a warning as -Xwarning-level does: NAME:(error|warning|disabled)")
    val WITH_KOTLINX_COROUTINES by directive("Adds kotlinx-coroutines-core to the classpath of the compilation and the box runtime")
    val MOCKABLE by stringDirective("Passes the patterns to the mockable option of the compiler plugin, as lokki { mockable(...) } does")
    val RESTRICTED by stringDirective("Passes the patterns to the restricted option of the compiler plugin, as lokki { restricted(...) } does")
    val CHECK_SOURCELESS_DIAGNOSTICS by directive("Compares the diagnostics reported without a source element with the .sourceless.txt file")
}
