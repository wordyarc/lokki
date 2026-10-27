// RUN_PIPELINE_TILL: FRONTEND
// RENDER_DIAGNOSTICS_FULL_TEXT

package sample

import io.lokki.every
import io.lokki.mock
import io.lokki.verify

interface UseCase {
    operator fun invoke(id: Int): String
}

interface Runner {
    fun run(task: (Int) -> Unit): Int
}

fun UseCase.stubbed() {
    every { <!LOKKI_INVOKE_MATCHER_SHADOWS_OPERATOR!>invoke<Int, String>(1)<!> } returns { "a" }
}

fun test(useCase: UseCase, runner: Runner) {
    mock<UseCase> { every { <!LOKKI_INVOKE_MATCHER_SHADOWS_OPERATOR!>invoke<Int, String>(1)<!> } returns { "a" } }
    with(useCase) { verify { <!LOKKI_INVOKE_MATCHER_SHADOWS_OPERATOR!>invoke<Int, String>(1)<!> } }
    with(useCase) { every { <!LOKKI_INVOKE_MATCHER_SHADOWS_OPERATOR!>invoke<Int, String>(1)<!>.invoke(2) } returns "a" }
    with(runner) { every { <!LOKKI_MATCHER_NOT_IN_ARGUMENT_POSITION!>invoke<Int, String>(1)<!> } returns { "a" } }
    mock<UseCase> { <!CANNOT_INFER_PARAMETER_TYPE!>every<!> <!CANNOT_INFER_PARAMETER_TYPE!>{ <!LOKKI_INVOKE_MATCHER_SHADOWS_OPERATOR!><!CANNOT_INFER_PARAMETER_TYPE, CANNOT_INFER_PARAMETER_TYPE!>invoke<!>(<!CANNOT_INFER_PARAMETER_TYPE!>any<!>())<!> }<!> returns "a" }
    <!CANNOT_INFER_PARAMETER_TYPE!>with<!>(useCase) { <!CANNOT_INFER_PARAMETER_TYPE!>every<!> <!CANNOT_INFER_PARAMETER_TYPE!>{ <!LOKKI_INVOKE_MATCHER_SHADOWS_OPERATOR!><!CANNOT_INFER_PARAMETER_TYPE!>invoke<!>(1)<!> }<!> returns "a" }

    mock<UseCase> { every { this@mock.invoke(any()) } returns "a" }
    with(useCase) { every { this@with(1) } returns "a" }
    every { useCase(any()) } returns "a"
    every { useCase.invoke(any()) } returns "a"
    every { runner.run(invoke(1)) } returns 1
}
