package org.seqra.ir.api.py.grpc

import ir.*
import org.seqra.ir.api.py.PIRClass
import org.seqra.ir.api.py.PIRFunc
import org.seqra.ir.api.py.PIRFuncDecl
import org.seqra.ir.api.py.PIRFuncSignature
import org.seqra.ir.api.py.PIRModule
import org.seqra.ir.api.py.PIRPrimitiveTypes
import org.seqra.ir.api.py.cfg.PIRArgument
import org.seqra.ir.api.py.cfg.PIRAssignInst
import org.seqra.ir.api.py.cfg.PIRBasicBlock
import org.seqra.ir.api.py.cfg.PIRCmpExpr
import org.seqra.ir.api.py.cfg.PIRCmpKind
import org.seqra.ir.api.py.cfg.PIRGotoInst
import org.seqra.ir.api.py.cfg.PIRIfInst
import org.seqra.ir.api.py.cfg.PIRInst
import org.seqra.ir.api.py.cfg.PIRInstLocation
import org.seqra.ir.api.py.cfg.PIRInstRef
import org.seqra.ir.api.py.cfg.PIRIntBinExpr
import org.seqra.ir.api.py.cfg.PIRIntOpKind
import org.seqra.ir.api.py.cfg.PIRInteger
import org.seqra.ir.api.py.cfg.PIRPhiExpr
import org.seqra.ir.api.py.cfg.PIRRegister
import org.seqra.ir.api.py.cfg.PIRReturnInst
import org.seqra.ir.api.py.emit.EmitMode
import org.seqra.ir.api.py.emit.EmitOptions
import org.seqra.ir.api.py.emit.PIRFuzzSupportChecker
import org.seqra.ir.api.py.emit.PIRToPythonEmitter
import org.seqra.ir.api.py.mapper.ProtoToPirMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files

class MapperEmitterTest {

    @Test
    fun `maps proto response and emits python function`() {
        val intType = RType.newBuilder()
            .setName("builtins.int")
            .setRprimitive(RPrimitive.newBuilder().build())
            .build()

        val x = Register.newBuilder()
            .setName("x")
            .setType(intType)
            .setIsArg(true)
            .build()

        val y = Register.newBuilder()
            .setName("y")
            .setType(intType)
            .setIsArg(true)
            .build()

        val tmp = Register.newBuilder()
            .setName("tmp")
            .setType(intType)
            .build()

        val addOp = Op.newBuilder()
            .setName("int_op")
            .setValue(
                Value.newBuilder()
                    .setType(intType)
                    .setRegister(tmp)
                    .build()
            )
            .setRegisterOp(
                RegisterOp.newBuilder()
                    .setIntOp(
                        IntOp.newBuilder()
                            .setType(intType)
                            .setLhs(Value.newBuilder().setType(intType).setRegister(x).build())
                            .setRhs(Value.newBuilder().setType(intType).setRegister(y).build())
                            .setOp(IntOp.OpType.ADD)
                            .build()
                    )
                    .build()
            )
            .build()

        val returnOp = Op.newBuilder()
            .setName("return")
            .setControlOp(
                ControlOp.newBuilder()
                    .setReturn(
                        Return.newBuilder()
                            .setValue(
                                Value.newBuilder()
                                    .setType(intType)
                                    .setRegister(tmp)
                                    .build()
                            )
                            .build()
                    )
                    .build()
            )
            .build()

        val function = ir.Function.newBuilder()
            .setDecl(
                FuncDecl.newBuilder()
                    .setName("add")
                    .setModuleName("sample")
                    .setKind(FunctionKind.FUNC_NORMAL)
                    .setSig(
                        FuncSignature.newBuilder()
                            .addArgs(intType)
                            .addArgs(intType)
                            .setRetType(intType)
                            .build()
                    )
                    .build()
            )
            .addArgRegs(x)
            .addArgRegs(y)
            .addBlocks(BasicBlock.newBuilder().setLabel(0).addOps(addOp).addOps(returnOp))
            .build()

        val module = ir.Module.newBuilder()
            .setFullname("sample")
            .addFunctions(function)
            .build()

        val moduleResponse = ModuleResponse.newBuilder()
            .setSuccess(true)
            .addModules(module)
            .build()

        val classResponse = ClassResponse.newBuilder()
            .setSuccess(true)
            .build()

        val cfgResponse = CFGResponse.newBuilder()
            .setSuccess(true)
            .build()

        val response = CompleteResponse.newBuilder()
            .setSuccess(true)
            .setModules(moduleResponse)
            .setClasses(classResponse)
            .setCfgs(cfgResponse)
            .build()

        val pirModule = ProtoToPirMapper().mapComplete(response).single()
        val emitted = PIRToPythonEmitter().emitModule(pirModule)

        assertEquals("sample", pirModule.fullname)
        assertTrue(emitted.contains("# emitter mode: fuzz"))
        assertTrue(emitted.contains("def add(x, y):"))
        assertTrue(emitted.contains("tmp = (x + y)"))
        assertTrue(emitted.contains("return tmp"))
    }

    @Test
    fun `supports explicit debug emitter mode`() {
        val module = org.seqra.ir.api.py.PIRModule(
            fullname = "sample",
            imports = emptyList(),
            functions = emptyList(),
            classes = emptyList(),
            finalNames = emptyList()
        )

        val emitted = PIRToPythonEmitter(
            EmitOptions(mode = EmitMode.DEBUG_IR)
        ).emitModule(module)

        assertTrue(emitted.contains("# emitter mode: debug-ir"))
    }

    @Test
    fun `emits dedicated sentinel for load error value`() {
        val intType = RType.newBuilder()
            .setName("builtins.int")
            .setRprimitive(RPrimitive.newBuilder().build())
            .build()

        val tmp = Register.newBuilder()
            .setName("tmp")
            .setType(intType)
            .build()

        val loadErrorOp = Op.newBuilder()
            .setName("load_error_value")
            .setValue(
                Value.newBuilder()
                    .setType(intType)
                    .setRegister(tmp)
                    .build()
            )
            .setRegisterOp(
                RegisterOp.newBuilder()
                    .setLoadErrorValue(
                        LoadErrorValue.newBuilder()
                            .setType(intType)
                            .setUndefines(true)
                            .build()
                    )
                    .build()
            )
            .build()

        val returnOp = Op.newBuilder()
            .setName("return")
            .setControlOp(
                ControlOp.newBuilder()
                    .setReturn(
                        Return.newBuilder()
                            .setValue(
                                Value.newBuilder()
                                    .setType(intType)
                                    .setRegister(tmp)
                                    .build()
                            )
                            .build()
                    )
                    .build()
            )
            .build()

        val function = ir.Function.newBuilder()
            .setDecl(
                FuncDecl.newBuilder()
                    .setName("f")
                    .setModuleName("sample")
                    .setKind(FunctionKind.FUNC_NORMAL)
                    .setSig(
                        FuncSignature.newBuilder()
                            .setRetType(intType)
                            .build()
                    )
                    .build()
            )
            .addBlocks(BasicBlock.newBuilder().setLabel(0).addOps(loadErrorOp).addOps(returnOp))
            .build()

        val response = CompleteResponse.newBuilder()
            .setSuccess(true)
            .setModules(
                ModuleResponse.newBuilder()
                    .setSuccess(true)
                    .addModules(
                        ir.Module.newBuilder()
                            .setFullname("sample")
                            .addFunctions(function)
                            .build()
                    )
                    .build()
            )
            .setClasses(ClassResponse.newBuilder().setSuccess(true).build())
            .setCfgs(CFGResponse.newBuilder().setSuccess(true).build())
            .build()

        val emitted = PIRToPythonEmitter().emitModule(ProtoToPirMapper().mapComplete(response).single())

        assertTrue(emitted.contains("__PIR_ERROR = object()"))
        assertTrue(emitted.contains("return x is __PIR_ERROR"))
        assertTrue(emitted.contains("tmp = __PIR_ERROR"))
    }

    @Test
    fun `emits module import prelude and top level init`() {
        val module = org.seqra.ir.api.py.PIRModule(
            fullname = "sample",
            imports = listOf("builtins", "helper"),
            functions = emptyList(),
            classes = emptyList(),
            finalNames = emptyList()
        )

        val emitted = PIRToPythonEmitter().emitModule(module)

        assertTrue(emitted.contains("import builtins"))
        assertTrue(emitted.contains("helper = __pir_import_module(\"helper_generated\", \"helper\")"))
        assertTrue(emitted.contains("def __pir_init_module():"))
        assertTrue(emitted.contains("__pir_init_module()"))
    }

    @Test
    fun `emits direct semantics for tagged add call c`() {
        val intType = RType.newBuilder()
            .setName("builtins.int")
            .setRprimitive(RPrimitive.newBuilder().build())
            .build()
        val shortIntType = RType.newBuilder()
            .setName("short_int")
            .setIsUnboxed(true)
            .setCUndefined("CPY_INT_TAG")
            .setCtype("CPyTagged")
            .setRprimitive(RPrimitive.newBuilder().build())
            .build()

        val x = Register.newBuilder()
            .setName("x")
            .setType(intType)
            .setIsArg(true)
            .build()

        val tmp = Register.newBuilder()
            .setName("tmp")
            .setType(intType)
            .build()

        val callCOp = Op.newBuilder()
            .setName("call_c")
            .setValue(
                Value.newBuilder()
                    .setType(intType)
                    .setRegister(tmp)
                    .build()
            )
            .setRegisterOp(
                RegisterOp.newBuilder()
                    .setCallC(
                        CallC.newBuilder()
                            .setFunctionName("CPyTagged_Add")
                            .addArgs(Value.newBuilder().setType(intType).setRegister(x).build())
                            .addArgs(
                                Value.newBuilder()
                                    .setType(shortIntType)
                                    .setInteger(
                                        Integer.newBuilder()
                                            .setValue(2)
                                            .setType(shortIntType)
                                            .build()
                                    )
                                    .build()
                            )
                            .setType(intType)
                            .build()
                    )
                    .build()
            )
            .build()

        val returnOp = Op.newBuilder()
            .setName("return")
            .setControlOp(
                ControlOp.newBuilder()
                    .setReturn(
                        Return.newBuilder()
                            .setValue(
                                Value.newBuilder()
                                    .setType(intType)
                                    .setRegister(tmp)
                                    .build()
                            )
                            .build()
                    )
                    .build()
            )
            .build()

        val function = ir.Function.newBuilder()
            .setDecl(
                FuncDecl.newBuilder()
                    .setName("inc")
                    .setModuleName("sample")
                    .setKind(FunctionKind.FUNC_NORMAL)
                    .setSig(
                        FuncSignature.newBuilder()
                            .addArgs(intType)
                            .setRetType(intType)
                            .build()
                    )
                    .build()
            )
            .addArgRegs(x)
            .addBlocks(BasicBlock.newBuilder().setLabel(0).addOps(callCOp).addOps(returnOp))
            .build()

        val response = CompleteResponse.newBuilder()
            .setSuccess(true)
            .setModules(
                ModuleResponse.newBuilder()
                    .setSuccess(true)
                    .addModules(
                        ir.Module.newBuilder()
                            .setFullname("sample")
                            .addFunctions(function)
                            .build()
                    )
                    .build()
            )
            .setClasses(ClassResponse.newBuilder().setSuccess(true).build())
            .setCfgs(CFGResponse.newBuilder().setSuccess(true).build())
            .build()

        val emitted = PIRToPythonEmitter().emitModule(ProtoToPirMapper().mapComplete(response).single())

        assertTrue(emitted.contains("tmp = (x + 1)"))
    }

    @Test
    fun `emits direct semantics for int gt primitive`() {
        val intType = RType.newBuilder()
            .setName("builtins.int")
            .setRprimitive(RPrimitive.newBuilder().build())
            .build()
        val shortIntType = RType.newBuilder()
            .setName("short_int")
            .setIsUnboxed(true)
            .setCUndefined("CPY_INT_TAG")
            .setCtype("CPyTagged")
            .setRprimitive(RPrimitive.newBuilder().build())
            .build()

        val bitType = RType.newBuilder()
            .setName("bit")
            .setIsUnboxed(true)
            .setCUndefined("2")
            .setCtype("char")
            .setRprimitive(
                RPrimitive.newBuilder()
                    .setSize(1)
                    .setMayBeImmortal(true)
                    .build()
            )
            .build()

        val x = Register.newBuilder()
            .setName("x")
            .setType(intType)
            .setIsArg(true)
            .build()

        val tmp = Register.newBuilder()
            .setName("tmp")
            .setType(bitType)
            .build()

        val primitiveOp = Op.newBuilder()
            .setName("primitive_op")
            .setValue(
                Value.newBuilder()
                    .setType(bitType)
                    .setRegister(tmp)
                    .build()
            )
            .setRegisterOp(
                RegisterOp.newBuilder()
                    .setPrimitiveOp(
                        PrimitiveOp.newBuilder()
                            .addArgs(Value.newBuilder().setType(intType).setRegister(x).build())
                            .addArgs(
                                Value.newBuilder()
                                    .setType(shortIntType)
                                    .setInteger(
                                        Integer.newBuilder()
                                            .setValue(10)
                                            .setType(shortIntType)
                                            .build()
                                    )
                                    .build()
                            )
                            .setType(bitType)
                            .setDesc(
                                PrimitiveDescription.newBuilder()
                                    .setName("int_gt")
                                    .setReturnType(bitType)
                                    .build()
                            )
                            .build()
                    )
                    .build()
            )
            .build()

        val returnOp = Op.newBuilder()
            .setName("return")
            .setControlOp(
                ControlOp.newBuilder()
                    .setReturn(
                        Return.newBuilder()
                            .setValue(
                                Value.newBuilder()
                                    .setType(bitType)
                                    .setRegister(tmp)
                                    .build()
                            )
                            .build()
                    )
                    .build()
            )
            .build()

        val module = ProtoToPirMapper().mapComplete(
            CompleteResponse.newBuilder()
                .setSuccess(true)
                .setModules(
                    ModuleResponse.newBuilder()
                        .setSuccess(true)
                        .addModules(
                            ir.Module.newBuilder()
                                .setFullname("sample")
                                .addFunctions(
                                    ir.Function.newBuilder()
                                        .setDecl(
                                            FuncDecl.newBuilder()
                                                .setName("gt")
                                                .setModuleName("sample")
                                                .setKind(FunctionKind.FUNC_NORMAL)
                                                .setSig(
                                                    FuncSignature.newBuilder()
                                                        .addArgs(intType)
                                                        .setRetType(bitType)
                                                        .build()
                                                )
                                                .build()
                                        )
                                        .addArgRegs(x)
                                        .addBlocks(BasicBlock.newBuilder().setLabel(0).addOps(primitiveOp).addOps(returnOp))
                                        .build()
                                )
                                .build()
                        )
                        .build()
                )
                .setClasses(ClassResponse.newBuilder().setSuccess(true).build())
                .setCfgs(CFGResponse.newBuilder().setSuccess(true).build())
                .build()
        ).single()

        val emitted = PIRToPythonEmitter().emitModule(module)

        assertTrue(emitted.contains("tmp = (x > 5)"))
    }

    @Test
    fun `reports unsupported primitive op for fuzz mode`() {
        val intType = RType.newBuilder()
            .setName("builtins.int")
            .setRprimitive(RPrimitive.newBuilder().build())
            .build()

        val tmp = Register.newBuilder()
            .setName("tmp")
            .setType(intType)
            .build()

        val primitiveOp = Op.newBuilder()
            .setName("primitive_op")
            .setValue(
                Value.newBuilder()
                    .setType(intType)
                    .setRegister(tmp)
                    .build()
            )
            .setRegisterOp(
                RegisterOp.newBuilder()
                    .setPrimitiveOp(
                        PrimitiveOp.newBuilder()
                            .setType(intType)
                            .setDesc(
                                PrimitiveDescription.newBuilder()
                                    .setName("unsupported_primitive")
                                    .setReturnType(intType)
                                    .build()
                            )
                            .build()
                    )
                    .build()
            )
            .build()

        val returnOp = Op.newBuilder()
            .setName("return")
            .setControlOp(
                ControlOp.newBuilder()
                    .setReturn(
                        Return.newBuilder()
                            .setValue(
                                Value.newBuilder()
                                    .setType(intType)
                                    .setRegister(tmp)
                                    .build()
                            )
                            .build()
                    )
                    .build()
            )
            .build()

        val function = ir.Function.newBuilder()
            .setDecl(
                FuncDecl.newBuilder()
                    .setName("f")
                    .setModuleName("sample")
                    .setKind(FunctionKind.FUNC_NORMAL)
                    .setSig(FuncSignature.newBuilder().setRetType(intType).build())
                    .build()
            )
            .addBlocks(BasicBlock.newBuilder().setLabel(0).addOps(primitiveOp).addOps(returnOp))
            .build()

        val module = ProtoToPirMapper().mapComplete(
            CompleteResponse.newBuilder()
                .setSuccess(true)
                .setModules(
                    ModuleResponse.newBuilder()
                        .setSuccess(true)
                        .addModules(
                            ir.Module.newBuilder()
                                .setFullname("sample")
                                .addFunctions(function)
                                .build()
                        )
                        .build()
                )
                .setClasses(ClassResponse.newBuilder().setSuccess(true).build())
                .setCfgs(CFGResponse.newBuilder().setSuccess(true).build())
                .build()
        ).single()

        val report = PIRFuzzSupportChecker.analyze(module)
        assertTrue(report.render().contains("unsupported primitive op: unsupported_primitive"))

        val error = assertThrows(IllegalArgumentException::class.java) {
            PIRToPythonEmitter().emitModule(module)
        }
        assertTrue(error.message!!.contains("Unsupported in fuzz mode"))
    }

    @Test
    fun `can emit unsupported module when fail on unsupported is disabled`() {
        val intType = RType.newBuilder()
            .setName("builtins.int")
            .setRprimitive(RPrimitive.newBuilder().build())
            .build()

        val tmp = Register.newBuilder()
            .setName("tmp")
            .setType(intType)
            .build()

        val primitiveOp = Op.newBuilder()
            .setName("primitive_op")
            .setValue(
                Value.newBuilder()
                    .setType(intType)
                    .setRegister(tmp)
                    .build()
            )
            .setRegisterOp(
                RegisterOp.newBuilder()
                    .setPrimitiveOp(
                        PrimitiveOp.newBuilder()
                            .setType(intType)
                            .setDesc(
                                PrimitiveDescription.newBuilder()
                                    .setName("unsupported_primitive")
                                    .setReturnType(intType)
                                    .build()
                            )
                            .build()
                    )
                    .build()
            )
            .build()

        val returnOp = Op.newBuilder()
            .setName("return")
            .setControlOp(
                ControlOp.newBuilder()
                    .setReturn(
                        Return.newBuilder()
                            .setValue(
                                Value.newBuilder()
                                    .setType(intType)
                                    .setRegister(tmp)
                                    .build()
                            )
                            .build()
                    )
                    .build()
            )
            .build()

        val module = ProtoToPirMapper().mapComplete(
            CompleteResponse.newBuilder()
                .setSuccess(true)
                .setModules(
                    ModuleResponse.newBuilder()
                        .setSuccess(true)
                        .addModules(
                            ir.Module.newBuilder()
                                .setFullname("sample")
                                .addFunctions(
                                    ir.Function.newBuilder()
                                        .setDecl(
                                            FuncDecl.newBuilder()
                                                .setName("f")
                                                .setModuleName("sample")
                                                .setKind(FunctionKind.FUNC_NORMAL)
                                                .setSig(FuncSignature.newBuilder().setRetType(intType).build())
                                                .build()
                                        )
                                        .addBlocks(BasicBlock.newBuilder().setLabel(0).addOps(primitiveOp).addOps(returnOp))
                                        .build()
                                )
                                .build()
                        )
                        .build()
                )
                .setClasses(ClassResponse.newBuilder().setSuccess(true).build())
                .setCfgs(CFGResponse.newBuilder().setSuccess(true).build())
                .build()
        ).single()

        val emitted = PIRToPythonEmitter(
            EmitOptions(mode = EmitMode.FUZZ, failOnUnsupported = false)
        ).emitModule(module)

        assertTrue(emitted.contains("__pir_primitive(\"unsupported_primitive\")"))
    }

    @Test
    fun `supports phi merge semantics in fuzz mode`() {
        val intType = PIRPrimitiveTypes.INT
        val x = PIRRegister("x", intType, isArg = true)
        val yThen = PIRRegister("y_then", intType)
        val yElse = PIRRegister("y_else", intType)
        val y = PIRRegister("y", intType)

        val function = buildSyntheticFunction(
            moduleName = "sample",
            functionName = "main",
            argRegs = listOf(x),
            blockRanges = listOf(0..0, 1..2, 3..4, 5..6)
        ) { methodProvider ->
            listOf(
                PIRIfInst(
                    location = loc(methodProvider, 0, 1),
                    condition = PIRCmpExpr(
                        lhs = x,
                        rhs = PIRInteger(0, intType),
                        op = PIRCmpKind.GT,
                        type = PIRPrimitiveTypes.BOOL,
                        line = 1
                    ),
                    trueBranch = PIRInstRef(1),
                    falseBranch = PIRInstRef(3)
                ),
                PIRAssignInst(
                    location = loc(methodProvider, 1, 2),
                    lhv = yThen,
                    rhv = PIRIntBinExpr(
                        lhs = x,
                        rhs = PIRInteger(1, intType),
                        op = PIRIntOpKind.ADD,
                        type = intType,
                        line = 2
                    )
                ),
                PIRGotoInst(loc(methodProvider, 2, 2), PIRInstRef(5)),
                PIRAssignInst(
                    location = loc(methodProvider, 3, 4),
                    lhv = yElse,
                    rhv = PIRIntBinExpr(
                        lhs = x,
                        rhs = PIRInteger(2, intType),
                        op = PIRIntOpKind.ADD,
                        type = intType,
                        line = 4
                    )
                ),
                PIRGotoInst(loc(methodProvider, 4, 4), PIRInstRef(5)),
                PIRAssignInst(
                    location = loc(methodProvider, 5, 5),
                    lhv = y,
                    rhv = PIRPhiExpr(
                        type = intType,
                        values = listOf(yThen, yElse),
                        line = 5
                    )
                ),
                PIRReturnInst(loc(methodProvider, 6, 6), y)
            )
        }

        val module = PIRModule(
            fullname = "sample",
            imports = emptyList(),
            functions = listOf(function),
            classes = emptyList(),
            finalNames = emptyList()
        )

        val report = PIRFuzzSupportChecker.analyze(module)
        assertTrue(report.isSupported)

        val emitted = PIRToPythonEmitter().emitModule(module)
        assertTrue(emitted.contains("__prev_pc = None"))
        assertTrue(emitted.contains("y = (y_then if __prev_pc == 1 else (y_else if __prev_pc == 3 else __pir_bad_phi(__prev_pc, __pc)))"))

        assertEquals("6", runGeneratedFunction(module, "main", "[5]"))
        assertEquals("-3", runGeneratedFunction(module, "main", "[-5]"))
    }

    @Test
    fun `supports phi loop header semantics in fuzz mode`() {
        val intType = PIRPrimitiveTypes.INT
        val init = PIRRegister("init", intType)
        val loopValue = PIRRegister("loop_value", intType)
        val nextValue = PIRRegister("next_value", intType)

        val function = buildSyntheticFunction(
            moduleName = "loop_sample",
            functionName = "main",
            argRegs = emptyList(),
            blockRanges = listOf(0..1, 2..3, 4..5, 6..6)
        ) { methodProvider ->
            listOf(
                PIRAssignInst(
                    location = loc(methodProvider, 0, 1),
                    lhv = init,
                    rhv = PIRIntBinExpr(
                        lhs = PIRInteger(0, intType),
                        rhs = PIRInteger(0, intType),
                        op = PIRIntOpKind.ADD,
                        type = intType,
                        line = 1
                    )
                ),
                PIRGotoInst(loc(methodProvider, 1, 1), PIRInstRef(2)),
                PIRAssignInst(
                    location = loc(methodProvider, 2, 2),
                    lhv = loopValue,
                    rhv = PIRPhiExpr(
                        type = intType,
                        values = listOf(init, nextValue),
                        line = 2
                    )
                ),
                PIRIfInst(
                    location = loc(methodProvider, 3, 2),
                    condition = PIRCmpExpr(
                        lhs = loopValue,
                        rhs = PIRInteger(3, intType),
                        op = PIRCmpKind.LT,
                        type = PIRPrimitiveTypes.BOOL,
                        line = 2
                    ),
                    trueBranch = PIRInstRef(4),
                    falseBranch = PIRInstRef(6)
                ),
                PIRAssignInst(
                    location = loc(methodProvider, 4, 3),
                    lhv = nextValue,
                    rhv = PIRIntBinExpr(
                        lhs = loopValue,
                        rhs = PIRInteger(1, intType),
                        op = PIRIntOpKind.ADD,
                        type = intType,
                        line = 3
                    )
                ),
                PIRGotoInst(loc(methodProvider, 5, 3), PIRInstRef(2)),
                PIRReturnInst(loc(methodProvider, 6, 4), loopValue)
            )
        }

        val module = PIRModule(
            fullname = "loop_sample",
            imports = emptyList(),
            functions = listOf(function),
            classes = emptyList(),
            finalNames = emptyList()
        )

        val emitted = PIRToPythonEmitter().emitModule(module)
        assertTrue(emitted.contains("loop_value = (init if __prev_pc == 0 else (next_value if __prev_pc == 4 else __pir_bad_phi(__prev_pc, __pc)))"))
        assertEquals("3", runGeneratedFunction(module, "main", "[]"))
    }

    @Test
    fun `decodes short int immediate in assignments`() {
        val shortIntType = org.seqra.ir.api.py.PIRPrimitiveType(
            name = "short_int",
            isUnboxed = true,
            isRefcounted = false,
            ctype = "CPyTagged"
        )
        val result = PIRRegister("result", PIRPrimitiveTypes.INT)

        val function = buildSyntheticFunction(
            moduleName = "short_sample",
            functionName = "main",
            argRegs = emptyList(),
            retType = PIRPrimitiveTypes.INT,
            blockRanges = listOf(0..1)
        ) { methodProvider ->
            listOf(
                PIRAssignInst(
                    location = loc(methodProvider, 0, 1),
                    lhv = result,
                    rhv = org.seqra.ir.api.py.cfg.PIRMoveExpr(
                        value = PIRInteger(2, shortIntType),
                        type = PIRPrimitiveTypes.INT,
                        line = 1
                    )
                ),
                PIRReturnInst(loc(methodProvider, 1, 1), result)
            )
        }

        val module = PIRModule(
            fullname = "short_sample",
            imports = emptyList(),
            functions = listOf(function),
            classes = emptyList(),
            finalNames = emptyList()
        )

        val emitted = PIRToPythonEmitter().emitModule(module)
        assertTrue(emitted.contains("result = 1"))
        assertEquals("1", runGeneratedFunction(module, "main", "[]"))
    }

    private fun buildSyntheticFunction(
        moduleName: String,
        functionName: String,
        argRegs: List<PIRRegister>,
        retType: org.seqra.ir.api.py.PIRType = PIRPrimitiveTypes.INT,
        blockRanges: List<IntRange>,
        instructionsBuilder: ((() -> PIRFunc) -> List<PIRInst>)
    ): PIRFunc {
        val owner = syntheticOwner(moduleName)

        class Holder {
            lateinit var func: PIRFunc
        }

        val holder = Holder()
        val methodProvider = { holder.func }
        val instructions = instructionsBuilder(methodProvider)
        val blocks = blockRanges.map { range ->
            PIRBasicBlock(
                start = PIRInstRef(range.first, instructions.getOrNull(range.first)),
                end = PIRInstRef(range.last, instructions.getOrNull(range.last))
            )
        }

        val func = PIRFunc(
            decl = PIRFuncDecl(
                name = functionName,
                className = null,
                moduleName = moduleName,
                sig = PIRFuncSignature(
                    args = argRegs.mapIndexed { index, reg ->
                        org.seqra.ir.api.py.PIRRuntimeArg(
                            name = reg.name,
                            type = reg.type,
                            kind = if (index >= 0) org.seqra.ir.api.py.ARG_POS else org.seqra.ir.api.py.ARG_POS
                        )
                    },
                    retType = retType
                )
            ),
            argRegs = argRegs,
            instructions = instructions,
            blocks = blocks,
            enclosingClass = owner
        )
        holder.func = func
        return func
    }

    private fun syntheticOwner(moduleName: String): PIRClass {
        val dummySig = PIRFuncSignature(emptyList(), PIRPrimitiveTypes.NONE)
        val dummyDecl = PIRFuncDecl(
            name = "__dummy__",
            className = null,
            moduleName = moduleName,
            sig = dummySig
        )
        return PIRClass(
            name = "__module__",
            moduleName = moduleName,
            ctor = dummyDecl,
            setup = dummyDecl
        )
    }

    private fun loc(methodProvider: () -> PIRFunc, index: Int, line: Int): PIRInstLocation =
        object : PIRInstLocation {
            override val method: PIRFunc
                get() = methodProvider()
            override val index: Int = index
            override val line: Int = line
        }

    private fun runGeneratedFunction(module: PIRModule, functionName: String, argsJson: String): String {
        val tempDir = Files.createTempDirectory("pir-phi-test")
        val moduleImportName = module.fullname.substringAfterLast('.')
        val generatedName = "${moduleImportName}_generated"
        val file = tempDir.resolve("$generatedName.py").toFile()
        PIRToPythonEmitter().writeModule(module, file)

        val script = """
            import json
            import $generatedName as generated
            args = json.loads('$argsJson')
            print(getattr(generated, '$functionName')(*args))
        """.trimIndent()

        val process = ProcessBuilder("python3", "-c", script)
            .directory(tempDir.toFile())
            .redirectErrorStream(true)
            .apply {
                environment()["PYTHONPATH"] = tempDir.toString()
            }
            .start()

        val output = process.inputStream.bufferedReader().readText().trim()
        val exitCode = process.waitFor()
        assertEquals(0, exitCode, output)
        assertFalse(output.contains("Traceback"), output)
        return output
    }
}
