package org.seqra.ir.api.py

import org.seqra.ir.api.common.CommonMethod
import org.seqra.ir.api.common.CommonMethodParameter
import org.seqra.ir.api.common.*
import org.seqra.ir.api.common.cfg.CommonInst
import org.seqra.ir.api.common.cfg.ControlFlowGraph
import org.seqra.ir.api.py.cfg.*

const val PIR_FUNC_NORMAL = 0
const val PIR_FUNC_STATICMETHOD = 1
const val PIR_FUNC_CLASSMETHOD = 2

const val ARG_POS = 0
const val ARG_OPT = 1
const val ARG_STAR = 2
const val ARG_NAMED = 3
const val ARG_STAR2 = 4
const val ARG_NAMED_OPT = 5

data class PIRRuntimeArg(
    val name: String,
    override val type: CommonTypeName,
    val kind: Int = ARG_POS,
    val posOnly: Boolean = false
) : CommonMethodParameter {
    val optional: Boolean = kind == ARG_OPT || kind == ARG_NAMED_OPT
}

data class PIRFuncSignature(
    val args: List<PIRRuntimeArg>,
    val retType: PIRType
) {
//    val numBitmapArgs: Int = args.count {
//        it.type.errorOverlap && (it.kind == ARG_OPT || it.kind == ARG_NAMED_OPT)
//    }
}

data class PIRFuncDecl(
    val name: String,
    val className: String?,
    val moduleName: String,
    val sig: PIRFuncSignature,
    val kind: Int = PIR_FUNC_NORMAL,
    val isPropSetter: Boolean = false,
    val isPropGetter: Boolean = false,
    val isGenerator: Boolean = false,
    val isCoroutine: Boolean = false,
    val implicit: Boolean = false,
    val internal: Boolean = false,
    val line: Int? = null
) {
    val shortname: String = if (className != null) "$className.$name" else name
    val fullname: String = "$moduleName.$shortname"
}

data class PIRFunc(
    val decl: PIRFuncDecl,
    val argRegs: List<PIRRegister>,
    val blocks: List<PIRBasicBlock>,
    val tracebackName: String? = null,
    val enclosingClass: PIRClass
) : CommonMethod {
    override val name: String get() = decl.name

    override val parameters: List<CommonMethodParameter>
        get() = decl.sig.args

    override val returnType: CommonTypeName
        get() = decl.sig.retType

    override fun flowGraph(): ControlFlowGraph<CommonInst> {
        return ControlFlowGraph(emptyList(), emptyList())
    }

    val line: Int? get() = decl.line
    val className: String? get() = decl.className
    val fullname: String get() = decl.fullname
}

class ControlFlowGraph<T : CommonInst>(
    override val instructions: List<T>,
    private val edges: List<Pair<Int, Int>>
) : ControlFlowGraph<T> {

    private val successorsMap: Map<Int, Set<Int>> = edges
        .groupBy { it.first }
        .mapValues { (_, edgesFrom) -> edgesFrom.map { it.second }.toSet() }

    private val predecessorsMap: Map<Int, Set<Int>> = edges
        .groupBy { it.second }
        .mapValues { (_, edgesTo) -> edgesTo.map { it.first }.toSet() }

    override fun successors(node: T): Set<T> {
        val index = instructions.indexOf(node)
        return if (index in successorsMap) {
            successorsMap[index]!!.map { instructions[it] }.toSet()
        } else {
            emptySet()
        }
    }

    override fun predecessors(node: T): Set<T> {
        val index = instructions.indexOf(node)
        return if (index in predecessorsMap) {
            predecessorsMap[index]!!.map { instructions[it] }.toSet()
        } else {
            emptySet()
        }
    }

    override val entries: List<T>
        get() = instructions.filterIndexed { i, _ ->
            !predecessorsMap.containsKey(i) || predecessorsMap[i]!!.isEmpty()
        }

    override val exits: List<T>
        get() = instructions.filterIndexed { i, _ ->
            !successorsMap.containsKey(i) || successorsMap[i]!!.isEmpty()
        }
}