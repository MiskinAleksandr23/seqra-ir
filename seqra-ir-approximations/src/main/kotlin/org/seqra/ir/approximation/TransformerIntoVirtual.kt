package org.seqra.ir.approximation

import org.seqra.ir.api.jvm.JIRClassOrInterface
import org.seqra.ir.api.jvm.JIRClasspath
import org.seqra.ir.api.jvm.JIRField
import org.seqra.ir.api.jvm.JIRMethod
import org.seqra.ir.impl.features.JIRFeaturesChain

class TransformerIntoVirtual(
    private val approximations: Approximations
) {
    fun JIRClasspath.transformMethodIntoVirtual(
        to: JIRClassOrInterface,
        method: JIRMethod
    ): JIREnrichedVirtualMethod = with(method) {
        val parameters = parameters.map { param ->
            // TODO process annotations somehow to eliminate approximations
            with(param) {
                JIREnrichedVirtualParameter(index, type.eliminateApproximation(approximations), name, annotations, access)
            }
        }

        val featuresChain = features?.let { JIRFeaturesChain(it) } ?: JIRFeaturesChain(emptyList())

        val exceptions = exceptions.map { it.eliminateApproximation(approximations) }

        val methodNode = withAsmNode { it } // Safe since used under synchronization in JIREnrichedVirtualMethod

        (EnrichedVirtualMethodBuilder()
            .name(name)
            .access(access)
            .returnType(returnType.eliminateApproximation(approximations).typeName) as EnrichedVirtualMethodBuilder)
            .enrichedParameters(parameters)
            .featuresChain(featuresChain)
            .exceptions(exceptions)
            .annotations(annotations)
            .asmNode(methodNode)
            .build()
            .also { it.bind(to) }
    }

    fun transformIntoVirtualField(
        to: JIRClassOrInterface,
        field: JIRField
    ): JIREnrichedVirtualField = with(field) {
        (EnrichedVirtualFieldBuilder()
            .name(name)
            .type(type.eliminateApproximation(approximations).typeName)
            .access(access) as EnrichedVirtualFieldBuilder)
            .annotations(annotations)
            .build()
            .also { it.bind(to) }
    }
}
