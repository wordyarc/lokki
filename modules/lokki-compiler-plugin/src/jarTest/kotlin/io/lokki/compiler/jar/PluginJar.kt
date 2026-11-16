package io.lokki.compiler.jar

import java.io.File
import java.nio.file.Path
import java.util.zip.ZipInputStream
import kotlin.io.path.inputStream
import org.jetbrains.kotlin.tooling.core.KotlinToolingVersion

internal const val FACTORY_SERVICE: String = "META-INF/services/io.lokki.compiler.compat.CompilerCompat\$Factory"

internal val testedKotlin: List<String> = property("lokki.kotlin.tested").split(',')

internal val latestTestedKotlin: String = testedKotlin.maxBy(::KotlinToolingVersion)

internal val compilerJar: Path = classpath("lokki.compiler.jar").single()

internal val adapters: List<Adapter> = property("lokki.compiler.adapters").split(',')
    .map { Adapter(it.substringBefore('='), it.substringAfter('=')) }

internal val checkedKotlin: List<String> =
    (testedKotlin + adapters.map(Adapter::minVersion)).distinct().sortedBy { KotlinToolingVersion(it) }

internal val pluginShapes: Map<String, ClassShape> = compilerJar.entries()
    .filter { (name, _) -> name.endsWith(".class") }
    .associate { (name, bytes) -> name.removeSuffix(".class") to ClassShape.read(bytes, withReferences = true) }

internal class Adapter(val minVersion: String, val implementation: String) {
    val packageName: String
        get() = implementation.substringBeforeLast('.')

    val factory: String
        get() = "$packageName.CompilerCompatFactory"

    override fun toString(): String = "$minVersion=$implementation"
}

internal fun adapterOf(kotlin: String): Adapter =
    adapters.last { KotlinToolingVersion(it.minVersion) <= KotlinToolingVersion(kotlin) }

internal fun pluginWith(adapter: Adapter): Set<String> {
    val factories = adapters.map { it.factory.internalName }
    val unused = adapters.filter { it !== adapter }.map { "${it.packageName.internalName}/" }
    return pluginShapes.keys.filterTo(mutableSetOf()) { name -> name in factories || unused.none(name::startsWith) }
}

internal fun violations(adapter: Adapter, host: List<Path>): List<String> =
    CompilerAbi(pluginShapes, host).use { it.violations(pluginWith(adapter)) }

internal fun linkageFailures(adapter: Adapter, host: List<Path>): List<String> =
    linkageFailures(pluginWith(adapter), listOf(compilerJar) + host)

internal fun property(name: String): String = requireNotNull(System.getProperty(name)) { "missing -D$name" }

internal fun classpath(name: String): List<Path> =
    property(name).split(File.pathSeparator).filter(String::isNotEmpty).map(Path::of)

internal fun Path.entries(): List<Pair<String, ByteArray>> = ZipInputStream(inputStream()).use { zip ->
    generateSequence(zip::getNextEntry).filterNot { it.isDirectory }.map { it.name to zip.readBytes() }.toList()
}

internal val String.internalName: String
    get() = replace('.', '/')
