import com.vanniktech.maven.publish.DeploymentValidation
import lokki.buildlogic.REPOSITORY_PATH
import lokki.buildlogic.REPOSITORY_URL
import lokki.buildlogic.functionalTestRepository

plugins {
    id("com.vanniktech.maven.publish")
}

tasks.withType<AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

mavenPublishing {
    publishToMavenCentral(automaticRelease = true, validateDeployment = DeploymentValidation.PUBLISHED)
    if (providers.gradleProperty("signingInMemoryKey").isPresent) signAllPublications()

    pom {
        name = project.name
        description = provider { requireNotNull(project.description) { "${project.path} has no description" } }
        url = REPOSITORY_URL
        inceptionYear = "2026"
        licenses {
            license {
                name = "The Apache License, Version 2.0"
                url = "https://www.apache.org/licenses/LICENSE-2.0.txt"
                distribution = "repo"
            }
        }
        developers {
            developer {
                id = "wordyarc"
                name = "Viktor Kogai"
                url = "https://github.com/wordyarc"
            }
        }
        scm {
            url = REPOSITORY_URL
            connection = "scm:git:$REPOSITORY_URL.git"
            developerConnection = "scm:git:ssh://git@github.com/$REPOSITORY_PATH.git"
        }
    }
}

publishing {
    repositories {
        maven {
            name = "functionalTest"
            url = uri(functionalTestRepository)
        }
    }
}

val cleanFunctionalTestRepository = tasks.register<Delete>("cleanFunctionalTestRepository") {
    delete(functionalTestRepository)
}

val mavenCentralNamespace = group.toString()

tasks.withType<PublishToMavenRepository>().configureEach {
    if (name.endsWith("ToFunctionalTestRepository")) dependsOn(cleanFunctionalTestRepository)
    if (name.endsWith("ToMavenCentralRepository")) {
        val inNamespace = provider {
            publication.groupId.let { it == mavenCentralNamespace || it.startsWith("$mavenCentralNamespace.") }
        }
        onlyIf("its group is in the Maven Central namespace $mavenCentralNamespace") { inNamespace.get() }
    }
}
