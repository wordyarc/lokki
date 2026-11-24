package io.lokki.it

import io.lokki.LokkiException
import io.lokki.every
import io.lokki.isMock
import io.lokki.mock
import io.lokki.spy
import io.lokki.verify
import org.aopalliance.intercept.MethodInterceptor
import org.aopalliance.intercept.MethodInvocation
import org.springframework.aop.framework.ProxyFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

class SpringProxyTest {
    private val advised = ArrayList<String>()

    @Test
    fun mockOfInterfaceIsStubbedAndVerifiedThroughClassProxy() {
        val car = mock<Car>()
        val proxy = proxied(car)
        every { proxy.drive(1) } returns "stuck"

        assertEquals("stuck", Garage(proxy).leave())
        verify(exactly = 1) { proxy.drive(1) }
        verify(exactly = 1) { car.drive(1) }
        assertTrue(isMock(proxy))
        assertTrue("drive" in advised, advised.toString())
    }

    @Test
    fun mockOfClassIsStubbedOnTheTargetAndCalledThroughClassProxy() {
        val car = mock<Car>()
        val mechanic = mock<Mechanic>()
        val proxy = proxied(mechanic)
        every { mechanic.repair(car) } returns "later"

        assertEquals("later", proxy.repair(car))
        verify { proxy.repair(car) }
        assertTrue("repair" in advised, advised.toString())
    }

    @Test
    fun spyRunsTheOriginalBehindClassProxy() {
        val car = mock<Car> { every { plate } returns "A-1" }
        val workshop = spy(Workshop("north"))
        val proxy = proxied(workshop)

        assertEquals(listOf("A-1 at north"), proxy.service(listOf(car)))
        verify { proxy.schedule(car) }
        every { proxy.schedule(car) } returns "later"
        assertEquals(listOf("later"), proxy.service(listOf(car)))
    }

    @Test
    fun interfaceProxyOfMockIsNotTakenForTheMock() {
        val car = mock<Car>()
        val factory = ProxyFactory(car)
        factory.setInterfaces(Car::class.java)
        val proxy = factory.proxy as Car

        val failure = assertFailsWith<LokkiException> { every { proxy.drive(1) } returns "stuck" }

        assertEquals(
            "the JDK dynamic proxy ${proxy.javaClass.name} is not a lokki mock: stub and verify the mock behind it, " +
                "or proxy the class of the mock instead of its interfaces (proxyTargetClass = true in Spring AOP)",
            failure.message,
        )
    }

    private inline fun <reified T : Any> proxied(target: T): T =
        proxied(target) { invocation: MethodInvocation ->
            advised += invocation.method.name
            invocation.proceed()
        }

    private inline fun <reified T : Any> proxied(target: T, interceptor: MethodInterceptor): T {
        val factory = ProxyFactory(target)
        factory.isProxyTargetClass = true
        factory.addAdvice(interceptor)
        return (factory.proxy as T).also { assertNotSame(target, it) }
    }
}
