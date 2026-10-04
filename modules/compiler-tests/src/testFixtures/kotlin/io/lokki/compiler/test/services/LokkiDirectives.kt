package io.lokki.compiler.test.services

import org.jetbrains.kotlin.test.directives.model.SimpleDirectivesContainer

object LokkiDirectives : SimpleDirectivesContainer() {
    val WARNING_LEVEL by stringDirective("Sets the level of a warning as -Xwarning-level does: NAME:(error|warning|disabled)")
}
