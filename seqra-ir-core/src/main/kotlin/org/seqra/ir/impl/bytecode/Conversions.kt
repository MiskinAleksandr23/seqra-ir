package org.seqra.ir.impl.bytecode

import org.seqra.ir.api.jvm.JIRClassOrInterface
import org.seqra.ir.api.jvm.JIRMethod
import org.seqra.ir.api.jvm.JIRMethodExtFeature
import org.seqra.ir.impl.features.JIRFeaturesChain
import org.seqra.ir.impl.types.MethodInfo

fun JIRClassOrInterface.toJIRMethod(
    methodInfo: MethodInfo,
    featuresChain: JIRFeaturesChain
): JIRMethod {
    return JIRMethodImpl(methodInfo, featuresChain, this)
}
