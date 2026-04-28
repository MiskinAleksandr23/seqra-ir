package org.seqra.ir.api.py.emit

import org.seqra.ir.api.py.*
import org.seqra.ir.api.py.cfg.*
import java.io.File

class PIRToPythonEmitter {

    fun emitModule(module: PIRModule): String {
        val out = StringBuilder()

        out.appendLine("from __future__ import annotations")
        out.appendLine()
        emitRuntimeHelpers(out)

        val topLevelFunctions = module.functions.filter { it.decl.className == null }

        for (fn in topLevelFunctions) {
            emitFunction(fn, out, indent = "")
            out.appendLine()
        }

        for (cls in module.classes) {
            emitClass(cls, out)
            out.appendLine()
        }

        return out.toString()
    }

    fun writeModule(module: PIRModule, file: File) {
        file.writeText(emitModule(module))
    }

    private fun emitRuntimeHelpers(out: StringBuilder) {
        out.appendLine("def __pir_is_error(x):")
        out.appendLine("    return x is None")
        out.appendLine()

        out.appendLine("def __pir_call_c(name, *args):")
        out.appendLine("    # runtime stub for low-level C call")
        out.appendLine("    return None")
        out.appendLine()

        out.appendLine("def __pir_primitive(name, *args):")
        out.appendLine("    # runtime stub for mypyc primitive op")
        out.appendLine("    raise NotImplementedError(f'primitive op not implemented: {name}')")
        out.appendLine()

        out.appendLine("def __pir_load_address(x):")
        out.appendLine("    return x")
        out.appendLine()

        out.appendLine("def __pir_load_mem(x):")
        out.appendLine("    return x")
        out.appendLine()

        out.appendLine("def __pir_set_mem(dest, src):")
        out.appendLine("    return None")
        out.appendLine()

        out.appendLine("def __pir_get_element_ptr(src, field):")
        out.appendLine("    try:")
        out.appendLine("        return getattr(src, field)")
        out.appendLine("    except Exception:")
        out.appendLine("        return None")
        out.appendLine()

        out.appendLine("def __pir_set_element(src, field, item):")
        out.appendLine("    try:")
        out.appendLine("        setattr(src, field, item)")
        out.appendLine("    except Exception:")
        out.appendLine("        pass")
        out.appendLine()

        out.appendLine("def __pir_keep_alive(*args):")
        out.appendLine("    return None")
        out.appendLine()

        out.appendLine("def __pir_raise_standard_error(class_name, value=None):")
        out.appendLine("    if value is None:")
        out.appendLine("        raise RuntimeError(class_name)")
        out.appendLine("    raise RuntimeError(f'{class_name}: {value}')")
        out.appendLine()
    }

    private fun emitClass(cls: PIRClass, out: StringBuilder) {
        val base = cls.base?.name?.takeIf { it.isNotBlank() } ?: "object"
        out.appendLine("class ${pySafeName(cls.name)}($base):")

        val methods = cls.methods.values.sortedBy { it.decl.name }

        if (methods.isEmpty()) {
            out.appendLine("    pass")
            return
        }

        out.appendLine()
        methods.forEachIndexed { idx, fn ->
            emitFunction(fn, out, indent = "    ")
            if (idx != methods.lastIndex) {
                out.appendLine()
            }
        }
    }

    private fun emitFunction(fn: PIRFunc, out: StringBuilder, indent: String) {
        when (fn.decl.kind) {
            PIR_FUNC_STATICMETHOD -> out.appendLine("${indent}@staticmethod")
            PIR_FUNC_CLASSMETHOD -> out.appendLine("${indent}@classmethod")
        }

        val params = fn.argRegs.joinToString(", ") { pyName(it.name) }
        out.appendLine("${indent}def ${pySafeName(fn.decl.name)}($params):")

        val inner = indent + "    "

        if (fn.instructions.isEmpty() || fn.blocks.isEmpty()) {
            out.appendLine("${inner}return None")
            return
        }

        val entryPc = fn.blocks.first().start.index
        out.appendLine("${inner}__pc = $entryPc")
        out.appendLine("${inner}while True:")

        val loopIndent = inner + "    "

        fn.blocks.forEachIndexed { idx, block ->
            val keyword = if (idx == 0) "if" else "elif"
            out.appendLine("${loopIndent}$keyword __pc == ${block.start.index}:")

            val blockIndent = loopIndent + "    "
            val blockInstructions = instructionsOf(fn, block)

            if (blockInstructions.isEmpty()) {
                out.appendLine("${blockIndent}return None")
            } else {
                blockInstructions.forEach { inst ->
                    emitInst(inst, out, blockIndent)
                }

                val last = blockInstructions.last()
                if (last !is PIRGotoInst &&
                    last !is PIRIfInst &&
                    last !is PIRReturnInst &&
                    last !is PIRUnreachableInst
                ) {
                    val nextBlock = nextBlock(fn, block)
                    if (nextBlock != null) {
                        out.appendLine("${blockIndent}__pc = ${nextBlock.start.index}")
                        out.appendLine("${blockIndent}continue")
                    } else {
                        out.appendLine("${blockIndent}return None")
                    }
                }
            }
        }

        out.appendLine("${loopIndent}else:")
        out.appendLine("${loopIndent}    raise RuntimeError(f'bad pc: {__pc}')")
    }

    private fun emitInst(inst: PIRInst, out: StringBuilder, indent: String) {
        when (inst) {
            is PIRAssignInst -> {
                out.appendLine("${indent}${emitValue(inst.lhv)} = ${emitExpr(inst.rhv)}")
            }

            is PIREffectInst -> {
                emitEffect(inst.effect, out, indent)
            }

            is PIRGotoInst -> {
                out.appendLine("${indent}__pc = ${inst.target.index}")
                out.appendLine("${indent}continue")
            }

            is PIRIfInst -> {
                val cond = emitCondition(inst.condition)
                val actual = if (inst.negated) "(not ($cond))" else cond

                out.appendLine("${indent}if $actual:")
                out.appendLine("${indent}    __pc = ${inst.trueBranch.index}")
                out.appendLine("${indent}else:")
                out.appendLine("${indent}    __pc = ${inst.falseBranch.index}")
                out.appendLine("${indent}continue")
            }

            is PIRReturnInst -> {
                if (inst.returnValue == null) {
                    out.appendLine("${indent}return None")
                } else {
                    out.appendLine("${indent}return ${emitValue(inst.returnValue)}")
                }
            }

            is PIRUnreachableInst -> {
                out.appendLine("${indent}raise RuntimeError('unreachable')")
            }

            else -> {
                out.appendLine("${indent}raise NotImplementedError(${quote(inst::class.simpleName ?: "UnknownInst")})")
            }
        }
    }

    private fun emitEffect(effect: PIREffectExpr, out: StringBuilder, indent: String) {
        when (effect) {
            is PIRSetAttrExpr -> {
                out.appendLine("${indent}${emitValue(effect.obj)}.${pySafeName(effect.attr)} = ${emitValue(effect.src)}")
            }

            is PIRInitStaticExpr -> {
                out.appendLine("${indent}${pySafeName(effect.identifier)} = ${emitValue(effect.value)}")
            }

            is PIRSetMemExpr -> {
                out.appendLine("${indent}__pir_set_mem(${emitValue(effect.dest)}, ${emitValue(effect.src)})")
            }

            is PIRSetElementExpr -> {
                out.appendLine("${indent}__pir_set_element(${emitValue(effect.src)}, ${quote(effect.field)}, ${emitValue(effect.item)})")
            }

            is PIRKeepAliveExpr -> {
                val args = effect.src.joinToString(", ") { emitValue(it) }
                out.appendLine("${indent}__pir_keep_alive($args)")
            }

            is PIRIncRefExpr -> {
                out.appendLine("${indent}# incref ${emitValue(effect.src)}")
            }

            is PIRDecRefExpr -> {
                out.appendLine("${indent}# decref ${emitValue(effect.src)}")
            }

            is PIRUnborrowExpr -> {
                out.appendLine("${indent}${emitValue(effect.src)}  # unborrow")
            }

            is PIRRaiseStandardErrorExpr -> {
                val valueExpr = when (val v = effect.value) {
                    null -> "None"
                    is PIRValue -> emitValue(v)
                    is String -> quote(v)
                    else -> quote(v.toString())
                }
                out.appendLine("${indent}__pir_raise_standard_error(${quote(effect.className)}, $valueExpr)")
            }

            else -> {
                out.appendLine("${indent}raise NotImplementedError(${quote(effect::class.simpleName ?: "UnknownEffect")})")
            }
        }
    }

    private fun emitCondition(expr: PIRConditionExpr): String {
        return when (expr) {
            is PIRTruthExpr -> emitValue(expr.value)
            is PIRErrorCheckExpr -> "__pir_is_error(${emitValue(expr.value)})"
            else -> emitExpr(expr)
        }
    }

    private fun emitCallC(expr: PIRCallCExpr): String {
        val args = expr.args.map(::emitValue)

        return when (expr.functionName) {
            "PyNumber_Add" ->
                if (args.size == 2) "(${args[0]} + ${args[1]})" else "__pir_call_c(${quote(expr.functionName)}, ${args.joinToString(", ")})"

            "PyNumber_Subtract" ->
                if (args.size == 2) "(${args[0]} - ${args[1]})" else "__pir_call_c(${quote(expr.functionName)}, ${args.joinToString(", ")})"

            "PyNumber_Multiply" ->
                if (args.size == 2) "(${args[0]} * ${args[1]})" else "__pir_call_c(${quote(expr.functionName)}, ${args.joinToString(", ")})"

            "PyNumber_TrueDivide" ->
                if (args.size == 2) "(${args[0]} / ${args[1]})" else "__pir_call_c(${quote(expr.functionName)}, ${args.joinToString(", ")})"

            else ->
                "__pir_call_c(${quote(expr.functionName)}${if (args.isNotEmpty()) ", ${args.joinToString(", ")}" else ""})"
        }
    }

    private fun emitExpr(expr: PIRExpr): String {
        return when (expr) {
            is PIRMoveExpr -> emitValue(expr.value)

            is PIRLiteralExpr -> emitLiteral(expr.literal.value)

            is PIRDirectCallExpr -> {
                val callee = emitCallableName(expr.funcDecl)
                val args = expr.args.joinToString(", ") { emitValue(it) }
                "$callee($args)"
            }

            is PIRMethodCallExpr -> {
                val obj = emitValue(expr.obj)
                val args = expr.args.joinToString(", ") { emitValue(it) }
                "$obj.${pySafeName(expr.method)}($args)"
            }

            is PIRPrimitiveCallExpr -> {
                val args = expr.args.joinToString(", ") { emitValue(it) }
                "__pir_primitive(${quote(expr.primitive.name)}${if (args.isNotEmpty()) ", $args" else ""})"
            }

            is PIRCallCExpr -> emitCallC(expr)

            is PIRLoadErrorValueExpr -> "None"

            is PIRGetAttrExpr -> "${emitValue(expr.obj)}.${pySafeName(expr.attr)}"

            is PIRLoadStaticExpr -> pySafeName(expr.identifier)

            is PIRTupleExpr -> {
                val items = expr.items.joinToString(", ") { emitValue(it) }
                if (expr.items.size == 1) "($items,)" else "($items)"
            }

            is PIRTupleGetExpr -> "${emitValue(expr.tuple)}[${expr.index}]"

            is PIRCastExpr -> emitValue(expr.operand)

            is PIRBoxExpr -> emitValue(expr.operand)

            is PIRUnboxExpr -> emitValue(expr.operand)

            is PIRIntBinExpr -> {
                val op = when (expr.op) {
                    PIRIntOpKind.ADD -> "+"
                    PIRIntOpKind.SUB -> "-"
                    PIRIntOpKind.MUL -> "*"
                    PIRIntOpKind.DIV -> "/"
                    PIRIntOpKind.MOD -> "%"
                    PIRIntOpKind.AND -> "&"
                    PIRIntOpKind.OR -> "|"
                    PIRIntOpKind.XOR -> "^"
                    PIRIntOpKind.SHL -> "<<"
                    PIRIntOpKind.SHR -> ">>"
                }
                "(${emitValue(expr.lhs)} $op ${emitValue(expr.rhs)})"
            }

            is PIRCmpExpr -> {
                val op = when (expr.op) {
                    PIRCmpKind.EQ -> "=="
                    PIRCmpKind.NEQ -> "!="
                    PIRCmpKind.LT -> "<"
                    PIRCmpKind.GT -> ">"
                    PIRCmpKind.LE -> "<="
                    PIRCmpKind.GE -> ">="
                    PIRCmpKind.ULT -> "<"
                    PIRCmpKind.UGT -> ">"
                    PIRCmpKind.ULE -> "<="
                    PIRCmpKind.UGE -> ">="
                }
                "(${emitValue(expr.lhs)} $op ${emitValue(expr.rhs)})"
            }

            is PIRFloatBinExpr -> {
                val op = when (expr.op) {
                    PIRFloatOpKind.ADD -> "+"
                    PIRFloatOpKind.SUB -> "-"
                    PIRFloatOpKind.MUL -> "*"
                    PIRFloatOpKind.DIV -> "/"
                    PIRFloatOpKind.MOD -> "%"
                }
                "(${emitValue(expr.lhs)} $op ${emitValue(expr.rhs)})"
            }

            is PIRFloatNegExpr -> "(-${emitValue(expr.operand)})"

            is PIRLoadMemExpr -> "__pir_load_mem(${emitValue(expr.address)})"

            is PIRGetElementPtrExpr -> "__pir_get_element_ptr(${emitValue(expr.src)}, ${quote(expr.field)})"

            is PIRLoadAddressExpr -> "__pir_load_address(${emitLoadAddressTarget(expr.target)})"

            is PIRLoadGlobalExpr -> pySafeName(expr.identifier)

            is PIRPhiExpr -> {
                if (expr.values.isEmpty()) "None" else emitValue(expr.values.first())
            }

            is PIRTruthExpr -> emitValue(expr.value)

            is PIRErrorCheckExpr -> "__pir_is_error(${emitValue(expr.value)})"

            else -> "None"
        }
    }

    private fun emitValue(value: PIRValue): String {
        return when (value) {
            is PIRRegister -> pyName(value.name)
            is PIRArgument -> pyName(value.name)
            is PIRInteger -> value.value.toString()
            is PIRFloat -> value.value.toString()
            is PIRCString -> quote(value.value.decodeToString())
            is PIRUndef -> "None"
            else -> "None"
        }
    }

    private fun emitLoadAddressTarget(target: Any): String {
        return when (target) {
            is String -> quote(target)
            is PIRRegister -> pyName(target.name)
            is PIRLoadStaticExpr -> pySafeName(target.identifier)
            else -> quote(target.toString())
        }
    }

    private fun emitCallableName(decl: PIRFuncDecl): String {
        return if (decl.className != null) {
            "${pySafeName(decl.className)}.${pySafeName(decl.name)}"
        } else {
            pySafeName(decl.name)
        }
    }

    private fun emitLiteral(value: Any?): String {
        return when (value) {
            null -> "None"
            is String -> quote(value)
            is Boolean -> if (value) "True" else "False"
            is Float -> value.toString()
            is Double -> value.toString()
            else -> value.toString()
        }
    }

    private fun instructionsOf(fn: PIRFunc, block: PIRBasicBlock): List<PIRInst> {
        return fn.instructions.filter { block.contains(it) }
    }

    private fun nextBlock(fn: PIRFunc, current: PIRBasicBlock): PIRBasicBlock? {
        val idx = fn.blocks.indexOf(current)
        return if (idx >= 0 && idx + 1 < fn.blocks.size) fn.blocks[idx + 1] else null
    }

    private fun pyName(name: String): String {
        val raw = name.ifBlank { "tmp" }
        val cleaned = raw.replace(Regex("[^A-Za-z0-9_]"), "_")
        val safe = if (cleaned.firstOrNull()?.isDigit() == true) "_$cleaned" else cleaned
        return if (safe.isBlank()) "tmp" else safe
    }

    private fun pySafeName(name: String): String = pyName(name)

    private fun quote(s: String): String {
        return "\"" + s
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\t", "\\t") + "\""
    }
}