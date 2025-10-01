package org.seqra.ir.impl.features.classpaths

import org.seqra.ir.api.jvm.JIRInstExtFeature
import org.seqra.ir.api.jvm.JIRMethod
import org.seqra.ir.api.jvm.JIRMethodExtFeature
import org.seqra.ir.api.jvm.cfg.JIRInst
import org.seqra.ir.api.jvm.cfg.JIRInstList
import org.seqra.ir.impl.analysis.impl.StringConcatSimplifierTransformer

object StringConcatSimplifier : JIRInstExtFeature, JIRMethodExtFeature {

    override fun transformInstList(method: JIRMethod, list: JIRInstList<JIRInst>): JIRInstList<JIRInst> {
        return StringConcatSimplifierTransformer(method.enclosingClass.classpath, list).transform()
    }

}
