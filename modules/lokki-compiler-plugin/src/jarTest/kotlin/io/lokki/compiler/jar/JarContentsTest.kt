package io.lokki.compiler.jar

import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

internal class JarContentsTest {
    private val entries: List<Pair<String, ByteArray>> = compilerJar.entries()

    private val names: List<String> = entries.map { (name, _) -> name }

    @Test
    fun `stores every entry once`() {
        assertEquals(emptyList(), names.groupingBy { it }.eachCount().filterValues { it > 1 }.keys.toList())
    }

    @Test
    fun `declares the factory of every adapter in one descriptor`() {
        val expected = adapters.map { it.factory }.sorted()

        assertEquals(expected.joinToString(separator = "") { "$it\n" }, text(FACTORY_SERVICE))
        for (adapter in adapters) {
            assertContains(names, "${adapter.factory.internalName}.class")
            assertContains(names, "${adapter.implementation.internalName}.class")
        }
    }

    @Test
    fun `declares one registrar and one command line processor`() {
        val plugin = "META-INF/services/org.jetbrains.kotlin.compiler.plugin"

        assertEquals("io.lokki.compiler.LokkiCompilerPluginRegistrar\n", text("$plugin.CompilerPluginRegistrar"))
        assertEquals("io.lokki.compiler.LokkiCommandLineProcessor\n", text("$plugin.CommandLineProcessor"))
        assertEquals(3, names.count { it.startsWith("META-INF/services/") }, names.toString())
    }

    @Test
    fun `contains the classes and the module metadata of lokki only`() {
        val foreign = names.filterNot { name ->
            name == "META-INF/MANIFEST.MF" || name.startsWith("META-INF/services/") ||
                name.startsWith("META-INF/io.lokki") && name.endsWith(".kotlin_module") ||
                name.startsWith("io/lokki/compiler/") && name.endsWith(".class")
        }

        assertEquals(emptyList(), foreign)
    }

    @Test
    fun `publishes no dependencies`() {
        val pom = classpath("lokki.compiler.pom").single().readText()
        val module = classpath("lokki.compiler.module").single().readText()

        assertFalse("<dependency>" in pom, pom)
        assertFalse("\"dependencies\"" in module, module)
    }

    private fun text(name: String): String = entries.single { it.first == name }.second.decodeToString()
}
