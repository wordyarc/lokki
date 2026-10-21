package io.lokki.compiler.ir

import io.lokki.compiler.ir.collect.builtIns
import io.lokki.compiler.ir.collect.collectMockClasses
import io.lokki.compiler.ir.generate.MockClassGenerator
import io.lokki.compiler.ir.generate.MockFieldInitializers
import io.lokki.compiler.ir.lower.DslLowering
import io.lokki.compiler.ir.lower.StaticInitializers
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.incremental.components.LookupTracker
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment

class LokkiIrGenerationExtension(private val lookupTracker: LookupTracker?) : IrGenerationExtension {
    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        val symbols = LokkiSymbols(pluginContext)
        if (!symbols.isRuntimeAvailable) return
        moduleFragment.builtIns = pluginContext.irBuiltIns
        val generator = MockClassGenerator(pluginContext, symbols, lookupTracker)
        val staticInitializers = StaticInitializers(pluginContext, symbols)
        val fields = MockFieldInitializers(pluginContext, symbols, lookupTracker)
        fields.declare(moduleFragment)
        for (file in moduleFragment.files) {
            val mocks = generator.generate(file, file.collectMockClasses())
            fields.implement(file, mocks)
            file.transform(DslLowering(pluginContext, symbols, file, mocks), null)
            staticInitializers.lower(file)
        }
    }
}
