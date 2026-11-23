// WITH_STDLIB
package sample

import io.lokki.AdditionalInterface
import io.lokki.InjectMocks
import io.lokki.Mock
import io.lokki.RelaxedMock
import io.lokki.Spy
import io.lokki.every
import io.lokki.isMock

interface Dep {
    fun value(): Int
}

open class Tool(val dep: Dep) {
    open fun use(): Int = dep.value() + 1
}

open class Plain {
    open fun name(): String = "plain"
}

class Service(val dep: Dep, val tool: Tool) {
    var late: Plain? = null
}

class Fixture(@Mock val inConstructor: Dep, @RelaxedMock other: Dep) {
    val relaxed = other.value()

    fun method(
        @InjectMocks service: Service,
        @Mock dep: Dep,
        @Spy tool: Tool,
        @Spy late: Plain,
        @Mock(name = "named", relaxed = true) @AdditionalInterface(Runnable::class) extra: Plain,
    ): String {
        if (service.dep !== dep || service.tool !== tool) return "Fail constructor arguments"
        if (service.late !== late) return "Fail property injection"
        if (!isMock(tool) || tool.dep !== dep) return "Fail spy with constructor arguments"
        every { dep.value() } returns 41
        if (tool.use() != 42) return "Fail spy calls the original"
        if (late.name() != "plain") return "Fail spy"
        if (extra !is Runnable || "named" !in extra.toString()) return "Fail name or interface"
        return "OK"
    }
}

class Unique {
    fun method(@InjectMocks service: Single, @Mock dep: Dep): String =
        if (service.dep === dep) "OK" else "Fail"
}

class Single(val dep: Dep)

@Suppress("UNCHECKED_CAST")
private fun resolve(executable: java.lang.reflect.Executable): Array<Any?> {
    val type = Class.forName("io.lokki.internal.MockParameter") as Class<Annotation>
    val annotations = executable.parameters.map { it.getAnnotation(type) }
    val factoryClass = type.getMethod("factory").invoke(annotations.first()) as Class<*>
    val factory = factoryClass.getDeclaredConstructor().newInstance()
    val valuesClass = Class.forName("io.lokki.internal.MockParameterValues")
    val values = valuesClass.constructors.single().newInstance(factory)
    val get = valuesClass.getMethod("get", Int::class.java)
    return annotations.map { get.invoke(values, type.getMethod("index").invoke(it)) }.toTypedArray()
}

fun box(): String {
    val constructor = Fixture::class.java.constructors.single()
    val fixture = constructor.newInstance(*resolve(constructor)) as Fixture
    if (!isMock(fixture.inConstructor) || fixture.relaxed != 0) return "Fail constructor parameters"
    val method = Fixture::class.java.methods.single { it.name == "method" }
    val result = method.invoke(fixture, *resolve(method))
    if (result != "OK") return result as String
    val first = Unique::class.java.methods.single { it.name == "method" }
    val a = resolve(first)
    val b = resolve(first)
    if (a[1] === b[1]) return "Fail mock shared between calls"
    return first.invoke(Unique(), *a) as String
}
