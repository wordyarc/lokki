import io.lokki.LokkiException
import io.lokki.every
import io.lokki.mock
import java.nio.file.attribute.BasicFileAttributes
import java.util.function.IntPredicate

fun box(): String {
    val attributes = mock<BasicFileAttributes>()
    every { attributes.isDirectory } returns true
    if (!attributes.isDirectory) return "Fail: synthetic property"

    val appendable = mock<Appendable>()
    every { appendable.append("x") } returns appendable
    if (appendable.append("x") !== appendable) return "Fail: value of a platform type"

    val predicate = mock<IntPredicate>()
    every { predicate.test(any()) } returns true
    if (!predicate.test(5)) return "Fail: matcher"

    try {
        predicate.negate()
        return "Fail: default method is not mocked"
    } catch (e: LokkiException) {
        if (e.message != "no answer found for IntPredicate.negate") return "Fail message: ${e.message}"
    }

    return "OK"
}
