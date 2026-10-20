package io.lokki.compiler.test.services

import org.jetbrains.kotlin.test.directives.model.SimpleDirectivesContainer

object LokkiDirectives : SimpleDirectivesContainer() {
    val WARNING_LEVEL by stringDirective("Sets the level of a warning as -Xwarning-level does: NAME:(error|warning|disabled)")
    val WITH_KOTLINX_COROUTINES by directive("Adds kotlinx-coroutines-core to the classpath of the compilation and the box runtime")
}
