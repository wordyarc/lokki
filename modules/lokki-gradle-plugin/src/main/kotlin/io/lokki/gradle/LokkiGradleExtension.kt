package io.lokki.gradle

import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import javax.inject.Inject

public abstract class LokkiGradleExtension @Inject constructor(objects: ObjectFactory) {
    @get:JvmName("getMockable")
    internal val mockable: SetProperty<String> = objects.setProperty(String::class.java)

    @get:JvmName("getInterceptable")
    internal val interceptable: SetProperty<String> = objects.setProperty(String::class.java)

    @get:JvmName("getRestricted")
    internal val restricted: SetProperty<String> = objects.setProperty(String::class.java)

    @get:JvmName("getTestCompilations")
    internal val testCompilations: SetProperty<String> = objects.setProperty(String::class.java)

    public val applyToMain: Property<Boolean> = objects.property(Boolean::class.java).convention(false)

    public val recordStackTraces: Property<Boolean> = objects.property(Boolean::class.java).convention(false)

    init {
        listOf(mockable, interceptable, restricted, testCompilations, applyToMain, recordStackTraces).forEach { it.finalizeValueOnRead() }
    }

    public fun mockable(vararg patterns: String) {
        mockable.addAll(*patterns)
    }

    public fun interceptable(vararg patterns: String) {
        interceptable.addAll(*patterns)
    }

    public fun restricted(vararg patterns: String) {
        restricted.addAll(*patterns)
    }

    public fun testCompilations(vararg names: String) {
        testCompilations.addAll(*names)
    }
}
