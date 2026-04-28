package org.seqra.ir.api.py.cfg

import org.seqra.ir.api.common.cfg.BytecodeGraph

interface PIRBytecodeGraph<T> {
    val instructions: List<T>
    val exits: List<T>
    val entries: List<T>

    fun successors(node: T): Set<T>
    fun predecessors(node: T): Set<T>
}
