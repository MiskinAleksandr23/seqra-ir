package org.seqra.ir.impl.types.substition

import org.seqra.ir.api.jvm.JIRClassOrInterface
import org.seqra.ir.api.jvm.JIRGenericsSubstitutionFeature
import org.seqra.ir.api.jvm.JIRSubstitutor
import org.seqra.ir.api.jvm.JvmType
import org.seqra.ir.api.jvm.JvmTypeParameterDeclaration
import org.seqra.ir.impl.cfg.util.OBJECT_CLASS
import org.seqra.ir.impl.features.classpaths.JIRUnknownClass
import org.seqra.ir.impl.types.signature.JvmClassRefType
import org.seqra.ir.impl.types.typeParameters

private fun List<JvmTypeParameterDeclaration>.substitute(
    parameters: List<JvmType>,
    outer: JIRSubstitutor?
): JIRSubstitutor {
    val substitution = mapIndexed { index, declaration ->
        declaration to parameters[index]
    }.toMap()
    return (outer ?: JIRSubstitutorImpl.empty).newScope(substitution)
}


object SafeSubstitution : JIRGenericsSubstitutionFeature {

    override fun substitute(
        clazz: JIRClassOrInterface,
        parameters: List<JvmType>,
        outer: JIRSubstitutor?
    ): JIRSubstitutor {
        val params = clazz.typeParameters
        return if (clazz is JIRUnknownClass) {
            ignoreProblemsAndSubstitute(params, parameters, outer)
        } else {
            require(params.size == parameters.size) {
                "Incorrect parameters specified for class ${clazz.name}: expected ${params.size} found ${parameters.size}"
            }
            params.substitute(parameters, outer)
        }
    }
}

object IgnoreSubstitutionProblems : JIRGenericsSubstitutionFeature {

    override fun substitute(
        clazz: JIRClassOrInterface,
        parameters: List<JvmType>,
        outer: JIRSubstitutor?
    ): JIRSubstitutor {
        val params = clazz.typeParameters
        return ignoreProblemsAndSubstitute(params, parameters, outer)
    }
}

private val jvmObjectType = JvmClassRefType(OBJECT_CLASS, true, emptyList())

private fun ignoreProblemsAndSubstitute(
    params: List<JvmTypeParameterDeclaration>,
    parameters: List<JvmType>,
    outer: JIRSubstitutor?
): JIRSubstitutor {
    if (params.size == parameters.size) {
        return params.substitute(parameters, outer)
    }
    val substitution = params.associateWith { it.bounds?.first() ?: jvmObjectType }
    return (outer ?: JIRSubstitutorImpl.empty).newScope(substitution)
}
