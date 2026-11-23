package io.lokki.compiler.ir

import io.lokki.compiler.IncompatibleRuntimeException
import io.lokki.compiler.PLUGIN
import kotlin.coroutines.cancellation.CancellationException
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.path

internal class LokkiInternalError(message: String, cause: Throwable) : IllegalStateException(message, cause)

internal inline fun <T> IrFile.locatingInternalErrors(element: IrElement? = null, block: () -> T): T =
    try {
        block()
    } catch (failure: RuntimeException) {
        throw when (failure) {
            is LokkiInternalError, is IncompatibleRuntimeException, is CancellationException -> failure
            else -> LokkiInternalError(internalErrorMessage(element, failure), failure)
        }
    }

internal fun IrFile.internalErrorMessage(element: IrElement?, failure: Throwable): String {
    val position = element?.startOffset?.takeIf { it >= 0 }
        ?.let { ":${fileEntry.getLineNumber(it) + 1}:${fileEntry.getColumnNumber(it) + 1}" }
        .orEmpty()
    return "$PLUGIN failed on $path$position: $failure. This is a bug in lokki, please report it at $ISSUES"
}

private const val ISSUES = "https://github.com/wordyarc/lokki/issues"
