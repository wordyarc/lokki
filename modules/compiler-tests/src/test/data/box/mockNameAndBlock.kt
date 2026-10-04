package sample

import io.lokki.every
import io.lokki.mock

interface Task {
    fun doIt(): Int
    fun subTask(): Task
}

interface Goal {
    fun rootTask(): Task
}

fun stubbedOnce(): Task {
    return mock<Task> {
        every { doIt() } returns 1
        if (doIt() == 1) return this
        every { doIt() } returns 2
    }
}

fun box(): String {
    if (!Regex("""Task\(#\d+\)""").matches(mock<Task>().toString())) return "Fail: mock without a name"
    if (!Regex("""Task\(first#\d+\)""").matches(mock<Task>("first").toString())) return "Fail: name"

    val log = StringBuilder()
    val named = mock<Task>(name = log.append("name;").let { "second" }) {
        log.append("block;")
        every { doIt() } returns 3
    }
    if (log.toString() != "name;block;") return "Fail: evaluation order $log"
    if (!named.toString().startsWith("Task(second#") || named.doIt() != 3) return "Fail: name and block"

    val goal = mock<Goal> {
        every { rootTask() } returns mock {
            every { doIt() } returns 5
        } andThen mock<Task>("last") {
            every { doIt() } returns 6 andThen 7
            every { subTask() } returns mock {
                every { doIt() } returns 8
            }
        }
    }
    if (goal.rootTask().doIt() != 5) return "Fail: block of an inferred mock"
    val last = goal.rootTask()
    if (listOf(last.doIt(), last.doIt(), last.subTask().doIt()) != listOf(6, 7, 8)) return "Fail: nested blocks"

    val setup: Task.() -> Unit = { every { doIt() } returns 9 }
    if (mock<Task>(block = setup).doIt() != 9) return "Fail: block that is not a lambda literal"

    if (stubbedOnce().doIt() != 1) return "Fail: return from the block"

    return "OK"
}
