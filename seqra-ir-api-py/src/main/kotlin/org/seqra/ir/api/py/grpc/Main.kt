package org.seqra.ir.api.py.grpc

import com.google.protobuf.util.JsonFormat
import io.grpc.ManagedChannelBuilder
import ir.FileList
import ir.IRServiceGrpcKt
import ir.SourceRequest
import kotlinx.coroutines.runBlocking
import org.seqra.ir.api.py.emit.EmitMode
import org.seqra.ir.api.py.emit.EmitOptions
import org.seqra.ir.api.py.emit.PIRToPythonEmitter
import org.seqra.ir.api.py.mapper.ProtoToPirMapper
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText

private data class ClientConfig(
    val host: String,
    val port: Int,
    val files: List<String>,
    val outputDir: Path,
    val jsonOutput: Path?,
    val includeMetadata: Boolean,
    val emitOptions: EmitOptions
)

fun main(args: Array<String>): Unit = runBlocking {
    val config = parseArgs(args) ?: return@runBlocking
    val channel = ManagedChannelBuilder
        .forAddress(config.host, config.port)
        .usePlaintext()
        .build()

    val stub = IRServiceGrpcKt.IRServiceCoroutineStub(channel)

    val request = SourceRequest.newBuilder()
        .setFiles(
            FileList.newBuilder()
                .addAllFiles(config.files)
                .build()
        )
        .setIncludeMetadata(config.includeMetadata)
        .build()

    val response = stub.getAll(request)

    val jsonString = JsonFormat.printer()
        .includingDefaultValueFields()
        .preservingProtoFieldNames()
        .print(response)

    config.jsonOutput?.let {
        Files.createDirectories(it.parent ?: Path.of("."))
        it.writeText(jsonString)
        println("Saved response JSON: $it")
    }

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

    val emitter = PIRToPythonEmitter(config.emitOptions)
    Files.createDirectories(config.outputDir)

    pirModules.forEach { module ->
        val code = emitter.emitModule(module)
        val outputPath = moduleOutputPath(module.fullname, config.outputDir)
        Files.createDirectories(outputPath.parent)
        outputPath.writeText(code)
        println("Generated Python file: $outputPath")
    }

    channel.shutdown()
}

private fun parseArgs(args: Array<String>): ClientConfig? {
    var host = System.getenv("SEQRA_PY_GRPC_HOST") ?: "127.0.0.1"
    var port = (System.getenv("SEQRA_PY_GRPC_PORT") ?: "50051").toInt()
    var outputDir = Path.of(System.getenv("SEQRA_PY_OUT_DIR") ?: ".")
    var jsonOutput: Path? = null
    var includeMetadata = true
    var emitMode = EmitMode.fromCli(System.getenv("SEQRA_PY_EMIT_MODE") ?: EmitMode.FUZZ.cliName)
    var failOnUnsupported =
        (System.getenv("SEQRA_PY_FAIL_ON_UNSUPPORTED") ?: "true").toBooleanStrictOrNull() ?: true
    val files = mutableListOf<String>()

    var index = 0
    while (index < args.size) {
        when (val arg = args[index]) {
            "--help", "-h" -> {
                printUsage()
                return null
            }
            "--host" -> {
                host = args.getOrNull(++index) ?: error("Missing value for --host")
            }
            "--port" -> {
                port = args.getOrNull(++index)?.toIntOrNull()
                    ?: error("Missing or invalid value for --port")
            }
            "--out-dir" -> {
                outputDir = Path.of(args.getOrNull(++index) ?: error("Missing value for --out-dir"))
            }
            "--json-out" -> {
                jsonOutput = Path.of(args.getOrNull(++index) ?: error("Missing value for --json-out"))
            }
            "--emit-mode" -> {
                emitMode = EmitMode.fromCli(args.getOrNull(++index) ?: error("Missing value for --emit-mode"))
            }
            "--allow-unsupported" -> failOnUnsupported = false
            "--no-metadata" -> includeMetadata = false
            else -> {
                require(!arg.startsWith("--")) { "Unknown option: $arg" }
                files += arg
            }
        }
        index++
    }

    if (files.isEmpty()) {
        printUsage()
        return null
    }

    return ClientConfig(
        host = host,
        port = port,
        files = files,
        outputDir = outputDir,
        jsonOutput = jsonOutput,
        includeMetadata = includeMetadata,
        emitOptions = EmitOptions(mode = emitMode, failOnUnsupported = failOnUnsupported)
    )
}

private fun moduleOutputPath(moduleName: String, outputDir: Path): Path {
    val parts = moduleName.split('.').filter { it.isNotBlank() }
    val fileName = parts.lastOrNull()?.replace(".", "_")?.plus("_generated.py") ?: "module_generated.py"
    val parent = parts.dropLast(1).fold(outputDir) { acc, part -> acc.resolve(part) }
    return parent.resolve(fileName)
}

private fun printUsage() {
    println(
        """
        Usage: MainKt [--host HOST] [--port PORT] [--out-dir DIR] [--json-out FILE] [--emit-mode fuzz|debug-ir] [--allow-unsupported] [--no-metadata] <python-file>...
        """.trimIndent()
    )
}
