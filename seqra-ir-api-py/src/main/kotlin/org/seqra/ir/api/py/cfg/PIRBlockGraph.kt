package org.seqra.ir.api.py.cfg

import org.seqra.ir.api.py.*

data class PIRInstRef(
    val index: Int,
    val inst: PIRInst? = null
)

data class PIRBasicBlock(val start: PIRInstRef, val end: PIRInstRef) {
    operator fun contains(inst: PIRInst): Boolean {
        return inst.location.index <= end.index && inst.location.index >= start.index
    }

    operator fun contains(inst: PIRInstRef): Boolean {
        return inst.index <= end.index && inst.index >= start.index
    }
}

interface PIRBlockGraph : PIRBytecodeGraph<PIRBasicBlock> {
    val pIRGraph: PIRGraph
    val entry: PIRBasicBlock

    fun instructions(block: PIRBasicBlock): List<PIRInst>
    fun block(inst: PIRInst): PIRBasicBlock
}
