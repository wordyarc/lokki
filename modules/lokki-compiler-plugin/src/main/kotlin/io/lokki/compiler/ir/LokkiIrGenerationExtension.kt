package io.lokki.compiler.ir

import io.lokki.compiler.IncompatibleRuntimeException
import io.lokki.compiler.LokkiDiagnostics
import io.lokki.compiler.MockablePatterns
import io.lokki.compiler.compat.CompilerCompat
import io.lokki.compiler.ir.collect.Mockability
import io.lokki.compiler.ir.collect.ReifiedHelpers
import io.lokki.compiler.ir.collect.collectExternalMocks
import io.lokki.compiler.ir.collect.collectMockFields
import io.lokki.compiler.ir.collect.collectMockSites
import io.lokki.compiler.ir.generate.ExternalMockFactories
import io.lokki.compiler.ir.generate.InterceptionPoints
import io.lokki.compiler.ir.generate.MockClassGenerator
import io.lokki.compiler.ir.generate.MockFieldInitializers
import io.lokki.compiler.ir.generate.MockParameterFactories
import io.lokki.compiler.ir.generate.MockableClasses
import io.lokki.compiler.ir.generate.PrivateMembers
import io.lokki.compiler.ir.generate.sourceClasses
import io.lokki.compiler.ir.lower.ConstructorCalls
import io.lokki.compiler.ir.lower.ContextBlocks
import io.lokki.compiler.ir.lower.DslLowering
import io.lokki.compiler.ir.lower.DynamicCalls
import io.lokki.compiler.ir.lower.StaticInitializers
import io.lokki.compiler.linked
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.incremental.components.LookupTracker
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.util.dumpKotlinLike

internal class LokkiIrGenerationExtension(
    private val opened: MockablePatterns?,
    private val intercepted: MockablePatterns?,
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
        opened?.let { patterns ->
            val classes = moduleFragment.sourceClasses()
            MockableClasses(lokki, patterns).open(classes)
            PrivateMembers(lokki, patterns).generate(classes)
            intercepted?.let { InterceptionPoints(lokki, it).generate(moduleFragment, classes) }
        }
        val constructorCalls = ConstructorCalls(lokki)
        if (!lokki.symbols.isRuntimeAvailable) {
            moduleFragment.files.forEach { constructorCalls.rewrite(it) }
            return
        }
        val generator = MockClassGenerator(lokki)
        val staticInitializers = StaticInitializers(lokki)
        val fieldInitializers = MockFieldInitializers(lokki)
        val parameterFactories = MockParameterFactories(lokki)
        val externalFactories = ExternalMockFactories(lokki)
        val contextBlocks = ContextBlocks(lokki)
        val reifiedHelpers = ReifiedHelpers(lokki).apply { collect(moduleFragment) }
        val files = moduleFragment.files.map { file ->
            val mockability = Mockability(file, lokki)
            val fields = file.collectMockFields(mockability, lokki.typeSystem)
            FileContext(file, lokki, mockability, fields, file.collectExternalMocks(mockability), reifiedHelpers)
        }
        fieldInitializers.declare(files.map { it.fields })
        for (file in files) {
            file.ir.locatingInternalErrors {
                file.ir.transform(contextBlocks, null)
                file.ir.transform(DynamicCalls(lokki, file), null)
                val sites = file.collectMockSites()
                file.diagnostics.report(file.fields, sites)
                file.diagnostics.report(file.external)
                val classes = generator.generate(file, sites.classes)
                fieldInitializers.implement(file, classes)
                parameterFactories.generate(file, classes)
                externalFactories.generate(file, classes)
                file.ir.transform(DslLowering(lokki, file, sites, classes), null)
                constructorCalls.rewrite(file.ir, file.redirects)
                staticInitializers.lower(file.ir)
            }
        }
    }
}
