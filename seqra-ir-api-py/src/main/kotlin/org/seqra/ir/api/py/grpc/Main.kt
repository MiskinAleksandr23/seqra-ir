package org.seqra.ir.api.py.grpc

import com.google.protobuf.util.JsonFormat
import io.grpc.ManagedChannelBuilder
import ir.FileList
import ir.IRServiceGrpcKt
import ir.SourceRequest
import kotlinx.coroutines.runBlocking
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

    val response = stub.getCFG(request)

    val jsonString = JsonFormat.printer()
        .includingDefaultValueFields()
        .preservingProtoFieldNames()
        .print(response)

    File("output.json").writeText(jsonString)

    println("Success: ${response.success}")
    println("CFG count: ${response.functionCfgsCount}")

    channel.shutdown()
}
