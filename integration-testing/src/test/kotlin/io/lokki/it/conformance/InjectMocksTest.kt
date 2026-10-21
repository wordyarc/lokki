package io.lokki.it.conformance

import io.lokki.InjectMocks
import io.lokki.Mock
import io.lokki.MockAnnotations
import io.lokki.every
import io.lokki.verify
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class InjectMocksTest {
    interface IFoo

    open class Foo : IFoo {
        open fun method() {
        }
    }

    abstract class AbstractBar<T : IFoo> {
        lateinit var foo: T
    }

    class Bar : AbstractBar<Foo>() {
        fun call() {
            foo.method()
        }
    }

    @Mock
    lateinit var foo: Foo

    @InjectMocks
    lateinit var bar: Bar

    @BeforeTest
    fun setUp() = MockAnnotations.init(this)

    @Test
    fun test() {
        every { foo.method() } answers { nothing }

        bar.call()

        verify { foo.method() }
    }
}

class InjectMocksListTest {
    interface IFoo {
        fun method()
    }

    open class Foo1 : IFoo {
        override fun method() {}
    }

    open class Foo2 : IFoo {
        override fun method() {}
    }

    class Bar(
        val foos: List<IFoo>,
    )

    @Mock
    lateinit var foo1: Foo1

    @Mock
    lateinit var foo2: Foo2

    @InjectMocks
    lateinit var bar: Bar

    @BeforeTest
    fun setUp() = MockAnnotations.init(this)

    @Test
    fun testListInjection() {
        assertEquals(2, bar.foos.size)
        assertTrue(bar.foos.contains(foo1))
        assertTrue(bar.foos.contains(foo2))
    }
}

class InjectMocksDependencyOrderTest {
    interface Repository {
        fun getData(): String
    }

    class ServiceB(
        val repository: Repository,
    )

    class ServiceA(
        val b: ServiceB,
    )

    class LevelC(
        val repository: Repository,
    )

    class LevelB(
        val c: LevelC,
    )

    class LevelA(
        val b: LevelB,
    )

    class DiamondA(
        val repository: Repository,
    )

    class DiamondB(
        val a: DiamondA,
    )

    class DiamondC(
        val a: DiamondA,
    )

    class DiamondD(
        val b: DiamondB,
        val c: DiamondC,
    )

    interface InterfaceDependency

    class InterfaceDependencyImpl : InterfaceDependency

    class InterfaceConsumer(
        val dependency: InterfaceDependency,
    )

    class ListInterfaceConsumer(
        val dependencies: List<InterfaceDependency>,
    )

    class TwoLevelTestTarget {
        @Mock
        lateinit var repository: Repository

        @InjectMocks
        lateinit var a: ServiceA

        @InjectMocks
        lateinit var b: ServiceB
    }

    @Test
    fun twoLevelDependency() {
        val obj = TwoLevelTestTarget()
        MockAnnotations.init(obj)

        assertNotNull(obj.b, "ServiceB should be created")
        assertNotNull(obj.a, "ServiceA should be created")
        assertSame(obj.b, obj.a.b, "ServiceA.b should be the injected ServiceB")
    }

    class ThreeLevelTestTarget {
        @Mock
        lateinit var repository: Repository

        @InjectMocks
        lateinit var a: LevelA

        @InjectMocks
        lateinit var b: LevelB

        @InjectMocks
        lateinit var c: LevelC
    }

    @Test
    fun threeLevelChainDependency() {
        val obj = ThreeLevelTestTarget()
        MockAnnotations.init(obj)

        assertNotNull(obj.c, "LevelC should be created")
        assertNotNull(obj.b, "LevelB should be created")
        assertNotNull(obj.a, "LevelA should be created")
        assertSame(obj.c, obj.b.c, "LevelB.c should be the injected LevelC")
        assertSame(obj.b, obj.a.b, "LevelA.b should be the injected LevelB")
    }

    class DiamondTestTarget {
        @Mock
        lateinit var repository: Repository

        @InjectMocks
        lateinit var alpha: DiamondD

        @InjectMocks
        lateinit var beta: DiamondB

        @InjectMocks
        lateinit var gamma: DiamondC

        @InjectMocks
        lateinit var zeta: DiamondA
    }

    @Test
    fun diamondDependency() {
        val obj = DiamondTestTarget()
        MockAnnotations.init(obj)

        assertNotNull(obj.zeta, "DiamondA (zeta) should be created")
        assertNotNull(obj.beta, "DiamondB (beta) should be created")
        assertNotNull(obj.gamma, "DiamondC (gamma) should be created")
        assertNotNull(obj.alpha, "DiamondD (alpha) should be created")

        assertSame(obj.zeta, obj.beta.a, "DiamondB.a should be zeta")
        assertSame(obj.zeta, obj.gamma.a, "DiamondC.a should be zeta")
        assertSame(obj.beta, obj.alpha.b, "DiamondD.b should be beta")
        assertSame(obj.gamma, obj.alpha.c, "DiamondD.c should be gamma")
    }

    class InterfaceDependencyTestTarget {
        @InjectMocks
        lateinit var consumer: InterfaceConsumer

        @InjectMocks
        lateinit var implementation: InterfaceDependencyImpl
    }

    @Test
    fun interfaceDependency() {
        val obj = InterfaceDependencyTestTarget()
        MockAnnotations.init(obj)

        assertNotNull(obj.implementation, "InterfaceDependencyImpl should be created")
        assertNotNull(obj.consumer, "InterfaceConsumer should be created")
        assertSame(obj.implementation, obj.consumer.dependency, "InterfaceConsumer should receive the implementation")
    }

    class ListDependencyTestTarget {
        @InjectMocks
        lateinit var consumer: ListInterfaceConsumer

        @InjectMocks
        lateinit var implementation1: InterfaceDependencyImpl

        @InjectMocks
        lateinit var implementation2: InterfaceDependencyImpl
    }

    @Test
    fun listDependency() {
        val obj = ListDependencyTestTarget()
        MockAnnotations.init(obj)

        assertNotNull(obj.implementation1, "The first InterfaceDependencyImpl should be created")
        assertNotNull(obj.implementation2, "The second InterfaceDependencyImpl should be created")
        assertNotNull(obj.consumer, "ListInterfaceConsumer should be created")
        assertEquals(2, obj.consumer.dependencies.size, "ListInterfaceConsumer should receive two implementations")
        assertTrue(
            obj.consumer.dependencies.contains(obj.implementation1),
            "ListInterfaceConsumer should receive the first implementation",
        )
        assertTrue(
            obj.consumer.dependencies.contains(obj.implementation2),
            "ListInterfaceConsumer should receive the second implementation",
        )
    }
}
