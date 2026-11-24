// PARAMETERS_METADATA

package sample

import io.lokki.mock
import java.lang.reflect.Method

@Retention(AnnotationRetention.RUNTIME)
annotation class Marker(val value: String)

class Request

@JvmInline
value class Code(val value: String)

interface Service {
    @Marker("method")
    fun plain(@Marker("request") request: Request, tags: List<String>, vararg ids: Int): String

    suspend fun deploy(@Marker("request") request: Request, options: Map<String, List<Int>>): String

    fun Request.label(width: Int = 2): String

    fun <T : Any> convert(value: T, type: Class<T>): T

    fun code(code: Code, fallback: Code?): Code

    val size: Int

    var title: String?
}

open class Base {
    open fun find(id: Long, filter: Set<String>?, @Marker("callback") callback: (String) -> Unit): String = ""

    protected open suspend fun hidden(names: List<String>): Int = 0
}

private fun Method.view(): String =
    "$name ${genericReturnType.typeName} ${annotations.toList()} varargs=$isVarArgs " +
        parameters.joinToString { parameter ->
            "${parameter.name} synthetic=${parameter.isSynthetic} implicit=${parameter.isImplicit} " +
                "${parameter.parameterizedType.typeName} ${parameter.annotations.toList()}"
        }

private fun mismatch(mocked: Class<*>, mock: Class<*>): String? {
    for (member in mocked.declaredMethods.filter { !it.isSynthetic }) {
        val override = mock.getDeclaredMethod(member.name, *member.parameterTypes)
        if (override.view() != member.view()) return "${member.view()}\n${override.view()}"
    }
    val internals = mock.declaredMethods.filter { it.name.startsWith("lokki\$") }
    val visible = internals.filter { !it.isSynthetic }.map { it.name }
    if (visible != listOf("lokki\$state")) return "visible internals of ${mock.simpleName}: $visible"
    val getters = mock.methods.filter { it.name.startsWith("getLokki") }
    if (getters.isNotEmpty()) return "getters of lokki state in ${mock.simpleName}: $getters"
    return null
}

fun box(): String {
    mismatch(Service::class.java, mock<Service>().javaClass)?.let { return "Fail: $it" }
    mismatch(Base::class.java, mock<Base>().javaClass)?.let { return "Fail: $it" }
    return "OK"
}
