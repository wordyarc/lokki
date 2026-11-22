<h1>
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="docs/logo/lokki-wordmark-dark.svg">
    <img src="docs/logo/lokki-wordmark.svg" alt="lokki" height="72">
  </picture>
</h1>

[![Build](https://github.com/wordyarc/lokki/actions/workflows/build.yml/badge.svg?branch=master)](https://github.com/wordyarc/lokki/actions/workflows/build.yml)
[![Gradle Plugin Portal](https://img.shields.io/gradle-plugin-portal/v/io.github.wordyarc.lokki?label=Gradle%20Plugin%20Portal)](https://plugins.gradle.org/plugin/io.github.wordyarc.lokki)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.wordyarc.lokki/lokki-core?label=Maven%20Central)](https://central.sonatype.com/artifact/io.github.wordyarc.lokki/lokki-core)
[![License](https://img.shields.io/badge/license-Apache_2.0-blue)](LICENSE.txt)

lokki is a mocking library for Kotlin/JVM with the [MockK](https://mockk.io) DSL, implemented as a K2 compiler
plugin. `mock<T>()` compiles into an ordinary class that implements `T`, and the blocks of `every { }` and
`verify { }` compile into calls of the runtime instead of being executed.

- **No instrumentation.** No Java agent, proxies or runtime class generation.
- **Errors at compile time.** Unmockable types, calls that cannot be intercepted and stubs without an answer fail the
  build.
- **Scoped state.** `mockObject`, `mockStatic` and stubs added to shared mocks last until their scope — a JUnit 5
  test or `lokkiScope { }` — closes.

**Status:** alpha. Until 1.0, the API may change without a deprecation cycle.

## Requirements

- Kotlin 2.4.0 – 2.5.0-RC: an older Kotlin fails the build, a newer one is untested and gives a warning.
  `mockObject` and `mockStatic` need Kotlin 2.4.20 or newer.
- Gradle 9.0 or newer.
- JVM target 17 or newer for test code.
- JUnit Jupiter 5.12 or newer, including 6.x, for `LokkiExtension`; lokki raises an older JUnit 5 to 5.12.2.

## Installation

```kotlin
plugins {
    kotlin("jvm") version "2.4.20"
    id("io.github.wordyarc.lokki") version "0.1.2"
}
```

The plugin applies to test compilations — `test`, `testFixtures`, JVM test suites, `jvmTest` of multiplatform
projects and Android test variants — and adds the runtime, `io.github.wordyarc.lokki:lokki-core`, to them. The
runtime includes the JUnit 5 extension.

## Usage

```kotlin
class Job(val id: String)

interface JobQueue {
    fun poll(): Job?
    fun complete(job: Job)
}

class JobProcessor(private val queue: JobQueue) {
    fun runNext(): Boolean {
        val job = queue.poll() ?: return false
        queue.complete(job)
        return true
    }
}
```

```kotlin
import io.lokki.Runs
import io.lokki.every
import io.lokki.just
import io.lokki.mock
import io.lokki.verify
import kotlin.test.Test
import kotlin.test.assertTrue

class JobProcessorTest {
    @Test
    fun completesPolledJob() {
        val queue = mock<JobQueue>()
        val job = Job("42")
        every { queue.poll() } returns job
        every { queue.complete(any()) } just Runs

        assertTrue(JobProcessor(queue).runNext())
        verify(exactly = 1) { queue.complete(job) }
    }
}
```

`mock<T>()` accepts interfaces, open and abstract classes and function types, including generic types and `suspend`
members. Matchers, answers, `coEvery`/`coVerify`, ordered verification, relaxed mocks and spies work as in MockK,
with the differences listed in [Coming from MockK](#coming-from-mockk).

Misuse fails compilation:

```kotlin
mock<Job>()
// e: 'com.example.Job' cannot be mocked: it is a final class. Extract an interface,
//    make the class and its members open, or add it to lokki { mockable(...) }.

every { queue.complete(any()) }
// e: 'every' registers nothing without an answer: add 'returns', 'answers', 'throws'
//    or 'just Runs' after the block.
```

### JUnit 5

```kotlin
@ExtendWith(LokkiExtension::class) // io.lokki.junit5
class JobProcessorTest {
    @Mock
    lateinit var queue: JobQueue

    @Test
    fun skipsEmptyQueue() {
        every { queue.poll() } returns null

        assertFalse(JobProcessor(queue).runNext())
        verify { queue.poll() }
        confirmVerified()
    }
}
```

`LokkiExtension` runs every test class, test and dynamic test in its own scope and initializes `@Mock`,
`@RelaxedMock`, `@Spy` and `@InjectMocks` properties. `@ConfirmVerification` and `@CheckUnnecessaryStub` on a test
class run `confirmVerified()` and `checkUnnecessaryStub()` after each test. `@KeepMocks` on a test class or method
keeps the stubs and recorded calls of a test for the rest of the class.

Without the extension, `MockAnnotations.init(this)` initializes the properties and `lokkiScope { }` provides the
scope. Outside a scope, `mockObject`, `mockStatic` and the calls without arguments `confirmVerified()`,
`checkUnnecessaryStub()` and `clearAllMocks()` throw `LokkiException`.

### Final classes, objects and top-level functions

Without an agent, only calls of open members can be intercepted. Declarations of the module's main code that match a
`mockable` pattern are compiled differently: classes and their members are open in the bytecode, and `object`s and
top-level functions get interception points. A pattern is a package with its subpackages (`com.example.**`), a class,
an annotation or a function.

```kotlin
// build.gradle.kts
lokki {
    mockable("com.example.**")
}
```

```kotlin
// src/main/kotlin
object Clock {
    fun hour(): Int = 12
}
```

```kotlin
// src/test/kotlin, in a test with LokkiExtension
@Test
fun morning() {
    every { Clock.hour() } returns 9
    // Clock is restored when the test's scope closes
}
```

`every` mocks the object in the current scope by itself; `mockObject(Clock)` records its calls for `verify` without
a stub. `mockStatic(::function)` does the same for top-level functions and properties, including extensions. An
overloaded or generic function cannot be passed to it: Kotlin does not resolve such a reference.

Only the bytecode changes: Kotlin still compiles matching classes as final, so smart casts and initialization rules of
main code stay as they are, and only mocks override the members. Inline and `tailrec` functions and `@JvmField`
properties stay final. Every call of an intercepted function reads a volatile field. The plugin warns if the project
applies `maven-publish`.

## Configuration

| `lokki { }` option | Effect |
|---|---|
| `mockable(vararg patterns)` | Makes final classes, `object`s and top-level functions of main code mockable, see [above](#final-classes-objects-and-top-level-functions). |
| `applyToMain = true` | Applies the plugin to main code too, for modules with test utilities that create mocks. |
| `recordStackTraces = true` | Adds call sites of mock calls to failure reports. |

## Coming from MockK

Functions, matchers and their parameters keep MockK's names, so the [MockK documentation](https://mockk.io) applies.
Imports move to `io.lokki`, and names that contain `mockk` or `MockK` change:

| MockK | lokki |
|---|---|
| `mockk`, `spyk` | `mock`, `spy` |
| `mockkObject`, `mockkStatic`, `unmockkAll` | `mockObject`, `mockStatic`, `unmockAll` |
| `@MockK`, `@RelaxedMockK`, `@SpyK`, `@InjectMockKs` | `@Mock`, `@RelaxedMock`, `@Spy`, `@InjectMocks` |
| `MockKExtension`, `MockKAnnotations`, `MockKException` | `LokkiExtension`, `MockAnnotations`, `LokkiException` |
| `MockKMatcherScope` | `MatcherScope` |
| `mockkStatic("com.example.FileKt")`, `mockkStatic(Foo::class)` | `mockStatic(::function)` for each function, or just `every { function() }` |

Behaviour that differs:

- The receiver and arguments of the call in `every { }` and `verify { }` are evaluated once, as ordinary code, and
  `any()` never creates a placeholder value.
- A call chain such as `every { car.door().open() }` stubs `car.door()` with a child mock only when the call has no
  answer yet; a stubbed `car.door()` keeps its answer. Relaxed mocks return child mocks only in files annotated with
  `@file:ChainedMocks`.
- A relaxed mock returns `null` for nullable types and for a type parameter of a Java method (`<T> T query(...)`),
  and empty values for primitives, strings, collections and arrays. A call that returns any other type throws
  `LokkiException`.
- A mock call without an answer fails the test even if the code under test catches the exception.
- Spies are not copies: `spy(Foo(args))` constructs the spy itself, and `spy(existing)` forwards calls to `existing`.
- Calls of `equals`, `hashCode` and `toString` are not recorded.
- `mock.javaClass` is a generated subclass of the mocked class, not the class itself.
- `coVerify` does not run coroutines that are still waiting to be dispatched: MockK runs them as a side effect of its
  `runBlocking`. Start them with `CoroutineStart.UNDISPATCHED` or let them complete before verifying.

## Limitations

- JVM only: Kotlin/JVM, Android and the JVM target of multiplatform projects. Android instrumented tests compile with
  the plugin but have not been run on ART.
- Mocks are created and stubbed only in Kotlin code compiled with the plugin; Java code can only call them.
- The mocked type and the stubbed call must be written where they are used: there is no `mockkClass(KClass)`, and
  `mock<T>()` inside your own `inline reified` helper or a block passed to `every` or `verify` through your own
  function is an error.
- Final classes, objects and functions from libraries, Java static methods and constructors (`mockkConstructor`)
  cannot be mocked.
- Private members cannot be stubbed or verified: there are no calls by name such as `every { spy["name"]() }`.
- No JUnit 4 integration: use `lokkiScope { }`. Kotest is not supported.
- IntelliJ IDEA shows `LOKKI_*` diagnostics only with the registry key
  `kotlin.k2.only.bundled.compiler.plugins.enabled` set to `false`; the Gradle build always reports them.

## Development

```shell
./gradlew build                                   # all tests and checks of the main build, including the ABI check
./gradlew -p integration-testing check            # end-to-end tests and the MockK conformance suite
./gradlew :compiler-tests:test --tests '*Name*'   # selected compiler plugin tests
./gradlew updateKotlinAbi                         # update the ABI dumps after a public API change
```

Gradle downloads the JDK 25 toolchain of the build if it is missing. The Gradle plugin tests in `build` need an
Android SDK (`ANDROID_HOME`). The tests of `lokki-maven` download Maven and keep its local repository in
`modules/lokki-maven/build`.

## License

[Apache License 2.0](LICENSE.txt)
