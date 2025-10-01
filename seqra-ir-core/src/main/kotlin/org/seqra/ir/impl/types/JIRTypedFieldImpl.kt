package org.seqra.ir.impl.types

import org.seqra.ir.api.jvm.JIRField
import org.seqra.ir.api.jvm.JIRRefType
import org.seqra.ir.api.jvm.JIRSubstitutor
import org.seqra.ir.api.jvm.JIRType
import org.seqra.ir.api.jvm.JIRTypedField
import org.seqra.ir.impl.bytecode.isNullable
import org.seqra.ir.api.jvm.throwClassNotFound
import org.seqra.ir.impl.bytecode.JIRAnnotationImpl
import org.seqra.ir.impl.bytecode.JIRFieldImpl
import org.seqra.ir.impl.types.signature.FieldResolutionImpl
import org.seqra.ir.impl.types.signature.FieldSignature
import kotlin.LazyThreadSafetyMode.PUBLICATION

class JIRTypedFieldImpl(
    override val enclosingType: JIRRefType,
    override val field: JIRField,
    private val substitutor: JIRSubstitutor,
) : JIRTypedField {

    override val access: Int
        get() = this.field.access

    override val name: String
        get() = this.field.name

    private val classpath = field.enclosingClass.classpath
    private val resolvedType by lazy(PUBLICATION) {
        val resolution = FieldSignature.of(field) as? FieldResolutionImpl
        resolution?.fieldType
    }

    override val type: JIRType by lazy {
        val typeName = field.type.typeName
        val rt = resolvedType
        if (rt != null) {
            classpath.typeOf(substitutor.substitute(rt))
        } else {
            val type = classpath.findTypeOrNull(field.type.typeName)?.copyWithAnnotations(
                (field as? JIRFieldImpl)?.typeAnnotationInfos?.map {
                    JIRAnnotationImpl(it, field.enclosingClass.classpath)
                } ?: listOf()
            ) ?: typeName.throwClassNotFound()
            field.isNullable?.let {
                (type as? JIRRefType)?.copyWithNullability(it)
            } ?: type
        }
    }

    // delegate identity to JIRField
    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        return other is JIRTypedFieldImpl && field == other.field
    }

    override fun hashCode(): Int = field.hashCode()
}
