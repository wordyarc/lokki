package io.lokki.compiler.ir

import io.lokki.compiler.ir.collect.collectMockClasses
import io.lokki.compiler.ir.generate.MockClassGenerator
import io.lokki.compiler.ir.lower.MockCallLowering
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.incremental.components.LookupTracker
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid

class LokkiIrGenerationExtension(private val lookupTracker: LookupTracker?) : IrGenerationExtension {
    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        val generator = MockClassGenerator(pluginContext, lookupTracker)
        for (file in moduleFragment.files) {
            val mocks = file.collectMockClasses().mapValues { (key, usage) -> generator.generate(key, usage) }
            if (mocks.isNotEmpty()) file.transformChildrenVoid(MockCallLowering(file, mocks))
        }
    }
}
