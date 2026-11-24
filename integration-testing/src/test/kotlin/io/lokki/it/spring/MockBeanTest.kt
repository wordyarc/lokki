package io.lokki.it.spring

import io.lokki.InternalLokkiApi
import io.lokki.every
import io.lokki.internal.ExternalMocks
import io.lokki.isMock
import io.lokki.junit5.LokkiExtension
import io.lokki.lokkiScope
import io.lokki.spring.MockBean
import io.lokki.spring.SpyBean
import io.lokki.verify
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

abstract class BeanOverrides(private val scoped: Boolean) {
    @MockBean(relaxed = true)
    lateinit var greeter: Greeter

    @SpyBean
    lateinit var repository: Repository

    @Autowired
    lateinit var service: Service

    @Test
    fun mockReplacesTheBean() {
        assertTrue(isMock(greeter))
        assertEquals("", service.welcome(1), "a stub of another test is left on the mock")
        every { greeter.greet(any()) } returns "stubbed"

        assertEquals("stubbed", service.welcome(1))
        verify { greeter.greet("stored 1") }
    }

    @Test
    fun spyWrapsTheBean() {
        assertEquals("", greeter.greet("x"), "a stub of another test is left on the mock")
        every { greeter.greet(any()) } answers { "hi ${firstArg<String>()}" }
        every { repository.load(2) } returns "spied"

        assertEquals("hi spied", service.welcome(2))
        assertEquals("hi stored 3", service.welcome(3))
        verify { repository.load(3) }
    }

    @Test
    @OptIn(InternalLokkiApi::class)
    fun clearingFollowsTheScopeOfThisTest() {
        lokkiScope { assertEquals(scoped, ExternalMocks.isScoped(this@BeanOverrides)) }
    }
}

@SpringJUnitConfig(Beans::class)
class MockBeanTest : BeanOverrides(scoped = false)

@SpringJUnitConfig(Beans::class)
@ExtendWith(LokkiExtension::class)
class MockBeanInScopeTest : BeanOverrides(scoped = true)
