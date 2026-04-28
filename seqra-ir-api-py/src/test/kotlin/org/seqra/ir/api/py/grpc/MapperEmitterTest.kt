package org.seqra.ir.api.py.grpc

import ir.*
import org.seqra.ir.api.py.emit.EmitMode
import org.seqra.ir.api.py.emit.EmitOptions
import org.seqra.ir.api.py.emit.PIRFuzzSupportChecker
import org.seqra.ir.api.py.emit.PIRToPythonEmitter
import org.seqra.ir.api.py.mapper.ProtoToPirMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

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
                                    .setType(intType)
                                    .setInteger(
                                        Integer.newBuilder()
                                            .setValue(2)
                                            .setType(intType)
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
                                    .setType(intType)
                                    .setInteger(
                                        Integer.newBuilder()
                                            .setValue(10)
                                            .setType(intType)
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
}
