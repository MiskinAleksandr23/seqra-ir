package org.seqra.ir.api.common.cfg

import org.seqra.ir.api.common.CommonMethod

interface CommonInst {
    val location: CommonInstLocation
}

interface CommonInstLocation {
    val method: CommonMethod
}

interface CommonAssignInst : CommonInst {
    val lhv: CommonValue
    val rhv: CommonExpr
}

interface CommonCallInst : CommonInst

interface CommonReturnInst : CommonInst {
    val returnValue: CommonValue?
}

interface CommonGotoInst : CommonInst

interface CommonIfInst : CommonInst
