package io.lokki.compiler.ir

import io.lokki.compiler.IncompatibleRuntimeException
import io.lokki.compiler.LokkiDiagnostics
import io.lokki.compiler.MockablePatterns
import io.lokki.compiler.compat.CompilerCompat
import io.lokki.compiler.ir.collect.Mockability
import io.lokki.compiler.ir.collect.collectMockFields
import io.lokki.compiler.ir.collect.collectMockSites
import io.lokki.compiler.ir.generate.InterceptionPoints
import io.lokki.compiler.ir.generate.MockClassGenerator
import io.lokki.compiler.ir.generate.MockFieldInitializers
import io.lokki.compiler.ir.generate.MockableClasses
import io.lokki.compiler.ir.lower.ContextBlocks
import io.lokki.compiler.ir.lower.DslLowering
import io.lokki.compiler.ir.lower.StaticInitializers
import io.lokki.compiler.linked
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.incremental.components.LookupTracker
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.util.dumpKotlinLike

internal class LokkiIrGenerationExtension(
    private val mockable: MockablePatterns?,
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
        try {
            lower(moduleFragment, LokkiIrContext(pluginContext, compat, lookupTracker))
        } catch (failure: IncompatibleRuntimeException) {
            val factory = LokkiDiagnostics.LOKKI_RUNTIME_INCOMPATIBLE
            compat.reportWithoutSource(pluginContext.diagnosticReporter, factory, failure.problem)
        }
    }

    private fun lower(moduleFragment: IrModuleFragment, lokki: LokkiIrContext) {
        mockable?.let {
            MockableClasses(lokki, it).open(moduleFragment)
            InterceptionPoints(lokki, it).generate(moduleFragment)
        }
        if (!lokki.symbols.isRuntimeAvailable) return
        val generator = MockClassGenerator(lokki)
        val staticInitializers = StaticInitializers(lokki)
        val fieldInitializers = MockFieldInitializers(lokki)
        val contextBlocks = ContextBlocks(lokki)
        val files = moduleFragment.files.map { file ->
            val mockability = Mockability(file)
            FileContext(file, lokki, mockability, file.collectMockFields(mockability, lokki.typeSystem))
        }
        fieldInitializers.declare(files.map { it.fields })
        for (file in files) {
            file.ir.transform(contextBlocks, null)
            val sites = file.collectMockSites()
            file.diagnostics.report(file.fields, sites)
            val classes = generator.generate(file, sites.classes)
            fieldInitializers.implement(file, classes)
            file.ir.transform(DslLowering(lokki, file, sites, classes), null)
            staticInitializers.lower(file.ir)
        }
    }
}
