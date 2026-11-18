# lokki

lokki is a mocking library for Kotlin/JVM with the [MockK](https://mockk.io) DSL, implemented as a K2 compiler
plugin. `mock<T>()` compiles into an ordinary class that implements `T`, and the blocks of `every { }` and
`verify { }` compile into calls of the runtime instead of being executed.

- **No instrumentation.** No Java agent, proxies or runtime class generation.
- **Errors at compile time.** Unmockable types, calls that cannot be intercepted and stubs without an answer fail the
  build.
- **Scoped state.** `mockObject`, `mockStatic` and stubs added to shared mocks belong to a scope — a JUnit 5 test or
  `lokkiScope { }` — and are undone when it closes.

**Status:** alpha. Until 1.0, the API may change without a deprecation cycle.

## Requirements

- Kotlin 2.4.0 – 2.5.0-RC; `mockObject` and `mockStatic` need Kotlin 2.4.20 or newer.
- Gradle 9.0 or newer.
- JVM target 17 or newer for test code.
- JUnit Jupiter 5.12 or newer, including 6.x, for `lokki-junit5`.

## Installation

```kotlin
plugins {
    kotlin("jvm") version "2.4.20"
    id("io.lokki") version "0.1.0"
}

dependencies {
    testImplementation("io.lokki:lokki-junit5") // optional: JUnit 5 extension
}
```

The plugin applies to test compilations — `test`, `testFixtures`, JVM test suites and Android test variants — and
adds `io.lokki:lokki-core` to them. `io.lokki` dependencies without a version get the version of the plugin.

| `lokki { }` option | Effect |
|---|---|
| `mockable(vararg patterns)` | Makes final classes, `object`s and top-level functions of main code mockable, see [below](#final-classes-objects-and-top-level-functions). |
| `applyToMain = true` | Applies the plugin to main code too, for modules with test utilities that create mocks. |
| `recordStackTraces = true` | Adds call sites of mock calls to failure reports. |

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
// e: 'com.example.Job' cannot be mocked: it is a final class. Extract an interface, make the class and its members open, or add it to lokki { mockable(...) }.

every { queue.complete(any()) }
// e: 'every' registers nothing without an answer: add 'returns', 'answers', 'throws' or 'just Runs' after the block.
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
`@RelaxedMock`, `@Spy` and `@InjectMocks` properties. On a test class, `@ConfirmVerification` and
`@CheckUnnecessaryStub` run `confirmVerified()` and `checkUnnecessaryStub()` after each test, and `@KeepMocks` keeps
the stubs and recorded calls of a test for the rest of the class.

Without the extension, initialize properties with `MockAnnotations.init(this)`. `mockObject`, `mockStatic` and
`confirmVerified()`, `checkUnnecessaryStub()` and `clearAllMocks()` without arguments need a scope, so wrap such
tests in `lokkiScope { }`.

### Final classes, objects and top-level functions

Without an agent, only calls of open members can be intercepted. Declarations of the module's main code that match a
`mockable` pattern are compiled differently: classes and their members become open, and `object`s and top-level
functions get interception points. A pattern is a package with its subpackages (`com.example.**`), a class, an
annotation or a function.

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
    mockObject(Clock)
    every { Clock.hour() } returns 9
    // Clock is restored when the test's scope closes
}
```

`mockStatic(::function)` does the same for top-level functions and properties, including extensions.

This changes the main bytecode: matching classes are open, and every call of an intercepted function reads a volatile
field. The plugin warns if the project applies `maven-publish`.

## Coming from MockK

Functions, matchers and their parameters keep MockK's names, so the [MockK documentation](https://mockk.io) applies.
Imports move to `io.lokki`, and names that contain `mockk` or `MockK` change:

| MockK | lokki |
|---|---|
| `mockk`, `spyk` | `mock`, `spy` |
| `mockkObject`, `mockkStatic`, `unmockkAll` | `mockObject`, `mockStatic`, `unmockAll` |
| `@MockK`, `@RelaxedMockK`, `@SpyK`, `@InjectMockKs` | `@Mock`, `@RelaxedMock`, `@Spy`, `@InjectMocks` |
| `MockKExtension`, `MockKAnnotations`, `MockKException` | `LokkiExtension`, `MockAnnotations`, `LokkiException` |

Behaviour that differs:

- The block of `every { }` and `verify { }` is not executed: its receiver and arguments are evaluated once, as
  ordinary code, and `any()` never creates a placeholder value.
- Child mocks need `@file:ChainedMocks`. Without it, the receiver of a stubbed call such as
  `every { car.door().open() }` is evaluated as ordinary code, and relaxed mocks return no child mocks.
- A relaxed mock returns `null` for nullable types and empty values for primitives, strings, collections and arrays.
  A call that returns any other type throws `LokkiException`.
- A mock call without an answer fails the test even if the code under test catches the exception.
- Spies are not copies: `spy(Foo(args))` constructs the spy itself, and `spy(existing)` forwards calls to `existing`.
- Calls of `equals`, `hashCode` and `toString` are not recorded.

## Limitations

- JVM only. Android instrumented tests compile with the plugin but have not been run on ART.
- Mocks are created and stubbed only in Kotlin code compiled with the plugin; Java code can only call them.
- The mocked type must be known at compile time: there is no `mockkClass(KClass)`, and `mock<T>()` inside your own
  `inline reified` helper is an error.
- Final classes, objects and functions from libraries, Java static methods and constructors (`mockkConstructor`)
  cannot be mocked.
- No JUnit 4 integration: use `lokkiScope { }`.
- IntelliJ IDEA shows `LOKKI_*` diagnostics only with the registry key
  `kotlin.k2.only.bundled.compiler.plugins.enabled` set to `false`; the Gradle build always reports them.

## Development

The build uses a JDK 25 toolchain, which Gradle downloads if it is missing.

```shell
./gradlew build                                   # all tests and checks of the main build, including the ABI check
./gradlew -p integration-testing check            # end-to-end tests and the MockK conformance suite
./gradlew :compiler-tests:test --tests '*Name*'   # selected compiler plugin tests
./gradlew updateKotlinAbi                         # update the ABI dumps after a public API change
```

`-Plokki.kotlin=<version>` runs the tests on another Kotlin version from `lokki.kotlin.tested` in
`gradle.properties`. The Gradle plugin tests in `build` need an Android SDK (`ANDROID_HOME`).

Compiler tests are data files in [`modules/compiler-tests/src/test/data`](modules/compiler-tests/src/test/data):
`box` for codegen, `diagnostics` and `dump/ir`. The [Kotlin Compiler DevKit][devkit] IntelliJ plugin runs them from
the files.

## License

[Apache License 2.0](LICENSE.txt)

[devkit]: https://github.com/JetBrains/kotlin-compiler-devkit
