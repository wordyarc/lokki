package io.lokki.gradle

fun declaration(header: String, vararg members: String): String =
    "package sample\n\n$header {\n${members.joinToString("\n") { "    $it" }}\n}\n"

fun exercise(type: String, call: String): String =
    """
    package sample

    fun exercise(mock: $type): Any? = mock.$call

    fun exercisedMember(): String = "${type.substringBefore('<')}.${call.substringBefore('(')}"
    """

fun additionalInterfaceExercise(call: String): String =
    """
    package sample

    fun exercise(mock: Named): Any? = mock.$call

    fun exercisedMember(): String = "Car.${call.substringBefore('(')}"
    """

fun mockTest(name: String, type: String, mock: String = "mock<$type>()"): String =
    """
    package sample

    import io.lokki.LokkiException
    import io.lokki.mock
    import kotlin.test.Test
    import kotlin.test.assertEquals
    import kotlin.test.assertFailsWith

    class $name {
        @Test
        fun strictMockHasNoAnswer() {
            val exception = assertFailsWith<LokkiException> { exercise($mock) }
            val member = exception.message?.replace(Regex("(?s)\\(#\\d+\\)\\.(\\w+)\\(.*"), ".${'$'}1")
            assertEquals("no answer found for ${'$'}{exercisedMember()}", member)
        }
    }
    """

const val UNRELATED_TEST: String =
    """
    package sample

    import kotlin.test.Test

    class UnrelatedTest {
        @Test
        fun passes() {
        }
    }
    """
