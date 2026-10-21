package io.lokki.internal

import io.lokki.LokkiException
import io.lokki.LokkiScope

@PublishedApi
internal class ObjectRedirect(
    internal val target: Any,
    internal val name: String,
    internal val factory: (MockSpec) -> Any,
    internal val install: (Any?) -> Unit,
)

@PublishedApi
internal object Redirects {
    private val active = ArrayList<ActiveRedirect>()

    fun mockObject(redirects: Array<ObjectRedirect>) {
        install(redirects)
    }

    inline fun mockObject(redirects: Array<ObjectRedirect>, block: () -> Unit) {
        val installed = install(redirects)
        try {
            block()
        } finally {
            uninstall(installed)
        }
    }

    fun install(redirects: Array<ObjectRedirect>): Any {
        val scope = LokkiScope.requireCurrent("mockObject")
        return redirects.mapNotNull { install(it, scope) }
    }

    fun uninstall(installed: Any) {
        @Suppress("UNCHECKED_CAST")
        for (redirect in installed as List<ActiveRedirect>) remove(redirect)
    }

    fun of(target: Any, name: String): GeneratedMock {
        val redirect = synchronized(this) { active.find { it.target === target } }
        val current = LokkiScope.currentScope
        if (redirect == null || current != null && !current.isWithin(redirect.scope)) {
            throw LokkiException("$name is not mocked in the current scope; call mockObject($name)")
        }
        return redirect.mock
    }

    internal fun find(target: Any): GeneratedMock? = synchronized(this) { active.find { it.target === target }?.mock }

    internal fun unmock(targets: Array<out Any>) {
        val scope = LokkiScope.currentScope ?: return
        for (target in targets) {
            synchronized(this) { active.find { it.target === target && it.scope === scope } }?.let(::remove)
        }
    }

    internal fun unmockAll() {
        LokkiScope.currentScope?.let(::release)
    }

    internal fun release(scope: LokkiScope) {
        synchronized(this) { active.filter { it.scope === scope } }.forEach(::remove)
    }

    private fun install(redirect: ObjectRedirect, scope: LokkiScope): ActiveRedirect? =
        synchronized(this) {
            val existing = active.find { it.target === redirect.target }
            when {
                existing == null -> {}
                existing.scope === scope -> active -= existing
                scope.isWithin(existing.scope) -> return@synchronized null
                else -> throw LokkiException(
                    "${redirect.name} is already mocked in ${existing.scope}; " +
                        "serialize these tests with @ResourceLock or @Isolated"
                )
            }
            val mock = redirect.factory(MockSpec(null, SPY or OBJECT)) as GeneratedMock
            redirect.install(mock)
            ActiveRedirect(redirect.target, scope, mock, redirect.install).also { active += it }
        }

    private fun remove(redirect: ActiveRedirect) {
        synchronized(this) {
            if (active.remove(redirect)) redirect.install(null)
        }
    }
}

internal class ActiveRedirect(
    val target: Any,
    val scope: LokkiScope,
    val mock: GeneratedMock,
    val install: (Any?) -> Unit,
)
