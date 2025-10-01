package org.seqra.ir.impl.types.signature

import org.seqra.ir.api.jvm.JIRAccessible
import org.seqra.ir.api.jvm.JvmType
import org.seqra.ir.api.jvm.JvmTypeParameterDeclaration

class JvmTypeParameterDeclarationImpl(
    override val symbol: String,
    override val owner: JIRAccessible,
    override val bounds: List<JvmType>? = null
) : JvmTypeParameterDeclaration {


    override fun toString(): String {
        return "$symbol : ${bounds?.joinToString { it.displayName }}"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as JvmTypeParameterDeclarationImpl

        if (symbol != other.symbol) return false
        if (owner != other.owner) return false

        return true
    }

    override fun hashCode(): Int {
        var result = symbol.hashCode()
        result = 31 * result + owner.hashCode()
        return result
    }

}
