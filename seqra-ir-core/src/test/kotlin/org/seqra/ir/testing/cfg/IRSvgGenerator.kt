package org.seqra.ir.testing.cfg

import kotlinx.coroutines.runBlocking
import org.seqra.ir.api.jvm.JIRClassOrInterface
import org.seqra.ir.api.jvm.JIRClasspath
import org.seqra.ir.api.jvm.JIRDatabase
import org.seqra.ir.api.jvm.ext.findClass
import org.seqra.ir.impl.cfg.toFile
import org.seqra.ir.impl.seqraIrDb
import org.seqra.ir.testing.allClasspath
import java.io.Closeable
import java.io.File

class IRSvgGenerator(private val folder: File) : Closeable {

    private val db: JIRDatabase
    private val cp: JIRClasspath

    init {
        if (!folder.exists()) {
            folder.mkdir()
        } else {
            folder.list()?.forEach { File(folder, it).delete() }
        }
        db = runBlocking {
            seqraIrDb {
                loadByteCode(allClasspath)
            }
        }
        cp = runBlocking { db.classpath(allClasspath) }
    }

    fun generate() {
        dumpClass(cp.findClass<IRExamples>())
    }

    private fun dumpClass(klass: JIRClassOrInterface) {
        klass.declaredMethods.filter { it.enclosingClass == klass }.mapIndexed { index, it ->
            val fixedName = it.name.replace(Regex("[^A-Za-z0-9]"), "")
            val fileName = "${it.enclosingClass.simpleName}-$fixedName-$index.svg"
            val graph = it.flowGraph()
            JIRGraphChecker(it, graph).check()
            graph.toFile(File(folder, "graph-$fileName"))
            graph.blockGraph().toFile(File(folder, "block-graph-$fileName"))
        }
    }


    override fun close() {
        cp.close()
        db.close()
    }
}

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        throw IllegalStateException("Please provide folder for target svgs")
    }
    val folder = args[0]
    IRSvgGenerator(folder = File(folder)).generate()
}
