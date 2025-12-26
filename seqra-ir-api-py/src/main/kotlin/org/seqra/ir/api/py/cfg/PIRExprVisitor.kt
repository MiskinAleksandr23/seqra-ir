package org.seqra.ir.api.py.cfg

interface PIRExprVisitor<T> {
    fun visitIntAdd(expr: PIRIntAddExpr): T
    fun visitIntSub(expr: PIRIntSubExpr): T
    fun visitIntMul(expr: PIRIntMulExpr): T
    fun visitIntDiv(expr: PIRIntDivExpr): T
    fun visitIntMod(expr: PIRIntModExpr): T
    fun visitIntAnd(expr: PIRIntAndExpr): T
    fun visitIntOr(expr: PIRIntOrExpr): T
    fun visitIntXor(expr: PIRIntXorExpr): T
    fun visitIntShl(expr: PIRIntShlExpr): T
    fun visitIntShr(expr: PIRIntShrExpr): T
    fun visitIntEq(expr: PIRIntEqExpr): T
    fun visitIntNe(expr: PIRIntNeExpr): T
    fun visitIntLt(expr: PIRIntLtExpr): T
    fun visitIntLe(expr: PIRIntLeExpr): T
    fun visitIntGt(expr: PIRIntGtExpr): T
    fun visitIntGe(expr: PIRIntGeExpr): T
    fun visitFloatEq(expr: PIRFloatEqExpr): T
    fun visitFloatNe(expr: PIRFloatNeExpr): T
    fun visitIntNeg(expr: PIRIntNegExpr): T
    fun visitFloatNeg(expr: PIRFloatNegExpr): T
    fun visitNot(expr: PIRNotExpr): T
    fun visitCast(expr: PIRCastExpr): T
    fun visitBox(expr: PIRBoxExpr): T
    fun visitUnbox(expr: PIRUnboxExpr): T
    fun visitGetAttr(expr: PIRGetAttrExpr): T
    fun visitGetElement(expr: PIRGetElementExpr): T
    fun visitTupleGet(expr: PIRTupleGetExpr): T
    fun visitLoadMem(expr: PIRLoadMemExpr): T
    fun visitLoadAddress(expr: PIRLoadAddressExpr): T
    fun visitLoadStatic(expr: PIRLoadStaticExpr): T
    fun visitLoadGlobal(expr: PIRLoadGlobalExpr): T
    fun visitLoadLiteral(expr: PIRLoadLiteralExpr): T
    fun visitLoadErrorValue(expr: PIRLoadErrorValueExpr): T
    fun visitPhi(expr: PIRPhiExpr): T
    fun visitDirectCall(expr: PIRDirectCallExpr): T
    fun visitMethodCall(expr: PIRMethodCallExpr): T
    fun visitPrimitiveCall(expr: PIRPrimitiveCallExpr): T
    fun visitCCall(expr: PIRCCallExpr): T

    fun visitRegister(expr: PIRRegister): T
    fun visitInteger(expr: PIRInteger): T
    fun visitFloat(expr: PIRFloat): T
    fun visitCString(expr: PIRCString): T
    fun visitUndef(expr: PIRUndef): T
    fun visitThis(expr: PIRThis): T
    fun visitArgument(expr: PIRArgument): T
    fun visitFieldRef(expr: PIRFieldRef): T
    fun visitArrayAccess(expr: PIRArrayAccess): T
}