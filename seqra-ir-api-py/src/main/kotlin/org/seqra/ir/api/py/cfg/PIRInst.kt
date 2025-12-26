package org.seqra.ir.api.py.cfg

import org.seqra.ir.api.common.cfg.*
import org.seqra.ir.api.py.*

const val ERR_NEVER = 0
const val ERR_FALSE = 2

const val NAMESPACE_STATIC = "static"
const val NAMESPACE_TYPE = "type"

interface PIRInstLocation : CommonInstLocation {
    override val method: PIRFunc
    val index: Int
    val line: Int
}

interface PIRInst : CommonInst {
    override val location: PIRInstLocation
    val operands: List<Any>
    val line: Int
    val errorKind: Int
    val type: PIRType

    fun canRaise(): Boolean = errorKind != ERR_NEVER

    fun <T> accept(visitPIRor: PIRInstVisitor<T>): T
}

abstract class AbstractPIRInst(
    final override val location: PIRInstLocation,
    override val errorKind: Int,
    override val type: PIRType
) : PIRInst {
    override val line: Int = location.line

    override fun hashCode(): Int = location.hashCode()
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AbstractPIRInst

        if (location != other.location) return false
        if (errorKind != other.errorKind) return false
        if (type != other.type) return false
        if (line != other.line) return false

        return true
    }
}

abstract class PIRBaseAssignInst(
    location: PIRInstLocation,
    errorKind: Int,
    type: PIRType,
    open val dest: PIRRegister
) : AbstractPIRInst(location, errorKind, type)

abstract class PIRControlOpInst(
    location: PIRInstLocation,
    errorKind: Int,
    type: PIRType
) : AbstractPIRInst(location, errorKind, type)

abstract class PIRRegisterOpInst(
    location: PIRInstLocation,
    errorKind: Int,
    type: PIRType
) : AbstractPIRInst(location, errorKind, type)

class PIRAssignInst(
    location: PIRInstLocation,
    override val dest: PIRRegister,
    private val src: PIRValue
) : PIRBaseAssignInst(location, ERR_NEVER, dest.type, dest), CommonAssignInst {
    override val lhv: CommonValue get() = dest
    override val rhv: CommonExpr get() = src

    override val operands: List<Any> = listOf(dest, src)

    override fun <T> accept(visitPIRor: PIRInstVisitor<T>): T = visitPIRor.visitPIRAssignInst(this)
}

class PIRAssignMultiInst(
    location: PIRInstLocation,
    override val dest: PIRRegister,
    private val src: List<PIRValue>
) : PIRBaseAssignInst(location, ERR_NEVER, dest.type, dest) {
    override val operands: List<Any> = listOf(dest) + src

    override fun <T> accept(visitPIRor: PIRInstVisitor<T>): T = visitPIRor.visitPIRAssignMultiInst(this)
}

class PIRGotoInst(
    location: PIRInstLocation,
    val target: PIRInstRef,
    val label: PIRBasicBlock
) : PIRControlOpInst(location, ERR_NEVER, PIRVoidType()), CommonGotoInst {
    override val operands: List<PIRValue> = emptyList()

    override fun <T> accept(visitPIRor: PIRInstVisitor<T>): T = visitPIRor.visitPIRGotoInst(this)
}

class PIRBranchInst(
    location: PIRInstLocation,
    private val value: PIRValue,
    val trueLabel: PIRInstRef,
    val falseLabel: PIRInstRef,
    val op: Int,
    val negated: Boolean = false,
    val tracebackEntry: Pair<String, Int>? = null,
    val rare: Boolean = false
) : PIRControlOpInst(location, ERR_NEVER, PIRVoidType()), CommonIfInst {
    override val operands: List<PIRValue> = listOf(value)

    override fun <T> accept(visitPIRor: PIRInstVisitor<T>): T = visitPIRor.visitPIRBranchInst(this)
}

class PIRReturnInst(
    location: PIRInstLocation,
    override val returnValue: PIRValue?,
    val yieldTarget: PIRBasicBlock? = null
) : PIRControlOpInst(location, ERR_NEVER, PIRVoidType()), CommonReturnInst {
    override val operands: List<PIRValue> = listOfNotNull(returnValue)

    override fun <T> accept(visitPIRor: PIRInstVisitor<T>): T = visitPIRor.visitPIRReturnInst(this)
}

class PIRUnreachableInst(
    location: PIRInstLocation
) : PIRControlOpInst(location, ERR_NEVER, PIRVoidType()) {
    override val operands: List<PIRValue> = emptyList()

    override fun <T> accept(visitPIRor: PIRInstVisitor<T>): T = visitPIRor.visitPIRUnreachableInst(this)
}

class PIRIncRefInst(
    location: PIRInstLocation,
    val src: PIRValue
) : PIRRegisterOpInst(location, ERR_NEVER, PIRVoidType()) {
    override val operands: List<PIRValue> = listOf(src)

    override fun <T> accept(visitPIRor: PIRInstVisitor<T>): T = visitPIRor.visitPIRIncRefInst(this)
}

class PIRDecRefInst(
    location: PIRInstLocation,
    val src: PIRValue,
    val isXdec: Boolean = false
) : PIRRegisterOpInst(location, ERR_NEVER, PIRVoidType()) {
    override val operands: List<PIRValue> = listOf(src)

    override fun <T> accept(visitPIRor: PIRInstVisitor<T>): T = visitPIRor.visitPIRDecRefInst(this)
}

class PIRCallInst(
    location: PIRInstLocation,
    val fn: PIRFuncDecl,
    val args: List<PIRValue>,
    override val type: PIRType,
    override val errorKind: Int
) : PIRRegisterOpInst(location, errorKind, type), CommonCallInst {
    override val operands: List<PIRValue> = args

    override fun <T> accept(visitPIRor: PIRInstVisitor<T>): T = visitPIRor.visitPIRCallInst(this)
}

class PIRMethodCallInst(
    location: PIRInstLocation,
    val obj: PIRValue,
    val method: String,
    val args: List<PIRValue>,
    val receiverType: PIRType,
    override val type: PIRType,
    override val errorKind: Int
) : PIRRegisterOpInst(location, errorKind, type), CommonCallInst {
    override val operands: List<PIRValue> = args + listOf(obj)

    override fun <T> accept(visitPIRor: PIRInstVisitor<T>): T = visitPIRor.visitPIRMethodCallInst(this)
}

class PIRPrimitiveOpInst(
    location: PIRInstLocation,
    val args: List<PIRValue>,
    val desc: PIRPrimitiveDescription,
    override val type: PIRType,
    override val errorKind: Int
) : PIRRegisterOpInst(location, errorKind, type), CommonCallInst {
    override val operands: List<PIRValue> = args

    override fun <T> accept(visitPIRor: PIRInstVisitor<T>): T = visitPIRor.visitPIRPrimitiveOpInst(this)
}

class PIRCallCInst(
    location: PIRInstLocation,
    val functionName: String,
    val args: List<PIRValue>,
    override val type: PIRType,
    val steals: Any, // Either<Boolean, List<Boolean>>,
    val isBorrowed: Boolean,
    override val errorKind: Int,
    val varArgIdx: Int = -1,
    val isPure: Boolean = false,
    val returnsNull: Boolean = false,
    val dependencies: List<PIRDependency>? = null
) : PIRRegisterOpInst(location, errorKind, type), CommonCallInst {
    override val operands: List<PIRValue> = args

    override fun <T> accept(visitPIRor: PIRInstVisitor<T>): T = visitPIRor.visitPIRCallCInst(this)
}

class PIRLoadErrorValueInst(
    location: PIRInstLocation,
    override val type: PIRType,
    val isBorrowed: Boolean = false,
    val undefines: Boolean = false
) : PIRRegisterOpInst(location, ERR_NEVER, type) {
    override val operands: List<PIRValue> = emptyList()

    override fun <T> accept(visitPIRor: PIRInstVisitor<T>): T = visitPIRor.visitPIRLoadErrorValueInst(this)
}

class PIRCatchInst(
    override val location: PIRInstLocation,
    private val exceptionClass: PIRClass? = null,
    private val handler: PIRBasicBlock,
    val throwable: PIRValue,
    val throwers: List<PIRInstRef>,
    override val line: Int = -1,
    val rare: Boolean = false
) : PIRInst {
    override val type: PIRType = PIRVoidType()
    override val operands: List<PIRValue> = emptyList()
    val isVoid: Boolean = true
    override val errorKind: Int = ERR_NEVER

    override fun <T> accept(visitPIRor: PIRInstVisitor<T>): T = visitPIRor.visitPIRCatchInst(this)
}

class PIRLoadLiteralInst(
    location: PIRInstLocation,
    val value: PIRLiteralValue,
    override val type: PIRType
) : PIRRegisterOpInst(location, ERR_NEVER, type) {
    override val operands: List<PIRValue> = emptyList()

    override fun <T> accept(visitPIRor: PIRInstVisitor<T>): T = visitPIRor.visitPIRLoadLiteralInst(this)
}

class PIRGetAttrInst(
    location: PIRInstLocation,
    val obj: PIRValue,
    val attr: String,
    val classType: PIRType,
    val allowErrorValue: Boolean = false,
    override val type: PIRType,
    override val errorKind: Int,
    val isBorrowed: Boolean
) : PIRRegisterOpInst(location, errorKind, type) {
    override val operands: List<PIRValue> = listOf(obj)

    override fun <T> accept(visitPIRor: PIRInstVisitor<T>): T = visitPIRor.visitPIRGetAttrInst(this)
}

class PIRSetAttrInst(
    location: PIRInstLocation,
    val obj: PIRValue,
    val attr: String,
    val src: PIRValue,
    val classType: PIRType,
    override val type: PIRType,
    override val errorKind: Int,
    val isInit: Boolean = false
) : PIRRegisterOpInst(location, errorKind, type) {
    override val operands: List<PIRValue> = listOf(obj, src)

    override fun <T> accept(visitPIRor: PIRInstVisitor<T>): T = visitPIRor.visitPIRSetAttrInst(this)
}

class PIRLoadStaticInst(
    location: PIRInstLocation,
    override val type: PIRType,
    val identifier: String,
    val moduleName: String? = null,
    val namespace: String = NAMESPACE_STATIC,
    val ann: Any? = null
) : PIRRegisterOpInst(location, ERR_NEVER, type) {
    override val operands: List<PIRValue> = emptyList()

    override fun <T> accept(visitPIRor: PIRInstVisitor<T>): T = visitPIRor.visitPIRLoadStaticInst(this)
}

class PIRInitStaticInst(
    location: PIRInstLocation,
    val value: PIRValue,
    override val type: PIRType,
    val identifier: String,
    val moduleName: String? = null,
    val namespace: String = NAMESPACE_STATIC
) : PIRRegisterOpInst(location, ERR_NEVER, type) {
    override val operands: List<PIRValue> = listOf(value)

    override fun <T> accept(visitPIRor: PIRInstVisitor<T>): T = visitPIRor.visitPIRInitStaticInst(this)
}

class PIRTupleSetInst(
    location: PIRInstLocation,
    val items: List<PIRValue>,
    override val type: PIRType
) : PIRRegisterOpInst(location, ERR_NEVER, type) {
    override val operands: List<PIRValue> = items

    override fun <T> accept(visitPIRor: PIRInstVisitor<T>): T = visitPIRor.visitPIRTupleSetInst(this)
}

class PIRTupleGetInst(
    location: PIRInstLocation,
    val src: PIRValue,
    val index: Int,
    override val type: PIRType,
    val isBorrowed: Boolean = false
) : PIRRegisterOpInst(location, ERR_NEVER, type) {
    override val operands: List<PIRValue> = listOf(src)

    override fun <T> accept(visitPIRor: PIRInstVisitor<T>): T = visitPIRor.visitPIRTupleGetInst(this)
}

class PIRCastInst(
    location: PIRInstLocation,
    val src: PIRValue,
    override val type: PIRType,
    val isBorrowed: Boolean = false,
    val isUnchecked: Boolean = false,
    override val errorKind: Int
) : PIRRegisterOpInst(location, errorKind, type) {
    override val operands: List<PIRValue> = listOf(src)

    override fun <T> accept(visitPIRor: PIRInstVisitor<T>): T = visitPIRor.visitPIRCastInst(this)
}

class PIRBoxInst(
    location: PIRInstLocation,
    val src: PIRValue,
    override val type: PIRType
) : PIRRegisterOpInst(location, ERR_NEVER, type) {
    override val operands: List<PIRValue> = listOf(src)

    override fun <T> accept(visitPIRor: PIRInstVisitor<T>): T = visitPIRor.visitPIRBoxInst(this)
}

class PIRUnboxInst(
    location: PIRInstLocation,
    val src: PIRValue,
    override val type: PIRType,
    override val errorKind: Int
) : PIRRegisterOpInst(location, errorKind, type) {
    override val operands: List<PIRValue> = listOf(src)

    override fun <T> accept(visitPIRor: PIRInstVisitor<T>): T = visitPIRor.visitPIRUnboxInst(this)
}

class PIRRaiseStandardErrorInst(
    location: PIRInstLocation,
    val className: String,
    val value: Any?, // Either<String, PIRValue>?,
    override val type: PIRType
) : PIRRegisterOpInst(location, ERR_FALSE, type) {
    override val operands: List<PIRValue> = emptyList()

    override fun <T> accept(visitPIRor: PIRInstVisitor<T>): T = visitPIRor.visitPIRRaiseStandardErrorInst(this)
}

class PIRIntOpInst(
    location: PIRInstLocation,
    override val type: PIRType,
    val lhs: PIRValue,
    val rhs: PIRValue,
    val op: Int
) : PIRRegisterOpInst(location, ERR_NEVER, type) {
    override val operands: List<PIRValue> = listOf(lhs, rhs)

    override fun <T> accept(visitPIRor: PIRInstVisitor<T>): T = visitPIRor.visitPIRIntOpInst(this)
}

class PIRComparisonOpInst(
    location: PIRInstLocation,
    val lhs: PIRValue,
    val rhs: PIRValue,
    val op: Int
) : PIRRegisterOpInst(location, ERR_NEVER, PIRPrimitiveTypes.BITMAP) {
    override val operands: List<PIRValue> = listOf(lhs, rhs)

    override fun <T> accept(visitPIRor: PIRInstVisitor<T>): T = visitPIRor.visitPIRComparisonOpInst(this)
}

class PIRFloatOpInst(
    location: PIRInstLocation,
    val lhs: PIRValue,
    val rhs: PIRValue,
    val op: Int
) : PIRRegisterOpInst(location, ERR_NEVER, PIRPrimitiveTypes.FLOAT) {
    override val operands: List<PIRValue> = listOf(lhs, rhs)

    override fun <T> accept(visitPIRor: PIRInstVisitor<T>): T = visitPIRor.visitPIRFloatOpInst(this)
}

class PIRFloatNegInst(
    location: PIRInstLocation,
    val src: PIRValue
) : PIRRegisterOpInst(location, ERR_NEVER, PIRPrimitiveTypes.FLOAT) {
    override val operands: List<PIRValue> = listOf(src)

    override fun <T> accept(visitPIRor: PIRInstVisitor<T>): T = visitPIRor.visitPIRFloatNegInst(this)
}

class PIRFloatComparisonOpInst(
    location: PIRInstLocation,
    val lhs: PIRValue,
    val rhs: PIRValue,
    val op: Int
) : PIRRegisterOpInst(location, ERR_NEVER, PIRPrimitiveTypes.BITMAP) {
    override val operands: List<PIRValue> = listOf(lhs, rhs)

    override fun <T> accept(visitPIRor: PIRInstVisitor<T>): T = visitPIRor.visitPIRFloatComparisonOpInst(this)
}

class PIRLoadMemInst(
    location: PIRInstLocation,
    override val type: PIRType,
    val src: PIRValue,
    val isBorrowed: Boolean = false
) : PIRRegisterOpInst(location, ERR_NEVER, type) {
    override val operands: List<PIRValue> = listOf(src)

    override fun <T> accept(visitPIRor: PIRInstVisitor<T>): T = visitPIRor.visitPIRLoadMemInst(this)
}

class PIRSetMemInst(
    location: PIRInstLocation,
    val destType: PIRType,
    val dest: PIRValue,
    val src: PIRValue
) : AbstractPIRInst(location, ERR_NEVER, PIRVoidType()) {
    override val operands: List<PIRValue> = listOf(src, dest)

    override fun <T> accept(visitPIRor: PIRInstVisitor<T>): T = visitPIRor.visitPIRSetMemInst(this)
}

class PIRGetElementPtrInst(
    location: PIRInstLocation,
    val src: PIRValue,
    val srcType: PIRType,
    val field: String
) : PIRRegisterOpInst(location, ERR_NEVER, PIRPrimitiveTypes.POINTER) {
    override val operands: List<PIRValue> = listOf(src)

    override fun <T> accept(visitPIRor: PIRInstVisitor<T>): T = visitPIRor.visitPIRGetElementPtrInst(this)
}

class PIRSetElementInst(
    location: PIRInstLocation,
    val src: PIRValue,
    val field: String,
    val item: PIRValue,
    override val type: PIRType
) : PIRRegisterOpInst(location, ERR_NEVER, type) {
    override val operands: List<PIRValue> = listOf(src)

    override fun <T> accept(visitPIRor: PIRInstVisitor<T>): T = visitPIRor.visitPIRSetElementInst(this)
}

class PIRLoadAddressInst(
    location: PIRInstLocation,
    override val type: PIRType,
    val target: Any, // Either<String, Either<PIRRegister, PIRLoadStaticExpr>>
) : PIRRegisterOpInst(location, ERR_NEVER, type) {
    override val operands: List<PIRValue> = emptyList()

    override fun <T> accept(visitPIRor: PIRInstVisitor<T>): T = visitPIRor.visitPIRLoadAddressInst(this)
}

class PIRKeepAliveInst(
    location: PIRInstLocation,
    val src: List<PIRValue>
) : PIRRegisterOpInst(location, ERR_NEVER, PIRVoidType()) {
    override val operands: List<PIRValue> = src

    override fun <T> accept(visitPIRor: PIRInstVisitor<T>): T = visitPIRor.visitPIRKeepAliveInst(this)
}

class PIRUnborrowInst(
    location: PIRInstLocation,
    val src: PIRValue,
    override val type: PIRType
) : PIRRegisterOpInst(location, ERR_NEVER, type) {
    override val operands: List<PIRValue> = listOf(src)

    override fun <T> accept(visitPIRor: PIRInstVisitor<T>): T = visitPIRor.visitPIRUnborrowInst(this)
}

class PIRTruncateInst(
    location: PIRInstLocation,
    val src: PIRValue,
    override val type: PIRType
) : PIRRegisterOpInst(location, ERR_NEVER, type) {
    override val operands: List<PIRValue> = listOf(src)

    override fun <T> accept(visitPIRor: PIRInstVisitor<T>): T = visitPIRor.visitPIRTruncateInst(this)
}

class PIRExtendInst(
    location: PIRInstLocation,
    val src: PIRValue,
    override val type: PIRType,
    val signed: Boolean
) : PIRRegisterOpInst(location, ERR_NEVER, type) {
    override val operands: List<PIRValue> = listOf(src)

    override fun <T> accept(visitPIRor: PIRInstVisitor<T>): T = visitPIRor.visitPIRExtendInst(this)
}

class PIRLoadGlobalInst(
    location: PIRInstLocation,
    override val type: PIRType,
    val identifier: String,
    val ann: Any? = null
) : PIRRegisterOpInst(location, ERR_NEVER, type) {
    override val operands: List<PIRValue> = emptyList()

    override fun <T> accept(visitPIRor: PIRInstVisitor<T>): T = visitPIRor.visitPIRLoadGlobalInst(this)
}