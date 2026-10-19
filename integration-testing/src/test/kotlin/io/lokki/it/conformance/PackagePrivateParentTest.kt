package io.lokki.it.conformance

import io.lokki.mock
import kotlin.test.Test

internal open class InternalCls : PackagePrivateCls()

class PackagePrivateParentTest {
    @Test
    fun mockClassWithPackagePrivateParent() {
        mock<InternalCls>()
    }
}
