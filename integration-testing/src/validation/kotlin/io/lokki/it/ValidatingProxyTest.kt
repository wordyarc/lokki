package io.lokki.it

import io.lokki.every
import io.lokki.isMock
import io.lokki.mock
import io.lokki.verify
import jakarta.validation.ConstraintViolationException
import jakarta.validation.Valid
import jakarta.validation.Validation
import jakarta.validation.constraints.NotBlank
import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator
import org.springframework.aop.framework.ProxyFactory
import org.springframework.validation.beanvalidation.MethodValidationInterceptor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ValidatingProxyTest {
    @Test
    fun mockIsStubbedVerifiedAndValidatedThroughValidatingProxy() {
        val checkout = mock<Checkout>()
        val factory = ProxyFactory(checkout)
        factory.isProxyTargetClass = true
        factory.addAdvice(MethodValidationInterceptor(validator))
        val proxy = factory.proxy as Checkout
        every { proxy.place(any()) } returns "stubbed"

        assertEquals("stubbed", proxy.place(Order("1")))
        assertFailsWith<ConstraintViolationException> { proxy.place(Order("")) }
        verify(exactly = 1) { proxy.place(any()) }
        assertTrue(isMock(proxy))
    }
}

class Order(@field:NotBlank val id: String)

open class Checkout {
    open fun place(@Valid order: Order): String = "placed ${order.id}"

    open suspend fun confirm(@Valid order: Order, tags: List<String>): String = "confirmed ${order.id} $tags"
}

private val validator = Validation.byDefaultProvider().configure()
    .messageInterpolator(ParameterMessageInterpolator())
    .buildValidatorFactory()
    .validator
