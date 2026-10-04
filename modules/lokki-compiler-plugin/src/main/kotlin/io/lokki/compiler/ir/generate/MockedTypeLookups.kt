package io.lokki.compiler.ir.generate

import io.lokki.compiler.ir.collect.MockClassKey
import org.jetbrains.kotlin.incremental.components.LookupTracker
import org.jetbrains.kotlin.incremental.components.Position
import org.jetbrains.kotlin.incremental.components.ScopeKind
import org.jetbrains.kotlin.ir.declarations.IrOverridableMember
import org.jetbrains.kotlin.ir.declarations.path
import org.jetbrains.kotlin.ir.util.getAllSuperclasses
import org.jetbrains.kotlin.ir.util.isFakeOverride
import org.jetbrains.kotlin.ir.util.kotlinFqName
import org.jetbrains.kotlin.resolve.sam.SAM_LOOKUP_NAME

internal fun LookupTracker.recordMockedTypeLookups(key: MockClassKey) {
    for (type in key.mockedClass.getAllSuperclasses() + key.mockedClass) {
        val scope = type.kotlinFqName.asString()
        val memberNames = type.declarations.filterIsInstance<IrOverridableMember>()
            .filterNot { it.isFakeOverride }
            .map { it.name }
        for (name in memberNames + SAM_LOOKUP_NAME) {
            record(key.file.path, Position.NO_POSITION, scope, ScopeKind.CLASSIFIER, name.asString())
        }
    }
}
