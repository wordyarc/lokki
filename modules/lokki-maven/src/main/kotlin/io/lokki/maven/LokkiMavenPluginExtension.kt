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
        if (execution.hasMockablePatterns()) {
            when (execution.goal) {
                TEST_COMPILE_GOAL -> throw IllegalStateException(
                    "$MOCKABLE opens classes of the main code, but the execution '${execution.executionId}' of " +
                        "kotlin-maven-plugin passes it to the test compilation too. Set <pluginOptions> with " +
                        "$MOCKABLE in the configuration of the execution of the compile goal instead of the " +
                        "configuration of the plugin."
                )
                COMPILE_GOAL -> if (!project.isMockablePublicationWarningDisabled()) {
                    logger.warn(
                        "$MOCKABLE opens classes and adds interception points to objects and top-level functions " +
                            "compiled by the execution '${execution.executionId}' of ${project.groupId}:" +
                            "${project.artifactId}, and the artifacts of this project contain this bytecode. To " +
                            "disable this warning, set the property $MOCKABLE_PUBLICATION_NOWARN=true."
                    )
                }
            }
        }
        return emptyList()
    }
}

private fun MojoExecution.hasMockablePatterns(): Boolean =
    configuration?.getChild("pluginOptions")?.children.orEmpty().any { it.value?.startsWith("$MOCKABLE=") == true }

private fun MavenProject.isMockablePublicationWarningDisabled(): Boolean =
    (System.getProperty(MOCKABLE_PUBLICATION_NOWARN) ?: properties.getProperty(MOCKABLE_PUBLICATION_NOWARN))
        ?.toBoolean() == true

private const val MOCKABLE = "lokki:${BuildConfig.MOCKABLE_OPTION}"
private const val COMPILE_GOAL = "compile"
private const val TEST_COMPILE_GOAL = "test-compile"
private const val MOCKABLE_PUBLICATION_NOWARN = "lokki.mockablePublication.nowarn"
