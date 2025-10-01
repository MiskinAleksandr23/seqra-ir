package org.seqra.ir.api.jvm.analysis

import org.seqra.ir.api.jvm.JIRClassOrInterface
import org.seqra.ir.api.jvm.JIRClasspath
import org.seqra.ir.api.jvm.JIRField
import org.seqra.ir.api.jvm.JIRType
import org.seqra.ir.api.jvm.cfg.JIRInst
import org.seqra.ir.api.jvm.cfg.JIRLocal
import org.seqra.ir.api.jvm.ext.objectType

class FullObjectsSet(type: JIRType) : JIRPointsToSet {

    override val possibleTypes: Set<JIRType> = setOf(type)

    override val isEmpty: Boolean
        get() = possibleTypes.isEmpty()


    override fun intersects(other: JIRPointsToSet) = false

    override val possibleStrings: Set<String>? = null
    override val possibleClasses: Set<JIRClassOrInterface>? = null
}

class PrimitivePointsAnalysis(private val classpath: JIRClasspath) : JIRPointsToAnalysis<JIRInst> {

    override fun reachingObjects(local: JIRLocal, context: JIRInst?): JIRPointsToSet {
        return FullObjectsSet(local.type)
    }

    override fun reachingObjects(field: JIRField): JIRPointsToSet {
        return FullObjectsSet(classpath.findTypeOrNull(field.type.typeName) ?: classpath.objectType)
    }

    override fun reachingObjects(set: JIRPointsToSet, field: JIRField): JIRPointsToSet {
        return reachingObjects(field)
    }

    override fun reachingObjects(local: JIRLocal, field: JIRField, context: JIRInst?): JIRPointsToSet {
        return reachingObjects(field)
    }

    override fun reachingObjectsOfArrayElement(set: JIRPointsToSet): JIRPointsToSet {
        return FullObjectsSet(classpath.objectType)
    }
}
