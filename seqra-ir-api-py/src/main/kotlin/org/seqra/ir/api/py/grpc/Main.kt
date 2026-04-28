package org.seqra.ir.api.py.grpc

import com.google.protobuf.util.JsonFormat
import io.grpc.ManagedChannelBuilder
import ir.FileList
import ir.IRServiceGrpcKt
import ir.SourceRequest
import kotlinx.coroutines.runBlocking
import org.seqra.ir.api.py.emit.PIRToPythonEmitter
import org.seqra.ir.api.py.mapper.ProtoToPirMapper
import java.io.File

fun main(): Unit = runBlocking {
    val channel = ManagedChannelBuilder
        .forAddress("localhost", 50051)
        .usePlaintext()
        .build()

    val stub = IRServiceGrpcKt.IRServiceCoroutineStub(channel)

    val request = SourceRequest.newBuilder()
        .setFiles(
            FileList.newBuilder()
                .addFiles("/mnt/c/MKN/project2/test.py")
                .build()
        )
        .setIncludeMetadata(true)
        .build()

    val response = stub.getAll(request)

    val jsonString = JsonFormat.printer()
        .includingDefaultValueFields()
        .preservingProtoFieldNames()
        .print(response)

    File("output.json").writeText(jsonString)

    println("Success: ${response.success}")
    println("Module count: ${response.modules.modulesCount}")
    println("Class count: ${response.classes.classesCount}")
    println("CFG count: ${response.cfgs.functionCfgsCount}")

    if (!response.success) {
        println("Errors: ${response.errorsList}")
        channel.shutdown()
        return@runBlocking
    }

    val mapper = ProtoToPirMapper()
    val pirModules = mapper.mapComplete(response)

    val emitter = PIRToPythonEmitter()

    pirModules.forEach { module ->
        val code = emitter.emitModule(module)
        val fileName = module.fullname.substringAfterLast('.').replace(".", "_") + "_generated.py"
        File(fileName).writeText(code)
        println("Generated Python file: $fileName")
    }

    channel.shutdown()
}