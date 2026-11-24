package io.lokki.it.spring

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

interface Greeter {
    fun greet(name: String): String
}

open class Repository {
    open fun load(id: Int): String = "stored $id"
}

open class Service(private val greeter: Greeter, private val repository: Repository) {
    open fun welcome(id: Int): String = greeter.greet(repository.load(id))
}

@Configuration(proxyBeanMethods = false)
class Beans {
    @Bean
    fun greeter(): Greeter =
        object : Greeter {
            override fun greet(name: String): String = "hello $name"
        }

    @Bean
    fun repository(): Repository = Repository()

    @Bean
    fun service(greeter: Greeter, repository: Repository): Service = Service(greeter, repository)
}
