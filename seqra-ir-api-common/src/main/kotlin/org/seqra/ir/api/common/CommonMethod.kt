package org.seqra.ir.api.common

import org.seqra.ir.api.common.cfg.CommonInst
import org.seqra.ir.api.common.cfg.ControlFlowGraph

interface CommonMethod {
    val name: String
    val parameters: List<CommonMethodParameter>
    val returnType: CommonTypeName

    fun flowGraph(): ControlFlowGraph<CommonInst>
}

interface CommonMethodParameter {
    val type: CommonTypeName
}
