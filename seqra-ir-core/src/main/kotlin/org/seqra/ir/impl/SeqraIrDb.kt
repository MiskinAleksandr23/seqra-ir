package org.seqra.ir.impl

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.future.future
import org.seqra.ir.api.jvm.JIRDatabase
import org.seqra.ir.api.jvm.JIRSettings
import org.seqra.ir.impl.fs.JavaRuntime

suspend fun seqraIrDb(builder: JIRSettings.() -> Unit): JIRDatabase {
    return seqraIrDb(JIRSettings().also(builder))
}

suspend fun seqraIrDb(settings: JIRSettings): JIRDatabase {
    val javaRuntime = JavaRuntime(settings.jre)
    return JIRDatabaseImpl(javaRuntime = javaRuntime, settings = settings).also {
        it.restore()
        it.afterStart()
    }
}

/** bridge for Java */
fun async(settings: JIRSettings) = GlobalScope.future { seqraIrDb(settings) }
