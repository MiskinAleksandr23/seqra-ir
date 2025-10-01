package org.seqra.ir.impl.storage.ers.decorators

import org.seqra.ir.api.storage.ers.Transaction

fun Transaction.withAllDecorators(): Transaction =
    recomputeEntityIterableOnEachUse().withChecks()
