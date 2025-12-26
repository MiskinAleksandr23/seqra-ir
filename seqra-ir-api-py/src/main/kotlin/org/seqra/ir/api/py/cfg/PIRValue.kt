package org.seqra.ir.api.py.cfg

import org.seqra.ir.api.common.cfg.*
import org.seqra.ir.api.py.PIRType
import org.seqra.ir.api.py.*
import java.util.Collections.emptyList

interface PIRValue : CommonValue, PIRExpr {
    override val type: PIRType
    override val line: Int
    override val isBorrowed: Boolean

    override val operands: List<PIRValue> get() = emptyList()
    override val typeName: String get() = type.typeName
}

data class PIRRegister(
    val name: String,
    override val type: PIRType,
    override val line: Int = -1,
    override val isBorrowed: Boolean = false
) : PIRValue {
    override val operands: List<PIRValue> = emptyList()

    override fun <T> accept(visitor: PIRExprVisitor<T>): T = visitor.visitRegister(this)

    override fun toString(): String = name
}

data class PIRInteger(
    val value: Int,
    override val type: PIRType,
    override val line: Int = -1,
    override val isBorrowed: Boolean = false
) : PIRValue {
    fun numericValue(): Int = value
    override fun <T> accept(visitor: PIRExprVisitor<T>): T = visitor.visitInteger(this)
}

data class PIRFloat(
    val value: Double,
    override val type: PIRType,
    override val line: Int = -1,
    override val isBorrowed: Boolean = false
) : PIRValue {
    override fun <T> accept(visitor: PIRExprVisitor<T>): T = visitor.visitFloat(this)
}

data class PIRCString(
    val value: ByteArray,
    override val type: PIRType,
    override val line: Int = -1,
    override val isBorrowed: Boolean = false
) : PIRValue {
    override fun <T> accept(visitor: PIRExprVisitor<T>): T = visitor.visitCString(this)

    override fun hashCode(): Int {
        var result = value.contentHashCode()
        result = 31 * result + type.hashCode()
        return result
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PIRCString

        if (!value.contentEquals(other.value)) return false
        if (type != other.type) return false

        return true
    }
}

data class PIRUndef(
    override val type: PIRType,
    override val line: Int = -1,
    override val isBorrowed: Boolean = false
) : PIRValue {
    override fun <T> accept(visitor: PIRExprVisitor<T>): T = visitor.visitUndef(this)
}

data class PIRThis(
    override val type: PIRType,
    override val line: Int = -1,
    override val isBorrowed: Boolean = false
) : PIRValue, CommonThis {
    override fun <T> accept(visitor: PIRExprVisitor<T>): T = visitor.visitThis(this)
}

data class PIRArgument(
    val index: Int,
    val name: String,
    override val type: PIRType,
    override val line: Int = -1,
    override val isBorrowed: Boolean = false
) : PIRValue, CommonArgument {
    override fun <T> accept(visitor: PIRExprVisitor<T>): T = visitor.visitArgument(this)

    companion object {
        fun of(index: Int, name: String?, type: PIRType, line: Int = -1): PIRArgument {
            return PIRArgument(index, name ?: "arg$$index", type, line)
        }
    }
}

data class PIRField(
    val name: String,
    val type: PIRType,
    val owner: String? = null,
    val isStatic: Boolean = false,
    val isFinal: Boolean = false,
    val isPublic: Boolean = true,
    val line: Int = -1
) {
    val fullName: String get() =
        if (owner != null) "$owner.$name" else name

    fun serialize(): Map<String, Any?> = mapOf(
        "name" to name,
        "type" to type.toString(),
        "owner" to owner,
        "isStatic" to isStatic,
        "isFinal" to isFinal,
        "isPublic" to isPublic,
        "line" to line
    )
}

data class PIRFieldRef(
    override val instance: PIRValue?,
    val field: PIRField,
    override val line: Int = -1,
    override val isBorrowed: Boolean = false,
    override val type: PIRType
) : PIRValue, CommonFieldRef {
    override fun <T> accept(visitor: PIRExprVisitor<T>): T = visitor.visitFieldRef(this)
}

data class PIRArrayAccess(
    override val array: PIRValue,
    override val index: PIRValue,
    override val type: PIRType,
    override val line: Int = -1,
    override val isBorrowed: Boolean = false
) : PIRValue, CommonArrayAccess {
    override fun <T> accept(visitor: PIRExprVisitor<T>): T = visitor.visitArrayAccess(this)
}