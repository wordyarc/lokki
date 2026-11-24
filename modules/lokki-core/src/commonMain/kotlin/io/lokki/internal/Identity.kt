package io.lokki.internal

internal expect fun identityHashCode(instance: Any): Int

internal expect fun GeneratedMock.mocksClass(type: Any): Boolean

internal expect fun GeneratedMock.redirectsFile(type: Any): Boolean

internal expect fun Any.isStaticOf(type: Any): Boolean

internal expect fun dynamicProxyClass(instance: Any): String?
