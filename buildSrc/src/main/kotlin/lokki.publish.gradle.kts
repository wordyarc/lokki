import lokki.buildlogic.functionalTestRepository

plugins {
    `maven-publish`
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

tasks.withType<PublishToMavenRepository>().configureEach {
    dependsOn(cleanFunctionalTestRepository)
}
