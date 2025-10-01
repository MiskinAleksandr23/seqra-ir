package org.seqra.ir.impl.cfg.util

import org.seqra.ir.api.jvm.cfg.JIRRawInst
import org.seqra.ir.api.jvm.cfg.JIRRawInstVisitor

class InstructionFilter(
    val predicate: (JIRRawInst) -> Boolean,
) : JIRRawInstVisitor.Default<Boolean> {
    override fun defaultVisitJIRRawInst(inst: JIRRawInst): Boolean {
        return predicate(inst)
    }
}
