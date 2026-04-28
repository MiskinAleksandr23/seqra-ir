package org.seqra.ir.api.py.emit

import org.seqra.ir.api.py.PIRFunc
import org.seqra.ir.api.py.PIRModule
import org.seqra.ir.api.py.cfg.*

data class FuzzSupportIssue(
    val moduleName: String,
    val functionName: String,
    val line: Int,
    val detail: String
) {
    fun render(): String {
        val lineSuffix = if (line >= 0) " line $line" else ""
        return "$moduleName.$functionName$lineSuffix: $detail"
    }
}

data class FuzzSupportReport(
    val issues: List<FuzzSupportIssue>
) {
    val isSupported: Boolean
        get() = issues.isEmpty()

    fun render(): String = issues.joinToString(separator = "\n") { it.render() }
}

object PIRFuzzSupportChecker {
    private val supportedCallCNames = setOf(
        "CPyImport_GetNativeAttrs",
        "CPyImport_ImportNative",
        "CPyTagged_Add",
        "CPyTagged_Multiply",
        "CPyTagged_Remainder",
        "CPyTagged_Rshift",
        "CPyTagged_Subtract",
        "CPyTagged_TrueDivide",
        "PyImport_Import",
        "PyNumber_Add",
        "PyNumber_Multiply",
        "PyNumber_Subtract",
        "PyNumber_TrueDivide"
    )

    private val supportedPrimitiveNames = setOf(
        "int_eq",
        "int_ge",
        "int_gt",
        "int_le",
        "int_lt",
        "int_ne"
    )

    fun analyze(module: PIRModule): FuzzSupportReport {
        val issues = mutableListOf<FuzzSupportIssue>()

        module.functions.forEach { function ->
            analyzeFunction(module.fullname, function, issues)
        }

        module.classes.forEach { cls ->
            cls.methods.values.forEach { function ->
                analyzeFunction(module.fullname, function, issues)
            }
        }

        return FuzzSupportReport(issues.distinct())
    }

    private fun analyzeFunction(
        moduleName: String,
        function: PIRFunc,
        issues: MutableList<FuzzSupportIssue>
    ) {
        function.instructions.forEach { inst ->
            when (inst) {
                is PIRAssignInst -> analyzeExpr(moduleName, function, inst.rhv, issues)
                is PIREffectInst -> analyzeExpr(moduleName, function, inst.effect, issues)
                is PIRIfInst -> analyzeExpr(moduleName, function, inst.condition, issues)
                else -> Unit
            }
        }
    }

    private fun analyzeExpr(
        moduleName: String,
        function: PIRFunc,
        expr: PIRExpr,
        issues: MutableList<FuzzSupportIssue>
    ) {
        when (expr) {
            is PIRPrimitiveCallExpr -> {
                if (expr.primitive.name !in supportedPrimitiveNames) {
                    issues += issue(moduleName, function, expr.line, "unsupported primitive op: ${expr.primitive.name}")
                }
            }

            is PIRCallCExpr -> {
                if (expr.functionName !in supportedCallCNames) {
                    issues += issue(moduleName, function, expr.line, "unsupported C call: ${expr.functionName}")
                }
            }

            is PIRLoadMemExpr -> issues += issue(moduleName, function, expr.line, "unsupported memory load")
            is PIRGetElementPtrExpr -> issues += issue(moduleName, function, expr.line, "unsupported pointer field access")
            is PIRSetMemExpr -> issues += issue(moduleName, function, expr.line, "unsupported memory write")
            is PIRSetElementExpr -> issues += issue(moduleName, function, expr.line, "unsupported low-level field write")
            else -> Unit
        }
    }

    private fun issue(moduleName: String, function: PIRFunc, line: Int, detail: String): FuzzSupportIssue {
        return FuzzSupportIssue(
            moduleName = moduleName,
            functionName = function.decl.name,
            line = line,
            detail = detail
        )
    }
}
