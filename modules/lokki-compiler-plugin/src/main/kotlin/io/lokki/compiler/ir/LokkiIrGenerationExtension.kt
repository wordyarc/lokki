package io.lokki.compiler.ir

import io.lokki.compiler.LokkiDiagnostics
import io.lokki.compiler.Mockable
import io.lokki.compiler.compat.CompilerCompat
import io.lokki.compiler.ir.collect.MockFields
import io.lokki.compiler.ir.collect.Mockability
import io.lokki.compiler.ir.collect.collectMockFields
import io.lokki.compiler.ir.collect.collectMockSites
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
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.types.IrTypeSystemContextImpl
import org.jetbrains.kotlin.ir.util.dumpKotlinLike

internal class LokkiIrGenerationExtension(
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
        val diagnostics = IrDiagnostics(pluginContext, compat)
        val typeSystem = IrTypeSystemContextImpl(pluginContext.irBuiltIns)
        val generator = MockClassGenerator(pluginContext, symbols, lookupTracker, diagnostics, typeSystem)
        val staticInitializers = StaticInitializers(pluginContext, symbols)
        val fieldInitializers = MockFieldInitializers(pluginContext, symbols, lookupTracker, diagnostics)
        val contextBlocks = ContextBlocks(pluginContext, symbols)
        val files = moduleFragment.files.map { file ->
            val mockability = Mockability(file)
            FileMocks(file, mockability, file.collectMockFields(mockability, typeSystem))
        }
        fieldInitializers.declare(files.map { it.fields })
        for (mocks in files) {
            val file = mocks.file
            val redirects = Redirects(pluginContext, file)
            file.transform(contextBlocks, null)
            val sites = file.collectMockSites(mocks.mockability, mocks.fields, redirects)
            val classes = generator.generate(file, sites.classes, mocks.mockability)
            fieldInitializers.implement(file, mocks.fields, classes)
            val mockability = mocks.mockability
            file.transform(
                DslLowering(pluginContext, symbols, file, sites, classes, mockability, redirects, diagnostics),
                null,
            )
            staticInitializers.lower(file)
        }
    }

    private class FileMocks(val file: IrFile, val mockability: Mockability, val fields: MockFields)
}
