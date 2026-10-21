package io.lokki.it.conformance

import io.lokki.MockAnnotations
import io.lokki.RelaxedMock
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CollectionsReturnOnRelaxedMockTest {
    open class ReturningCollections {
        open fun getList(): List<Any> = listOf()

        open fun getMap(): Map<Any, Any> = mapOf()

        open fun getSet(): Set<Any> = setOf()

        open fun getArrayList(): ArrayList<Any> = arrayListOf()

        open fun getHashMap(): HashMap<Any, Any> = hashMapOf()

        open fun getHashSet(): HashSet<Any> = hashSetOf()
    }

    @RelaxedMock
    private lateinit var returningCollections: ReturningCollections

    @BeforeTest
    fun setup() {
        MockAnnotations.init(this, relaxUnitFun = true)
    }

    @Test
    fun getList() {
        val list = returningCollections.getList()
        assertEquals(0, list.size)
        assertTrue(list.isEmpty())
    }

    @Test
    fun getMap() {
        val map = returningCollections.getMap()
        assertEquals(0, map.size)
        assertTrue(map.isEmpty())
    }

    @Test
    fun getSet() {
        val set = returningCollections.getSet()
        assertEquals(0, set.size)
        assertTrue(set.isEmpty())
    }

    @Test
    fun getArrayList() {
        val arraylist = returningCollections.getArrayList()
        assertEquals(0, arraylist.size)
        assertTrue(arraylist.isEmpty())
    }

    @Test
    fun getHashMap() {
        val hashmap = returningCollections.getHashMap()
        assertEquals(0, hashmap.size)
        assertTrue(hashmap.isEmpty())
    }

    @Test
    fun getHashSet() {
        val hashset = returningCollections.getHashSet()
        assertEquals(0, hashset.size)
        assertTrue(hashset.isEmpty())
    }
}
