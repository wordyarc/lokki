package lokki.buildlogic

import org.gradle.api.file.FileCollection
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.process.CommandLineArgumentProvider

// The task input is the content of the files, not their paths.
class ClasspathSystemProperty(
    @get:Input val name: String,
    @get:Classpath val files: FileCollection,
) : CommandLineArgumentProvider {
    override fun asArguments(): Iterable<String> = listOf("-D$name=${files.asPath}")
}

// The location is not a task input: the task gives the same result in any checkout.
class LocationSystemProperty(
    @get:Input val name: String,
    @get:Internal val location: Provider<String>,
) : CommandLineArgumentProvider {
    override fun asArguments(): Iterable<String> = listOf("-D$name=${location.get()}")
}
