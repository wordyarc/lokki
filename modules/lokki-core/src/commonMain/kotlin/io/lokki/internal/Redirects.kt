package io.lokki.internal

import io.lokki.LokkiException
import io.lokki.LokkiScope
import io.lokki.clearMocks
import io.lokki.confirmVerified

@PublishedApi
internal class Redirect(
    internal val target: Any,
    internal val name: String,
    internal val factory: (MockSpec) -> Any,
    internal val install: (Any?) -> Unit,
)

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

    fun install(redirects: Array<Redirect>): List<ActiveRedirect> = install(redirects, "mockObject", OBJECT)

    fun installStatic(redirects: Array<Redirect>): List<ActiveRedirect> = install(redirects, "mockStatic", STATIC)

    fun uninstall(installed: List<ActiveRedirect>) {
        installed.forEach(::remove)
    }

    fun of(target: Any, name: String): GeneratedMock = of(target, name, "mockObject($name)")

    fun ofStatic(target: Any, name: String): GeneratedMock = of(target, name, "mockStatic($name)")

    fun unmock(targets: Array<out Any>) {
        val scope = LokkiScope.currentScope ?: return
        for (target in targets) {
            synchronized(this) { active.find { it.isFor(target) && it.scope === scope } }?.let(::remove)
        }
    }

    fun clearStatic(mocks: Array<Any>) {
        clearMocks(mocks.first(), *mocks.copyOfRange(1, mocks.size))
    }

    fun confirmVerifiedStatic(mocks: Array<Any>, clear: Boolean) {
        confirmVerified(*mocks, clear = clear)
    }

    internal fun find(target: Any): GeneratedMock? = synchronized(this) { active.find { it.isFor(target) }?.mock }

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

    private fun of(target: Any, name: String, call: String): GeneratedMock {
        val redirect = synchronized(this) { active.find { it.isFor(target) } }
        val current = LokkiScope.currentScope
        if (redirect == null || current != null && !current.isWithin(redirect.scope)) {
            throw LokkiException("$name is not mocked in the current scope; call $call")
        }
        return redirect.mock
    }

    private fun install(redirect: Redirect, scope: LokkiScope, kind: Int): ActiveRedirect? =
        synchronized(this) {
            val existing = active.find { it.isFor(redirect.target) }
            when {
                existing == null -> {}
                existing.scope === scope -> active -= existing
                scope.isWithin(existing.scope) -> return@synchronized null
                else -> throw LokkiException(
                    "${redirect.name} is already mocked in ${existing.scope}; " +
                        "serialize these tests with @ResourceLock or @Isolated"
                )
            }
            val mock = redirect.factory(MockSpec(null, SPY or kind)) as GeneratedMock
            redirect.install(mock)
            ActiveRedirect(redirect.target, scope, mock, redirect.install).also { active += it }
        }

    private fun remove(redirect: ActiveRedirect) {
        synchronized(this) {
            if (active.remove(redirect)) redirect.install(null)
        }
    }
}

@PublishedApi
internal class ActiveRedirect internal constructor(
    internal val target: Any,
    internal val scope: LokkiScope,
    internal val mock: GeneratedMock,
    internal val install: (Any?) -> Unit,
) {
    internal fun isFor(other: Any): Boolean = target === other || target is String && target == other
}
