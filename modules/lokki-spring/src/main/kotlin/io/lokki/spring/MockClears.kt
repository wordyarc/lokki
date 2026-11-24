package io.lokki.spring

import io.lokki.internal.ExternalMocks
import org.springframework.context.ApplicationContext
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.test.context.TestContext

internal object MockClears {
    fun clear(testContext: TestContext, clear: MockClear) {
        if (ExternalMocks.isScoped(testContext.testInstance) || !testContext.hasApplicationContext()) return
        var context: ApplicationContext? = testContext.applicationContext
        while (context is ConfigurableApplicationContext) {
            (context.beanFactory.getSingleton(MockBeans::class.java.name) as MockBeans?)?.clear(clear)
            context = context.parent
        }
    }
}
