package org.seqra.ir.impl.bytecode

import org.seqra.ir.api.jvm.JIRAnnotation
import org.seqra.ir.api.jvm.JIRDeclaration
import org.seqra.ir.api.jvm.JIRMethod
import org.seqra.ir.api.jvm.JIRParameter
import org.seqra.ir.api.jvm.TypeName
import org.seqra.ir.impl.types.ParameterInfo
import org.seqra.ir.impl.types.TypeNameImpl

class JIRParameterImpl(
    override val method: JIRMethod,
    private val info: ParameterInfo
) : JIRParameter {

    override val access: Int
        get() = info.access

    override val name: String? by lazy {
        info.name ?: kmParameter?.name
    }

    override val index: Int
        get() = info.index

    override val declaration: JIRDeclaration
        get() = JIRDeclarationImpl.of(method.enclosingClass.declaration.location, this)

    override val annotations: List<JIRAnnotation>
        get() = info.annotations.map { JIRAnnotationImpl(it, method.enclosingClass.classpath) }

    override val type: TypeName
        get() = TypeNameImpl.fromTypeName(info.type)

    override fun toString(): String {
        return "$method $name"
    }

}
