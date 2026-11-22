package io.lokki.maven

import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

val KOTLIN_VERSION: String = System.getProperty("lokki.test.kotlinVersion")
val TESTED_KOTLIN: List<String> = System.getProperty("lokki.test.testedKotlin").split(',')
val LOKKI_GROUP: String = System.getProperty("lokki.test.lokkiGroup")
val LOKKI_VERSION: String = System.getProperty("lokki.test.lokkiVersion")

enum class MavenUnderTest(val version: String, val home: String, val javaHome: String) {
    CURRENT(
        System.getProperty("lokki.test.mavenVersion"),
        System.getProperty("lokki.test.maven.current"),
        System.getProperty("java.home"),
    ),
    MINIMAL(
        System.getProperty("lokki.test.minMavenVersion"),
        System.getProperty("lokki.test.maven.minimal"),
        System.getProperty("lokki.test.minMavenJavaHome"),
    ),
}

class BuildResult(val exitCode: Int, val output: String)

class TestProject(
    private val dir: File,
    private val maven: MavenUnderTest = MavenUnderTest.CURRENT,
    private val kotlin: String = KOTLIN_VERSION,
) {
    init {
        write("settings.xml", "<settings/>")
    }

    fun write(path: String, content: String) {
        dir.resolve(path).apply { parentFile.mkdirs() }.writeText(content.trimIndent())
    }

    fun pom(configuration: String = "", compileConfiguration: String = "", properties: String = "") {
        val repositories = System.getProperty("lokki.test.repositories").split(File.pathSeparator)
            .mapIndexed { index, path -> "<id>lokki-$index</id><url>${File(path).toURI()}</url>" }
        write(
            "pom.xml",
            """
            <project xmlns="http://maven.apache.org/POM/4.0.0">
              <modelVersion>4.0.0</modelVersion>
              <groupId>sample</groupId>
              <artifactId>sample</artifactId>
              <version>1.0</version>
              <properties>
                <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
                <kotlin.compiler.jvmTarget>17</kotlin.compiler.jvmTarget>
                $properties
              </properties>
              <repositories>
                ${repositories.joinToString("") { "<repository>$it</repository>" }}
              </repositories>
              <pluginRepositories>
                ${repositories.joinToString("") { "<pluginRepository>$it</pluginRepository>" }}
              </pluginRepositories>
              <dependencies>
                <dependency>
                  <groupId>org.jetbrains.kotlin</groupId>
                  <artifactId>kotlin-stdlib</artifactId>
                  <version>$kotlin</version>
                </dependency>
                <dependency>
                  <groupId>$LOKKI_GROUP</groupId>
                  <artifactId>lokki-core-jvm</artifactId>
                  <version>$LOKKI_VERSION</version>
                  <scope>test</scope>
                </dependency>
                <dependency>
                  <groupId>org.junit.jupiter</groupId>
                  <artifactId>junit-jupiter</artifactId>
                  <version>${System.getProperty("lokki.test.jupiterVersion")}</version>
                  <scope>test</scope>
                </dependency>
              </dependencies>
              <build>
                <sourceDirectory>src/main/kotlin</sourceDirectory>
                <testSourceDirectory>src/test/kotlin</testSourceDirectory>
                <plugins>
                  <plugin>
                    <groupId>org.jetbrains.kotlin</groupId>
                    <artifactId>kotlin-maven-plugin</artifactId>
                    <version>$kotlin</version>
                    <configuration>
                      <compilerPlugins>
                        <plugin>lokki</plugin>
                      </compilerPlugins>
                      $configuration
                    </configuration>
                    <executions>
                      <execution>
                        <id>compile</id>
                        <goals>
                          <goal>compile</goal>
                        </goals>
                        <configuration>$compileConfiguration</configuration>
                      </execution>
                      <execution>
                        <id>test-compile</id>
                        <goals>
                          <goal>test-compile</goal>
                        </goals>
                      </execution>
                    </executions>
                    <dependencies>
                      <dependency>
                        <groupId>$LOKKI_GROUP</groupId>
                        <artifactId>lokki-maven</artifactId>
                        <version>$LOKKI_VERSION</version>
                      </dependency>
                    </dependencies>
                  </plugin>
                  <plugin>
                    <groupId>org.apache.maven.plugins</groupId>
                    <artifactId>maven-surefire-plugin</artifactId>
                    <version>${System.getProperty("lokki.test.surefireVersion")}</version>
                  </plugin>
                </plugins>
              </build>
            </project>
            """,
        )
    }

    fun build(vararg arguments: String): BuildResult =
        run(arguments).also { assertEquals(0, it.exitCode, "mvn ${arguments.joinToString(" ")} failed") }

    fun buildAndFail(vararg arguments: String): BuildResult =
        run(arguments).also { assertNotEquals(0, it.exitCode, "mvn ${arguments.joinToString(" ")} succeeded") }

    private fun run(arguments: Array<out String>): BuildResult {
        val command = listOf(
            "sh",
            File(maven.home, "bin/mvn").path,
            "--batch-mode",
            "--update-snapshots",
            "--no-transfer-progress",
            "--settings",
            dir.resolve("settings.xml").path,
            "-Dmaven.repo.local=${System.getProperty("lokki.test.mavenRepository")}",
            *arguments,
        )
        val process = ProcessBuilder(command)
            .directory(dir)
            .redirectErrorStream(true)
            .apply {
                environment()["JAVA_HOME"] = maven.javaHome
                environment().remove("MAVEN_OPTS")
                environment().remove("MAVEN_ARGS")
            }
            .start()
        val output = StringBuilder()
        process.inputStream.bufferedReader().useLines { lines ->
            lines.forEach { line ->
                println(line)
                output.appendLine(line)
            }
        }
        return BuildResult(process.waitFor(), output.toString())
    }

    fun testCount(className: String): Int {
        val report = dir.resolve("target/surefire-reports/TEST-$className.xml").readText()
        val suite = Regex("""<testsuite [^>]*>""").find(report)!!.value
        fun attribute(name: String) = Regex("""\b$name="(\d+)"""").find(suite)!!.groupValues[1].toInt()
        assertEquals(0, attribute("failures") + attribute("errors"), report)
        return attribute("tests")
    }
}
