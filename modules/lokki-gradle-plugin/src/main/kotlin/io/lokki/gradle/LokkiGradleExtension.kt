package io.lokki.gradle

import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import javax.inject.Inject

public abstract class LokkiGradleExtension @Inject constructor(objects: ObjectFactory) {
    internal val mockable: SetProperty<String> = objects.setProperty(String::class.java)

    public val applyToMain: Property<Boolean> = objects.property(Boolean::class.java).convention(false)

    public val debug: Property<Boolean> = objects.property(Boolean::class.java).convention(false)

    public val recordStackTraces: Property<Boolean> = objects.property(Boolean::class.java).convention(false)

    public fun mockable(vararg patterns: String) {
        mockable.addAll(*patterns)
    }
}
