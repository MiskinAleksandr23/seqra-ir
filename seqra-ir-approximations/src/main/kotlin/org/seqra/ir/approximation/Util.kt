package org.seqra.ir.approximation

import org.seqra.ir.api.jvm.TypeName
import org.seqra.ir.impl.cfg.util.asArray
import org.seqra.ir.impl.cfg.util.baseElementType
import org.seqra.ir.impl.cfg.util.isArray
import org.seqra.ir.impl.types.TypeNameImpl

fun String.toApproximationName() = ApproximationClassName(this)
fun String.toOriginalName() = OriginalClassName(this)

fun TypeName.eliminateApproximation(approximations: Approximations): TypeName {
    if (this.isArray) {
        val (elemType, dim) = this.baseElementType()
        val resultElemType = elemType.eliminateApproximation(approximations)
        return resultElemType.asArray(dim)
    }
    val originalClassName = approximations.findOriginalByApproximationOrNull(typeName.toApproximationName()) ?: return this
    return TypeNameImpl.fromTypeName(originalClassName)
}
