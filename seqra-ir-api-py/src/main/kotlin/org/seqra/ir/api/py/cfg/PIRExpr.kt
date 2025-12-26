package org.seqra.ir.api.py.cfg

import org.seqra.ir.api.common.cfg.*
import org.seqra.ir.api.py.*
import java.util.Collections.*

interface PIRExpr : CommonExpr {
    val type: PIRType
    val line: Int
    val isBorrowed: Boolean
    val operands: List<PIRValue>

    override val typeName: String
        get() = type.typeName

    fun <T> accept(visitor: PIRExprVisitor<T>): T
}

interface PIRBinaryExpr : PIRExpr {
    val lhs: PIRValue
    val rhs: PIRValue

    override val operands: List<PIRValue>
        get() = listOf(lhs, rhs)
}

data class PIRIntAddExpr(
    override val type: PIRType,
    override val lhs: PIRValue,
    override val rhs: PIRValue,
    override val line: Int = -1,
    override val isBorrowed: Boolean = false
) : PIRBinaryExpr {
    override fun <T> accept(visitor: PIRExprVisitor<T>): T = visitor.visitIntAdd(this)
}

data class PIRIntSubExpr(
    override val type: PIRType,
    override val lhs: PIRValue,
    override val rhs: PIRValue,
    override val line: Int = -1,
    override val isBorrowed: Boolean = false
) : PIRBinaryExpr {
    override fun <T> accept(visitor: PIRExprVisitor<T>): T = visitor.visitIntSub(this)
}

data class PIRIntMulExpr(
    override val type: PIRType,
    override val lhs: PIRValue,
    override val rhs: PIRValue,
    override val line: Int = -1,
    override val isBorrowed: Boolean = false
) : PIRBinaryExpr {
    override fun <T> accept(visitor: PIRExprVisitor<T>): T = visitor.visitIntMul(this)
}

data class PIRIntDivExpr(
    override val type: PIRType,
    override val lhs: PIRValue,
    override val rhs: PIRValue,
    override val line: Int = -1,
    override val isBorrowed: Boolean = false
) : PIRBinaryExpr {
    override fun <T> accept(visitor: PIRExprVisitor<T>): T = visitor.visitIntDiv(this)
}

data class PIRIntModExpr(
    override val type: PIRType,
    override val lhs: PIRValue,
    override val rhs: PIRValue,
    override val line: Int = -1,
    override val isBorrowed: Boolean = false
) : PIRBinaryExpr {
    override fun <T> accept(visitor: PIRExprVisitor<T>): T = visitor.visitIntMod(this)
}

data class PIRIntAndExpr(
    override val type: PIRType,
    override val lhs: PIRValue,
    override val rhs: PIRValue,
    override val line: Int = -1,
    override val isBorrowed: Boolean = false
) : PIRBinaryExpr {
    override fun <T> accept(visitor: PIRExprVisitor<T>): T = visitor.visitIntAnd(this)
}

data class PIRIntOrExpr(
    override val type: PIRType,
    override val lhs: PIRValue,
    override val rhs: PIRValue,
    override val line: Int = -1,
    override val isBorrowed: Boolean = false
) : PIRBinaryExpr {
    override fun <T> accept(visitor: PIRExprVisitor<T>): T = visitor.visitIntOr(this)
}

data class PIRIntXorExpr(
    override val type: PIRType,
    override val lhs: PIRValue,
    override val rhs: PIRValue,
    override val line: Int = -1,
    override val isBorrowed: Boolean = false
) : PIRBinaryExpr {
    override fun <T> accept(visitor: PIRExprVisitor<T>): T = visitor.visitIntXor(this)
}

data class PIRIntShlExpr(
    override val type: PIRType,
    override val lhs: PIRValue,
    override val rhs: PIRValue,
    override val line: Int = -1,
    override val isBorrowed: Boolean = false
) : PIRBinaryExpr {
    override fun <T> accept(visitor: PIRExprVisitor<T>): T = visitor.visitIntShl(this)
}

data class PIRIntShrExpr(
    override val type: PIRType,
    override val lhs: PIRValue,
    override val rhs: PIRValue,
    override val line: Int = -1,
    override val isBorrowed: Boolean = false
) : PIRBinaryExpr {
    override fun <T> accept(visitor: PIRExprVisitor<T>): T = visitor.visitIntShr(this)
}

interface PIRConditionExpr : PIRBinaryExpr

data class PIRIntEqExpr(
    override val type: PIRType,
    override val lhs: PIRValue,
    override val rhs: PIRValue,
    override val line: Int = -1,
    override val isBorrowed: Boolean = false
) : PIRConditionExpr {
    override fun <T> accept(visitor: PIRExprVisitor<T>): T = visitor.visitIntEq(this)
}

data class PIRIntNeExpr(
    override val type: PIRType,
    override val lhs: PIRValue,
    override val rhs: PIRValue,
    override val line: Int = -1,
    override val isBorrowed: Boolean = false
) : PIRConditionExpr {
    override fun <T> accept(visitor: PIRExprVisitor<T>): T = visitor.visitIntNe(this)
}

data class PIRIntLtExpr(
    override val type: PIRType,
    override val lhs: PIRValue,
    override val rhs: PIRValue,
    override val line: Int = -1,
    override val isBorrowed: Boolean = false
) : PIRConditionExpr {
    override fun <T> accept(visitor: PIRExprVisitor<T>): T = visitor.visitIntLt(this)
}

data class PIRIntLeExpr(
    override val type: PIRType,
    override val lhs: PIRValue,
    override val rhs: PIRValue,
    override val line: Int = -1,
    override val isBorrowed: Boolean = false
) : PIRConditionExpr {
    override fun <T> accept(visitor: PIRExprVisitor<T>): T = visitor.visitIntLe(this)
}

data class PIRIntGtExpr(
    override val type: PIRType,
    override val lhs: PIRValue,
    override val rhs: PIRValue,
    override val line: Int = -1,
    override val isBorrowed: Boolean = false
) : PIRConditionExpr {
    override fun <T> accept(visitor: PIRExprVisitor<T>): T = visitor.visitIntGt(this)
}

data class PIRIntGeExpr(
    override val type: PIRType,
    override val lhs: PIRValue,
    override val rhs: PIRValue,
    override val line: Int = -1,
    override val isBorrowed: Boolean = false
) : PIRConditionExpr {
    override fun <T> accept(visitor: PIRExprVisitor<T>): T = visitor.visitIntGe(this)
}

data class PIRFloatEqExpr(
    override val type: PIRType,
    override val lhs: PIRValue,
    override val rhs: PIRValue,
    override val line: Int = -1,
    override val isBorrowed: Boolean = false
) : PIRConditionExpr {
    override fun <T> accept(visitor: PIRExprVisitor<T>): T = visitor.visitFloatEq(this)
}

data class PIRFloatNeExpr(
    override val type: PIRType,
    override val lhs: PIRValue,
    override val rhs: PIRValue,
    override val line: Int = -1,
    override val isBorrowed: Boolean = false
) : PIRConditionExpr {
    override fun <T> accept(visitor: PIRExprVisitor<T>): T = visitor.visitFloatNe(this)
}

interface PIRUnaryExpr : PIRExpr {
    val operand: PIRValue

    override val operands: List<PIRValue>
        get() = listOf(operand)
}

data class PIRIntNegExpr(
    override val type: PIRType,
    override val operand: PIRValue,
    override val line: Int = -1,
    override val isBorrowed: Boolean = false
) : PIRUnaryExpr {
    override fun <T> accept(visitor: PIRExprVisitor<T>): T = visitor.visitIntNeg(this)
}

data class PIRFloatNegExpr(
    override val type: PIRType,
    override val operand: PIRValue,
    override val line: Int = -1,
    override val isBorrowed: Boolean = false
) : PIRUnaryExpr {
    override fun <T> accept(visitor: PIRExprVisitor<T>): T = visitor.visitFloatNeg(this)
}

data class PIRNotExpr(
    override val type: PIRType,
    override val operand: PIRValue,
    override val line: Int = -1,
    override val isBorrowed: Boolean = false
) : PIRUnaryExpr {
    override fun <T> accept(visitor: PIRExprVisitor<T>): T = visitor.visitNot(this)
}

data class PIRCastExpr(
    override val type: PIRType,
    val operand: PIRValue,
    val targetType: PIRType,
    override val line: Int = -1,
    override val isBorrowed: Boolean = false
) : PIRExpr {
    override val operands: List<PIRValue> = listOf(operand)

    override fun <T> accept(visitor: PIRExprVisitor<T>): T = visitor.visitCast(this)
}

data class PIRBoxExpr(
    override val type: PIRType,
    val operand: PIRValue,
    override val line: Int = -1,
    override val isBorrowed: Boolean = false
) : PIRExpr {
    override val operands: List<PIRValue> = listOf(operand)

    override fun <T> accept(visitor: PIRExprVisitor<T>): T = visitor.visitBox(this)
}

data class PIRUnboxExpr(
    override val type: PIRType,
    val operand: PIRValue,
    override val line: Int = -1,
    override val isBorrowed: Boolean = false
) : PIRExpr {
    override val operands: List<PIRValue> = listOf(operand)

    override fun <T> accept(visitor: PIRExprVisitor<T>): T = visitor.visitUnbox(this)
}

data class PIRGetAttrExpr(
    override val type: PIRType,
    val obj: PIRValue,
    val attr: String,
    override val line: Int = -1,
    override val isBorrowed: Boolean = false
) : PIRExpr {
    override val operands: List<PIRValue> = listOf(obj)

    override fun <T> accept(visitor: PIRExprVisitor<T>): T = visitor.visitGetAttr(this)
}

data class PIRGetElementExpr(
    override val type: PIRType,
    val struct: PIRValue,
    val field: String,
    override val line: Int = -1,
    override val isBorrowed: Boolean = false
) : PIRExpr {
    override val operands: List<PIRValue> = listOf(struct)

    override fun <T> accept(visitor: PIRExprVisitor<T>): T = visitor.visitGetElement(this)
}

data class PIRTupleGetExpr(
    override val type: PIRType,
    val tuple: PIRValue,
    val index: Int,
    override val line: Int = -1,
    override val isBorrowed: Boolean = false
) : PIRExpr {
    override val operands: List<PIRValue> = listOf(tuple)

    override fun <T> accept(visitor: PIRExprVisitor<T>): T = visitor.visitTupleGet(this)
}

data class PIRLoadMemExpr(
    override val type: PIRType,
    val address: PIRValue,
    override val line: Int = -1,
    override val isBorrowed: Boolean = false
) : PIRExpr {
    override val operands: List<PIRValue> = listOf(address)

    override fun <T> accept(visitor: PIRExprVisitor<T>): T = visitor.visitLoadMem(this)
}

data class PIRLoadAddressExpr(
    override val type: PIRType,
    val target: PIRValue,
    override val line: Int = -1,
    override val isBorrowed: Boolean = false
) : PIRExpr {
    override val operands: List<PIRValue> = listOf(target)

    override fun <T> accept(visitor: PIRExprVisitor<T>): T = visitor.visitLoadAddress(this)
}

data class PIRLoadStaticExpr(
    override val type: PIRType,
    val identifier: String,
    val namespace: String = NAMESPACE_STATIC,
    override val line: Int = -1,
    override val isBorrowed: Boolean = false
) : PIRExpr {
    override val operands: List<PIRValue> = emptyList()

    override fun <T> accept(visitor: PIRExprVisitor<T>): T = visitor.visitLoadStatic(this)
}

data class PIRLoadGlobalExpr(
    override val type: PIRType,
    val identifier: String,
    override val line: Int = -1,
    override val isBorrowed: Boolean = false
) : PIRExpr {
    override val operands: List<PIRValue> = emptyList()

    override fun <T> accept(visitor: PIRExprVisitor<T>): T = visitor.visitLoadGlobal(this)
}

interface PIRLiteralValue {
    val type: PIRType
    val value: Any
}

data class PIRLoadLiteralExpr(
    override val type: PIRType,
    val value: PIRLiteralValue,
    override val line: Int = -1,
    override val isBorrowed: Boolean = false
) : PIRExpr {
    override val operands: List<PIRValue> = emptyList()

    override fun <T> accept(visitor: PIRExprVisitor<T>): T = visitor.visitLoadLiteral(this)
}

data class PIRLoadErrorValueExpr(
    override val type: PIRType,
    override val line: Int = -1,
    override val isBorrowed: Boolean = false
) : PIRExpr {
    override val operands: List<PIRValue> = emptyList()

    override fun <T> accept(visitor: PIRExprVisitor<T>): T = visitor.visitLoadErrorValue(this)
}

data class PIRPhiExpr(
    override val type: PIRType,
    val values: List<PIRValue>,
    val blocks: List<PIRBasicBlock>,
    override val line: Int = -1,
    override val isBorrowed: Boolean = false
) : PIRExpr {
    override val operands: List<PIRValue> = values

    override fun <T> accept(visitor: PIRExprVisitor<T>): T = visitor.visitPhi(this)
}

interface PIRCallExpr : PIRExpr, CommonCallExpr {
    override val args: List<PIRValue>

    override val operands: List<PIRValue>
        get() = args
}

data class PIRDirectCallExpr(
    val funcDecl: PIRFuncDecl,
    override val args: List<PIRValue>,
    override val type: PIRType,
    override val line: Int = -1,
    override val isBorrowed: Boolean = false
) : PIRCallExpr {
    override fun <T> accept(visitor: PIRExprVisitor<T>): T = visitor.visitDirectCall(this)
}

data class PIRMethodCallExpr(
    val obj: PIRValue,
    val method: String,
    override val args: List<PIRValue>,
    override val type: PIRType,
    override val line: Int = -1,
    override val isBorrowed: Boolean = false
) : PIRCallExpr, CommonInstanceCallExpr {
    override val instance: PIRValue get() = obj

    override fun <T> accept(visitor: PIRExprVisitor<T>): T = visitor.visitMethodCall(this)
}

data class PIRPrimitiveDescription(
    val name: String,
    val returnType: PIRType
) {
    override fun toString(): String = "$name: $returnType"
}

data class PIRPrimitiveCallExpr(
    val primitive: PIRPrimitiveDescription,
    override val args: List<PIRValue>,
    override val type: PIRType,
    override val line: Int = -1,
    override val isBorrowed: Boolean = false
) : PIRCallExpr {
    override fun <T> accept(visitor: PIRExprVisitor<T>): T = visitor.visitPrimitiveCall(this)
}

data class PIRCCallExpr(
    val functionName: String,
    override val args: List<PIRValue>,
    override val type: PIRType,
    val steals: Boolean = false,
    override val line: Int = -1,
    override val isBorrowed: Boolean = false
) : PIRCallExpr {
    override fun <T> accept(visitor: PIRExprVisitor<T>): T = visitor.visitCCall(this)
}