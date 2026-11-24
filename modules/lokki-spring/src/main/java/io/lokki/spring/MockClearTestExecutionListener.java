package io.lokki.spring;

import org.springframework.core.Ordered;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.support.AbstractTestExecutionListener;

final class MockClearTestExecutionListener extends AbstractTestExecutionListener {
    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE - 100;
    }

    @Override
    public void beforeTestMethod(TestContext testContext) {
        MockClears.INSTANCE.clear(testContext, MockClear.BEFORE);
    }

    @Override
    public void afterTestMethod(TestContext testContext) {
        MockClears.INSTANCE.clear(testContext, MockClear.AFTER);
    }
}
