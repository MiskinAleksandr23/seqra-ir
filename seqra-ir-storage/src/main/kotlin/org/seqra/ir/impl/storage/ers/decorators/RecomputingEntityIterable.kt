package org.seqra.ir.impl.storage.ers.decorators

import org.seqra.ir.api.storage.ers.EntityIterable
import org.seqra.ir.api.storage.ers.Transaction

fun Transaction.recomputeEntityIterableOnEachUse() = decorateDeeplyWithLazyIterable(
    entityIterableWrapper = { entityIterableCreator -> RecomputingEntityIterable(entityIterableCreator) }
)

class RecomputingEntityIterable(
    val entityIterableCreator: () -> EntityIterable
) : AbstractEntityIterableDecorator() {
    override val delegate: EntityIterable get() = entityIterableCreator()
}
