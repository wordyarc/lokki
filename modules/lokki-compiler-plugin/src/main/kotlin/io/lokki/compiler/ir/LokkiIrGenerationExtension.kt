package io.lokki.compiler.ir

import io.lokki.compiler.ir.collect.collectMockClasses
import io.lokki.compiler.ir.generate.MockClassGenerator
import io.lokki.compiler.ir.lower.DslLowering
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.incremental.components.LookupTracker
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment

class LokkiIrGenerationExtension(private val lookupTracker: LookupTracker?) : IrGenerationExtension {
    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        val symbols = LokkiSymbols(pluginContext)
        val generator = MockClassGenerator(pluginContext, symbols, lookupTracker)
        for (file in moduleFragment.files) {
            val mocks = file.collectMockClasses().mapValues { (key, usage) -> generator.generate(key, usage) }
            file.transform(DslLowering(pluginContext, symbols, file, mocks), null)
        }
    }
}
