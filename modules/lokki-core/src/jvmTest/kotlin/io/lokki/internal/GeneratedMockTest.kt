package io.lokki.internal

import io.lokki.LokkiException
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GeneratedMockTest {
    @Test
    fun testJdkProxyIsNamedInsteadOfItsToString() {
        val proxy = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(Runnable::class.java)) { _, _, _ -> "Repo(#1)" }

        val message = assertFailsWith<LokkiException> { GeneratedMock.of(proxy) }.message

        assertEquals(
            "the JDK dynamic proxy ${proxy.javaClass.name} is not a lokki mock: stub and verify the mock behind it, " +
                "or proxy the class of the mock instead of its interfaces (proxyTargetClass = true in Spring AOP)",
            message,
        )
    }
}
