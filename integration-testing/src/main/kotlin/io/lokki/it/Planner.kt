package io.lokki.it

class Tenant(val name: String)

interface Scheduler {
    fun schedule(times: IntArray, task: (Int) -> Unit): Int

    context(tenant: Tenant)
    fun owner(id: Int): String
}

class Planner(private val scheduler: Scheduler) {
    fun plan(times: IntArray, task: (Int) -> Unit): Int = scheduler.schedule(times, task)

    context(tenant: Tenant)
    fun ownerOf(id: Int): String = scheduler.owner(id)
}
