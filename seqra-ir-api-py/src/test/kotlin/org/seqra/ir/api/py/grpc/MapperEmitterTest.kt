package org.seqra.ir.api.py.grpc

import ir.*
import org.seqra.ir.api.py.emit.PIRToPythonEmitter
import org.seqra.ir.api.py.mapper.ProtoToPirMapper
import org.junit.jupiter.api.Assertions.assertEquals
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
        assertTrue(emitted.contains("def add(x, y):"))
        assertTrue(emitted.contains("tmp = (x + y)"))
        assertTrue(emitted.contains("return tmp"))
    }
}
