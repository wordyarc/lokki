package io.lokki.maven

import org.apache.maven.plugin.MojoExecution
import org.apache.maven.project.MavenProject
import org.codehaus.plexus.logging.Logger
import org.jetbrains.kotlin.maven.KotlinMavenPluginExtension
import org.jetbrains.kotlin.maven.PluginOption

class LokkiMavenPluginExtension : KotlinMavenPluginExtension {
    private lateinit var logger: Logger

    override fun isApplicable(project: MavenProject, execution: MojoExecution): Boolean = true

    override fun getCompilerPluginId(): String = BuildConfig.COMPILER_PLUGIN_ID

    override fun getPluginOptions(project: MavenProject, execution: MojoExecution): List<PluginOption> {
        val options = MAIN_MODE_OPTIONS.filter { execution.hasPatterns(it) }
        if (options.isEmpty()) return emptyList()
        when (execution.goal) {
            TEST_COMPILE_GOAL -> throw IllegalStateException(
                "${options.first()} changes the main code, but the execution '${execution.executionId}' of " +
                    "kotlin-maven-plugin passes it to the test compilation too. Set <pluginOptions> with " +
                    "${options.first()} in the configuration of the execution of the compile goal instead of the " +
                    "configuration of the plugin."
            )
            COMPILE_GOAL -> if (!project.isMockablePublicationWarningDisabled()) {
                val change = when (INTERCEPTABLE) {
                    in options -> "$INTERCEPTABLE adds interception points to objects, top-level functions and " +
                        "constructor calls and opens classes"
                    else -> "$MOCKABLE opens classes"
                }
                logger.warn(
                    "$change compiled by the execution '${execution.executionId}' of ${project.groupId}:" +
                        "${project.artifactId}, and the artifacts of this project contain this bytecode. To " +
                        "disable this warning, set the property $MOCKABLE_PUBLICATION_NOWARN=true."
                )
            }
        }
        return emptyList()
    }
}

private fun MojoExecution.hasPatterns(option: String): Boolean =
    configuration?.getChild("pluginOptions")?.children.orEmpty().any { it.value?.startsWith("$option=") == true }

private fun MavenProject.isMockablePublicationWarningDisabled(): Boolean =
    (System.getProperty(MOCKABLE_PUBLICATION_NOWARN) ?: properties.getProperty(MOCKABLE_PUBLICATION_NOWARN))
        ?.toBoolean() == true

private const val MOCKABLE = "lokki:${BuildConfig.MOCKABLE_OPTION}"
private const val INTERCEPTABLE = "lokki:${BuildConfig.INTERCEPTABLE_OPTION}"
private val MAIN_MODE_OPTIONS = listOf(MOCKABLE, INTERCEPTABLE)
private const val COMPILE_GOAL = "compile"
private const val TEST_COMPILE_GOAL = "test-compile"
private const val MOCKABLE_PUBLICATION_NOWARN = "lokki.mockablePublication.nowarn"
