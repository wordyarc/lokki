package io.lokki.internal

import io.lokki.LokkiException
import io.lokki.LokkiScope
import io.lokki.Matcher
import io.lokki.clearMocks
import io.lokki.confirmVerified

@PublishedApi
internal class Redirect(
    internal val target: Any,
    internal val name: String,
    internal val factory: (MockSpec) -> Any,
    internal val install: (Any?) -> Unit,
    internal val flags: Int,
) {
    constructor(target: Any, name: String, factory: (MockSpec) -> Any, install: (Any?) -> Unit) :
        this(target, name, factory, install, 0)
}

@PublishedApi
internal object Redirects {
    private val active = ArrayList<ActiveRedirect>()

    fun mockObject(redirects: Array<Redirect>) {
        install(redirects)
    }

    inline fun mockObject(redirects: Array<Redirect>, block: () -> Unit) {
        val installed = install(redirects)
        try {
            block()
        } finally {
            uninstall(installed)
        }
    }

    fun mockStatic(redirects: Array<Redirect>) {
        installStatic(redirects)
    }

    inline fun mockStatic(redirects: Array<Redirect>, block: () -> Unit) {
        val installed = installStatic(redirects)
        try {
            block()
        } finally {
            uninstall(installed)
        }
    }

    fun mockConstructor(redirects: Array<Redirect>) {
        installConstructor(redirects)
    }

    inline fun mockConstructor(redirects: Array<Redirect>, block: () -> Unit) {
        val installed = installConstructor(redirects)
        try {
            block()
        } finally {
            uninstall(installed)
        }
    }

    fun installConstructor(redirects: Array<Redirect>): List<ActiveRedirect> =
        install(redirects, "mockConstructor", CONSTRUCTOR)

    fun constructed(key: String, name: String): Any = constructors(key, name).any

    fun constructedWith(key: String, name: String, matchers: Array<Matcher<Any?>>): Any =
        constructors(key, name).representative(matchers)

    fun clearConstructor(keys: Array<String>, names: Array<String>, answers: Boolean, recordedCalls: Boolean, childMocks: Boolean) {
        keys.forEachIndexed { index, key -> constructors(key, names[index]).clear(answers, recordedCalls, childMocks) }
    }

    fun install(redirects: Array<Redirect>): List<ActiveRedirect> = install(redirects, "mockObject", OBJECT)

    fun installStatic(redirects: Array<Redirect>): List<ActiveRedirect> = install(redirects, "mockStatic", STATIC)

    fun uninstall(installed: List<ActiveRedirect>) {
        installed.forEach(::remove)
    }

    fun of(target: Any, name: String): GeneratedMock = of(target, name, "mockObject($name)")

    fun ofStatic(target: Any, name: String): GeneratedMock = of(target, name, "mockStatic($name)")

    fun stubbed(redirect: Redirect): GeneratedMock = stubbed(redirect, "mockObject(${redirect.name})", OBJECT)

    fun stubbedStatic(redirect: Redirect): GeneratedMock = stubbed(redirect, "mockStatic(${redirect.name})", STATIC)

    fun unmock(targets: Array<out Any>) {
        val scope = LokkiScope.currentScope ?: return
        for (target in targets) {
            synchronized(this) { active.find { it.isFor(target) && it.scope === scope } }?.let(::remove)
        }
    }

    fun clearObjects(mocks: Array<Any>, answers: Boolean, recordedCalls: Boolean, childMocks: Boolean) {
        clearMocks(
            mocks.first(),
            *mocks.copyOfRange(1, mocks.size),
            answers = answers,
            recordedCalls = recordedCalls,
            childMocks = childMocks,
        )
    }

    fun clearStatic(mocks: Array<Any>) {
        clearMocks(mocks.first(), *mocks.copyOfRange(1, mocks.size))
    }

    fun confirmVerifiedStatic(mocks: Array<Any>, clear: Boolean) {
        confirmVerified(*mocks, clear = clear)
    }

    internal fun find(target: Any): GeneratedMock? = synchronized(this) { active.find { it.isFor(target) }?.mock }

    internal fun isObject(target: Any): Boolean =
        synchronized(this) { active.any { it.target === target && it.mock.`lokki$state`().isObject } }

    internal fun isStatic(type: Any): Boolean =
        synchronized(this) {
            active.any { active ->
                active.mock.`lokki$state`().isStatic &&
                    (active.mock.redirectsFile(type) || active.target !is String && active.target.isStaticOf(type))
            }
        }

    internal fun isConstructor(type: Any): Boolean =
        synchronized(this) { active.any { it.constructors != null && it.mock.mocksClass(type) } }

    internal fun unmockAll() {
        LokkiScope.currentScope?.let(::release)
    }

    internal fun release(scope: LokkiScope) {
        synchronized(this) { active.filter { it.scope === scope } }.forEach(::remove)
    }

    private fun install(redirects: Array<Redirect>, function: String, kind: Int): List<ActiveRedirect> {
        val scope = LokkiScope.requireCurrent(function)
        return redirects.mapNotNull { install(it, scope, kind) }
    }

    private fun constructors(key: String, name: String): ConstructorMocks {
        val redirect = synchronized(this) { active.find { it.isFor(key) } }
        val current = LokkiScope.currentScope
        val mocks = redirect?.constructors?.takeIf { current == null || current.isWithin(redirect.scope) }
        return mocks ?: notMocked(name, "mockConstructor($name::class)")
    }

    private fun of(target: Any, name: String, call: String): GeneratedMock {
        val redirect = synchronized(this) { active.find { it.isFor(target) } }
        val current = LokkiScope.currentScope
        if (redirect == null || current != null && !current.isWithin(redirect.scope)) {
            notMocked(if (target is Enum<*>) "$name.${target.name}" else name, call)
        }
        return redirect.mock
    }

    private fun notMocked(name: String, call: String): Nothing = throw LokkiException(
        if (LokkiScope.currentScope == null) {
            "$name is not mocked and there is no active LokkiScope: use LokkiExtension or lokkiScope { }"
        } else {
            "$name is not mocked in the current scope; call $call"
        }
    )

    private fun stubbed(redirect: Redirect, call: String, kind: Int): GeneratedMock {
        val scope = LokkiScope.currentScope ?: return of(redirect.target, redirect.name, call)
        val visible = synchronized(this) { active.find { it.isFor(redirect.target) && scope.isWithin(it.scope) } }
        return visible?.mock ?: install(redirect, scope, kind)?.mock ?: of(redirect.target, redirect.name, call)
    }

    private fun install(redirect: Redirect, scope: LokkiScope, kind: Int): ActiveRedirect? =
        synchronized(this) {
            val existing = active.find { it.isFor(redirect.target) }
            when {
                existing == null -> {}
                existing.scope === scope -> {
                    active -= existing
                    existing.constructors?.detach()
                }
                scope.isWithin(existing.scope) -> return@synchronized null
                else -> throw LokkiException(
                    "${redirect.name} is already mocked in ${existing.scope}; " +
                        "serialize these tests with @ResourceLock or @Isolated"
                )
            }
            if (kind == CONSTRUCTOR) {
                val mocks = ConstructorMocks(redirect.name, redirect.factory, scope, redirect.flags)
                redirect.install(mocks)
                return@synchronized ActiveRedirect(redirect.target, scope, mocks.any, redirect.install, mocks)
                    .also { active += it }
            }
            val mock = redirect.factory(MockSpec(null, SPY or kind or redirect.flags)) as GeneratedMock
            redirect.install(mock)
            ActiveRedirect(redirect.target, scope, mock, redirect.install).also { active += it }
        }

    private fun remove(redirect: ActiveRedirect) {
        synchronized(this) {
            if (active.remove(redirect)) {
                redirect.install(null)
                redirect.constructors?.detach()
            }
        }
    }
}

@PublishedApi
internal class ActiveRedirect internal constructor(
    internal val target: Any,
    internal val scope: LokkiScope,
    internal val mock: GeneratedMock,
    internal val install: (Any?) -> Unit,
    internal val constructors: ConstructorMocks? = null,
) {
    internal fun isFor(other: Any): Boolean = target === other || target is String && target == other
}
