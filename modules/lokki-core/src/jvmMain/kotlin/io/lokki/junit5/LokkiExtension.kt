package io.lokki.junit5

import io.lokki.InternalLokkiApi
import io.lokki.LokkiException
import io.lokki.LokkiScope
import io.lokki.MockAnnotations
import io.lokki.checkUnnecessaryStub
import io.lokki.confirmVerified
import io.lokki.internal.MockParameter
import io.lokki.internal.MockParameterValues
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.DynamicTestInvocationContext
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.InvocationInterceptor
import org.junit.jupiter.api.extension.InvocationInterceptor.Invocation
import org.junit.jupiter.api.extension.ParameterContext
import org.junit.jupiter.api.extension.ParameterResolver
import org.junit.jupiter.api.extension.ReflectiveInvocationContext
import org.junit.jupiter.api.extension.TestInstancePostProcessor
import org.junit.jupiter.api.extension.TestInstantiationAwareExtension.ExtensionContextScope
import org.junit.platform.commons.support.AnnotationSupport.isAnnotated
import java.lang.annotation.Inherited
import java.lang.reflect.Constructor
import java.lang.reflect.Method

@SubclassOptInRequired(InternalLokkiApi::class)
public open class LokkiExtension :
    BeforeAllCallback,
    BeforeEachCallback,
    AfterEachCallback,
    AfterAllCallback,
    TestInstancePostProcessor,
    ParameterResolver,
    InvocationInterceptor {
    final override fun getTestInstantiationExtensionContextScope(rootContext: ExtensionContext): ExtensionContextScope =
        ExtensionContextScope.TEST_METHOD

    final override fun beforeAll(context: ExtensionContext) {
        scope(context)
    }

    final override fun postProcessTestInstance(testInstance: Any, context: ExtensionContext) {
        LokkiScope.withCurrent(scope(context)) { MockAnnotations.init(testInstance) }
    }

    final override fun supportsParameter(parameter: ParameterContext, context: ExtensionContext): Boolean =
        parameter.isAnnotated(MockParameter::class.java)

    final override fun resolveParameter(parameter: ParameterContext, context: ExtensionContext): Any? {
        val annotation = parameter.findAnnotation(MockParameter::class.java).get()
        val key = "${context.uniqueId}:${parameter.declaringExecutable}"
        val values = context.store.getOrComputeIfAbsent(
            key,
            { MockParameterValues(annotation.factory.java.getDeclaredConstructor().newInstance()) },
            MockParameterValues::class.java,
        )
        return LokkiScope.withCurrent(scope(context)) { values[annotation.index] }
    }

    final override fun beforeEach(context: ExtensionContext) {
        scope(context)
    }

    final override fun afterEach(context: ExtensionContext) {
        close(context, context.executionException.orElse(null))
    }

    final override fun afterAll(context: ExtensionContext) {
        close(context, context.executionException.orElse(null))
    }

    final override fun <T> interceptTestClassConstructor(
        invocation: Invocation<T>,
        invocationContext: ReflectiveInvocationContext<Constructor<T>>,
        extensionContext: ExtensionContext,
    ): T = proceed(invocation, extensionContext)

    final override fun interceptBeforeAllMethod(
        invocation: Invocation<Void>,
        invocationContext: ReflectiveInvocationContext<Method>,
        extensionContext: ExtensionContext,
    ) {
        proceed(invocation, extensionContext)
    }

    final override fun interceptBeforeEachMethod(
        invocation: Invocation<Void>,
        invocationContext: ReflectiveInvocationContext<Method>,
        extensionContext: ExtensionContext,
    ) {
        proceed(invocation, extensionContext)
    }

    final override fun interceptTestMethod(
        invocation: Invocation<Void>,
        invocationContext: ReflectiveInvocationContext<Method>,
        extensionContext: ExtensionContext,
    ) {
        proceed(invocation, extensionContext)
    }

    final override fun interceptTestTemplateMethod(
        invocation: Invocation<Void>,
        invocationContext: ReflectiveInvocationContext<Method>,
        extensionContext: ExtensionContext,
    ) {
        proceed(invocation, extensionContext)
    }

    final override fun <T> interceptTestFactoryMethod(
        invocation: Invocation<T>,
        invocationContext: ReflectiveInvocationContext<Method>,
        extensionContext: ExtensionContext,
    ): T = proceed(invocation, extensionContext)

    final override fun interceptDynamicTest(
        invocation: Invocation<Void>,
        invocationContext: DynamicTestInvocationContext,
        extensionContext: ExtensionContext,
    ) {
        try {
            proceed(invocation, extensionContext)
        } catch (e: Throwable) {
            close(extensionContext, e)
            throw e
        }
        close(extensionContext, null)
    }

    final override fun interceptAfterEachMethod(
        invocation: Invocation<Void>,
        invocationContext: ReflectiveInvocationContext<Method>,
        extensionContext: ExtensionContext,
    ) {
        proceed(invocation, extensionContext)
    }

    final override fun interceptAfterAllMethod(
        invocation: Invocation<Void>,
        invocationContext: ReflectiveInvocationContext<Method>,
        extensionContext: ExtensionContext,
    ) {
        proceed(invocation, extensionContext)
    }

    @Inherited
    @Retention(AnnotationRetention.RUNTIME)
    @Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
    public annotation class KeepMocks

    @Inherited
    @Retention(AnnotationRetention.RUNTIME)
    @Target(AnnotationTarget.CLASS)
    public annotation class ConfirmVerification

    @Inherited
    @Retention(AnnotationRetention.RUNTIME)
    @Target(AnnotationTarget.CLASS)
    public annotation class CheckUnnecessaryStub

    public companion object {
        public const val KEEP_MOCKS_PROPERTY: String = "lokki.junit.extension.keepMocks"
        public const val CONFIRM_VERIFICATION_PROPERTY: String = "lokki.junit.extension.confirmVerification"
        public const val CHECK_UNNECESSARY_STUB_PROPERTY: String = "lokki.junit.extension.checkUnnecessaryStub"
    }
}

private val namespace = ExtensionContext.Namespace.create(LokkiExtension::class.java)

private val supportsTestInstantiationScope = runCatching { ExtensionContextScope.TEST_METHOD }.isSuccess

private class StoredScope(val scope: LokkiScope) : ExtensionContext.Store.CloseableResource, AutoCloseable {
    override fun close() {
        scope.close()
    }
}

private fun <T> proceed(invocation: Invocation<T>, context: ExtensionContext): T =
    LokkiScope.withCurrent(scope(context)) { invocation.proceed() }

private fun scope(context: ExtensionContext): LokkiScope {
    if (!supportsTestInstantiationScope) throw LokkiException("LokkiExtension requires JUnit Jupiter 5.12+")
    if (context.keepsMocks()) return context.parentScope()
    return context.store.getOrComputeIfAbsent(
        context.uniqueId,
        { StoredScope(LokkiScope.open(context.parentScope(), context.scopeName())) },
        StoredScope::class.java,
    ).scope
}

private fun close(context: ExtensionContext, failure: Throwable?) {
    val scope = context.store.remove(context.uniqueId, StoredScope::class.java)?.scope ?: return
    val error = runCatching { LokkiScope.withCurrent(scope) { context.verifyWindow() } }.exceptionOrNull()
    when {
        failure != null -> {
            error?.let(failure::addSuppressed)
            scope.close(failure)
        }
        error != null -> {
            scope.close(error)
            throw error
        }
        else -> scope.close()
    }
}

private fun ExtensionContext.verifyWindow() {
    if (isEnabled(LokkiExtension.ConfirmVerification::class.java, LokkiExtension.CONFIRM_VERIFICATION_PROPERTY)) {
        confirmVerified()
    }
    if (isEnabled(LokkiExtension.CheckUnnecessaryStub::class.java, LokkiExtension.CHECK_UNNECESSARY_STUB_PROPERTY)) {
        checkUnnecessaryStub()
    }
}

private fun ExtensionContext.isEnabled(annotation: Class<out Annotation>, parameter: String): Boolean =
    testClasses().any { isAnnotated(it, annotation) } || flag(parameter)

private fun ExtensionContext.testClasses(): Sequence<Class<*>> =
    lineage().mapNotNull { it.testClass.orElse(null) }.distinct()

private fun ExtensionContext.keepsMocks(): Boolean {
    val test = lineage().firstOrNull { it.testMethod.isPresent } ?: return false
    return isAnnotated(test.testMethod, LokkiExtension.KeepMocks::class.java) ||
        test.isEnabled(LokkiExtension.KeepMocks::class.java, LokkiExtension.KEEP_MOCKS_PROPERTY)
}

private fun ExtensionContext.parentScope(): LokkiScope =
    lineage().drop(1).firstNotNullOfOrNull { it.store.get(it.uniqueId, StoredScope::class.java)?.scope }
        ?: LokkiScope.root

private fun ExtensionContext.scopeName(): String =
    lineage().filter { it.parent.isPresent }.map { it.displayName }.toList().asReversed().joinToString(" > ")

private fun ExtensionContext.flag(name: String): Boolean =
    getConfigurationParameter(name).map { it.toBoolean() }.orElse(false)

private fun ExtensionContext.lineage(): Sequence<ExtensionContext> = generateSequence(this) { it.parent.orElse(null) }

private val ExtensionContext.store: ExtensionContext.Store
    get() = getStore(namespace)
