package io.lokki.compiler.ir

import io.lokki.compiler.LokkiDiagnostics
import io.lokki.compiler.Mockable
import io.lokki.compiler.compat.CompilerCompat
import io.lokki.compiler.ir.collect.builtIns
import io.lokki.compiler.ir.collect.collectMockClasses
import io.lokki.compiler.ir.generate.InterceptionPoints
import io.lokki.compiler.ir.generate.MockClassGenerator
import io.lokki.compiler.ir.generate.MockFieldInitializers
import io.lokki.compiler.ir.lower.ContextBlocks
import io.lokki.compiler.ir.lower.DslLowering
import io.lokki.compiler.ir.lower.StaticInitializers
import io.lokki.compiler.linked
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.incremental.components.LookupTracker
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.util.dumpKotlinLike

class LokkiIrGenerationExtension(
    private val mockable: Mockable?,
    private val lookupTracker: LookupTracker?,
    private val debug: Boolean,
    private val compat: CompilerCompat,
) : IrGenerationExtension {
    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        linked {
            if (!debug) return lower(moduleFragment, pluginContext)
            val dumps = moduleFragment.files.associateWith { it.dumpKotlinLike() }
            lower(moduleFragment, pluginContext)
            for ((file, before) in dumps) {
                val after = file.dumpKotlinLike()
                if (after != before) {
                    val message = "${file.fileEntry.name}:\n$after"
                    compat.reportWithoutSource(pluginContext.diagnosticReporter, LokkiDiagnostics.LOKKI_DEBUG, message)
                }
            }
        }
    }

    private fun lower(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        val symbols = LokkiSymbols(pluginContext)
        mockable?.let { InterceptionPoints(pluginContext, symbols, it, compat).generate(moduleFragment) }
        if (!symbols.isRuntimeAvailable) return
        moduleFragment.builtIns = pluginContext.irBuiltIns
        val generator = MockClassGenerator(pluginContext, symbols, lookupTracker, compat)
        val staticInitializers = StaticInitializers(pluginContext, symbols)
        val fields = MockFieldInitializers(pluginContext, symbols, lookupTracker, compat)
        val redirects = Redirects(pluginContext)
        val contextBlocks = ContextBlocks(pluginContext, symbols)
        fields.declare(moduleFragment)
        for (file in moduleFragment.files) {
            file.transform(contextBlocks, null)
            val mocks = generator.generate(file, file.collectMockClasses(redirects))
            fields.implement(file, mocks)
            file.transform(DslLowering(pluginContext, symbols, file, mocks, redirects, compat), null)
            staticInitializers.lower(file)
        }
    }
}
