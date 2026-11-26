// WITH_KOTLINX_COROUTINES

// FILE: children.kt
package sample

import io.lokki.ChainedMocks
import io.lokki.mock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface Feed {
    fun updates(): Flow<String>
    fun state(): StateFlow<Int>
    fun pending(): Flow<String>?
}

fun plainFeed(): Feed = mock<Feed>(relaxed = true)

@ChainedMocks
fun chainedFeed(): Feed = mock<Feed>(relaxed = true)

// FILE: test.kt
package sample

import io.lokki.LokkiException
import io.lokki.isMock
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking

fun box(): String {
    val feed = plainFeed()
    if (runBlocking { feed.updates().toList() } != emptyList<String>()) return "Fail: the flow is not empty"
    if (feed.pending() != null) return "Fail nullable flow"
    val state = try {
        "returned ${feed.state()}"
    } catch (e: LokkiException) {
        e.message!!.replace(Regex("""\(#\d+\)"""), "")
    }
    val expected = "relaxed mock has no default for StateFlow<Int> returned by Feed.state(); stub the call or annotate " +
        "the declaration in children.kt that creates the mock with @ChainedMocks"
    if (state != expected) return "Fail StateFlow: $state"

    val chained = chainedFeed()
    if (isMock(chained.updates())) return "Fail: the empty flow gave way to a child mock"
    if (!isMock(chained.state())) return "Fail: StateFlow of a chained mock"
    return "OK"
}
