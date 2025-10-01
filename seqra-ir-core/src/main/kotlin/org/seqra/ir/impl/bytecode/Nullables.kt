
@file:JvmName("Nullables")
package org.seqra.ir.impl.bytecode

import org.seqra.ir.api.jvm.JIRAnnotated
import org.seqra.ir.api.jvm.JIRAnnotation
import org.seqra.ir.api.jvm.JIRField
import org.seqra.ir.api.jvm.JIRMethod
import org.seqra.ir.api.jvm.JIRParameter
import org.seqra.ir.api.jvm.PredefinedPrimitives
import org.seqra.ir.api.jvm.TypeName
import kotlin.metadata.isNullable

val JIRAnnotation.isNotNullAnnotation: Boolean
    get() = NullabilityAnnotations.notNullAnnotations.any { matches(it) }

val JIRAnnotation.isNullableAnnotation: Boolean
    get() = NullabilityAnnotations.nullableAnnotations.any { matches(it) }

private object NullabilityAnnotations {
    val notNullAnnotations = listOf(
        "org.jetbrains.annotations.NotNull",
        "lombok.NonNull"
    )

    val nullableAnnotations = listOf(
        "org.jetbrains.annotations.Nullable"
    )
}

private fun JIRAnnotated.isNullable(type: TypeName): Boolean? =
    when {
        PredefinedPrimitives.matches(type.typeName) -> false
        annotations.any { it.isNotNullAnnotation } -> false
        annotations.any { it.isNullableAnnotation } -> true
        else -> null
    }

// TODO: maybe move these methods from ext into class definitions?
//  We already have many nullability-related methods there, furthermore this way we can use seqra-ir-core in implementation
val JIRField.isNullable: Boolean?
    get() = isNullable(type) ?: kmType?.isNullable

val JIRParameter.isNullable: Boolean?
    get() = isNullable(type) ?: kmType?.isNullable

val JIRMethod.isNullable: Boolean?
    get() = isNullable(returnType) ?: kmReturnType?.isNullable
